### Apache Flink sample on [AWS Kinesis Analytics](https://aws.amazon.com/blogs/aws/new-amazon-kinesis-data-analytics-for-java/)

This maven project implements a simple Flink based app for processing a simulated cars stream.

### Build, deploy, and run Apache Flink app using cloudformation template (in 4-5 minutes)

For a quick build and deploy you can deploy a cloud formation template (flink-1.6.2-build-sample.yml) present in this repository to run this demo app. This cloudformation template automates all steps necessary to deploy Flink app from source, in summary following resources are created :

* Creates a kinesis stream that will receive Car events simulated by a Lambda function
* Creates a lambda function using python to simulate Car events
* Creates IAM roles necessary for build, deploy, and execution of Java App by Kinesis analytics
* Build Jar artifact using the maven project in this repository
* Uploads Jar to S3 bucket to be used by AWS Kinesis analytics
* Creates an AWS Kinesis Analytics Service using the newly created Jar
* Invokes the lambda function once to simulate events

To deploy this demo app using CF template, follow these steps:
You can also review a small [gist](https://gist.github.com/s-ifti/ffe8496675ecf358d79d21495a544414) with animated gif to get an overview.

1. Clone this repository on your machine 
2. Launch Cloudformation within AWS and then access "Create Stack" button
3. Upload the CF file using "Upload to S3" option and upload the CF template file (flink-1.6.2.-build-sample.yml)
4. Choose a stack name (e.g. car-samples)
5. Once CloudFormation template is completed, launch Code Pipeline and review build history to ensure that code build process has finished.
6. Once all code build steps are completed (takes about 2-3 minutes), goto Kinesis page on AWS console, then select "Kinesis Data Analytics" tab from left pane.
5. You should be able to find the newly created app (with a prefix of the stack name you chose earlier).
6. Select the app and see Application Details.
7. The App should be showing Running status.
8. Now you can review AWS Cloudwatch metric emitted by this app within Cloudwatch metrics page.
9. On Cloudwatch metrics page, search for metrics with your stack name, you should find metrics within MyKinesisAnalytics/CarAvgSpeed, you can select them to plot the avg Speed calculated by the app, as well as the Sample Speed of raw stream events.
10. You will also be able to review AWS Cloudwatch Log created in log group:
java-app-log-group-{region}
and log stream:
{stack-name}-java-app-sample-{region}-log

After initial test, you can head over to the Lambda service in AWS console and find the event producer lambda with a prefix of the {stack name}-SampleStreamProducerPutFunction...
You can add a trigger of Cloudwath Event Schedule trigger and choose rate(1 minute) to run the producer continuously.

NOTE: The Lambda and the newly created Kinesis Analytics app will incurr AWS charges, so be sure to delete these resources once you have played with this demo app. 

#### Removing demo resources and CF Stack

Once you are done with this demo, prior to deleting stack to remove associated resource, you will need to remove S3 bucket created for uploading compiled Jar file and source artifacts manually, look for Resources created by the CF stack to find S3 bucket name.

After deleting stack, you will also have to delete Kinesis Analytics App manually by launching Kinesis service in AWS console and stopping and deleting the newly created app (with prefix of {stack name} chosen when you created the CF stack).


### Dive deep - Build (Locally)

The package depends on flink kinesis connector that is not available in Maven repository, you can build it
locally by downloading flink 1.6.2 source.
Once you have compliled and added flink kinesis connector add it to local maven repository by using following command-line:

````
 mvn install:install-file    -Dfile=/Users/myusername/Downloads/flink-connector-kinesis_2.11-1.6.2.jar -DgroupId=org.apache.flink -DartifactId=flink-connector-kinesis_2.11 -Dversion=1.6.2 -Dpackaging=jar -DgeneratePom=true

````

After that cd to this repository and build using maven
````
mvn package


````
./target folder will contain the shaded kinesis-analytics-sample-cars-1.0.jar ready for deployment to AWS Kinesis Analytics as a Java App.


### Simulating source stream

To simulate input stream, create a stream named "input-stream" in your account.
Each message sent to stream should use following JSON format:


````
{  "dataTimestamp":"2018-12-07 22:56:36.589",
   "vehicleId": "69b6d839-2273-407f-bdae-03f535596223",
   "latitude":47.67,"longitude":-122.24,
    "speed":172.69,
    "fuelEfficiency":67.21,
    "destinationLatitude":47.62,
    "destinationLongitude":-122.11,
    "hasMoonRoof":true,
    "engineTemperature":415

}
````

You can use following python script to generate sample event:


````

import boto
import json
import decimal
from datetime import datetime
import time
import uuid
import boto3

client = boto3.client('kinesis')

streamName = 'input-stream'


time = datetime.utcnow()
timeString = time.strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
vId = "\"" + str(uuid.uuid4()) + "\""
message ="{\"dataTimestamp\":\"" + timeString + "\",\"vehicleId\": " + vId + ""","latitude":47.67,"longitude":-122.24,"speed":172.69,"fuelEfficiency":67.21,"destinationLatitude":47.62,"destinationLongitude":-122.11,"hasMoonRoof":true,"engineTemperature":415}"""
 

print("sending message: " + message)
response = client.put_record(
    StreamName=streamName,
    Data=message,
    PartitionKey='nokey'
)
print("sent to " + str( response ) )
print('DONE')


````



### AWS Kinesis Analytics Create App via AWS CLI

Before issuing API call to create kinesis analytics app, make sure that you have a service execution role propertly configured.

Create an IAM role, and 
add following permissions:

* Add Trust Relationship with service kinesisanalytics.amazonaws.com
* Allow CW logs to be published by Kinesis Analytics service:
````
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "logs:DescribeLogGroups",
                "logs:DescribeLogStreams",
                "logs:PutLogEvents"
            ],
            "Effect": "Allow",
            "Resource": "*"
        }
    ]
}
````
* Allow CW metrics to be published by app using custom sink for CW metric
````
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Action": [
                "cloudwatch:PutMetricData"
            ],
            "Effect": "Allow",
            "Resource": [
                "*"
            ]
        }
    ]
}
````
* Allow Kinesis operations
````
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "kinesis:*",
            "Resource": "*"
        }
    ]
}
````

The permissions provided above uses broader permissions for simplicity, you can further restrict permissions to specific resouce and specific action as needed.

The service execution role requires permission to allow publishing of cloudwatch metric from the app only because the app uses a custom cloudwatch metric sink to write avg speed of car seen within last 30 seconds to a CW metric, this metric is written within namespace KDA/MyFlink/Events.


You can use following CLI commands to create this app :

````
export TEST_REGION=us-east-1
export APP_NAME=my-cars-app
$ aws kinesisanalyticsv2 create-application --application-name $TEST_REGION-$APP_NAME  --runtime-environment FLINK-1_6 --service-execution-role arn:aws:iam::xxxxxxxxxx:role/KinesisStreamKAJATest --cli-input-json file://create-car-sample-app.json --region $TEST_REGION

$ aws kinesisanalyticsv2 start-application --application-name $TEST_REGION-$APP_NAME  --run-configuration "{}" --region $TEST_REGION

$ aws kinesisanalyticsv2 describe-application --application-name $TEST_REGION-$APP_NAME   --region $TEST_REGION


````


create-car-sample-app.json file can be initialized with following content: 

Note: Replace ServiceExecutionRole with the IAM role created in previous steps.
Update BucketARN and FileKey to the location in S3 that contains the jar file for this sample.


`````
{
  "ApplicationName": "mycar-sample",
  "ApplicationDescription": "cars sample",
  "RuntimeEnvironment": "FLINK-1_6",
  "ServiceExecutionRole": "arn:aws:iam::xxxxxxx:role/KinesisStreamAnalyticsTestRole",
  "ApplicationConfiguration": {
    "ApplicationCodeConfiguration": {
      "CodeContent": {
        "S3ContentLocation": {
          "BucketARN": "arn:aws:s3:::xxxxxx-kda-apps",
          "FileKey": "my-cars-flink-sample-1.0.jar"
        }
      },
      "CodeContentType": "ZIPFILE"
    },
    "FlinkApplicationConfiguration": {
      "CheckpointConfiguration": {
        "CheckpointInterval": 60000,
        "CheckpointingEnabled": false,
        "ConfigurationType": "CUSTOM",
        "MinPauseBetweenCheckpoints": 5000
      },
      "MonitoringConfiguration": {
        "ConfigurationType": "CUSTOM",
        "LogLevel": "INFO",
        "MetricsLevel": "TASK"
      },
      "ParallelismConfiguration": {
        "AutoScalingEnabled": true,
        "ConfigurationType": "CUSTOM",
        "CurrentParallelism": 4,
        "Parallelism": 4,
        "ParallelismPerKPU": 2
      }
    },
    "EnvironmentProperties": {
      "PropertyGroups": [
        {
          "PropertyGroupId": "CarProperties",
          "PropertyMap": {
            "metricTag": "gamma"
          }
        }
      ]
    }
  },
  "CloudWatchLoggingOptions": [
    {
      "LogStreamARN": "arn:aws:logs:us-east-1:xxxxx:log-group:my-flink-group:log-stream:my-car-test"
    }
  ]
}

`````

### Deployment Notes

This sample uses parallelism of 4, either reduce that in code as needed, or
use following configuration when creating Kinesis Analytics app

````
"ParallelismConfiguration":  {
         "AutoScalingEnabled": true, 
         "ConfigurationType": "CUSTOM", 
         "CurrentParallelism": 4, 
         "Parallelism": 4, 
         "ParallelismPerKPU": 2
}
````


### License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)

