### Apache Flink sample 

This maven project implements a simple Flink based app for processing a simulated cars dataset.


### Build (Locally)

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

### Build (Using AWS Code Pipeline)

As an alternate to building locally, you can use provided Cloud Formation template (flink-1.6.2-build-sample.yml) to build sample project jar files in the cloud (this will also generate  flink kinesis connector jar for any future projects use).

Once Code Build is completed, simply check the output artifacts of the cloud formation stack and copy the built jar files from the S3 bucket to locally or to another S3 folder for deployment as kinesis analytics service.

You can also skip copyin the jar file and use the generated jar file (uploaded to the S3 bucket by Code pipeline) to be executed as Kinesis Analytics Java (Flink) app.



### Simulating source stream

To simulate input stream, create a stream named "input-stream" in your account.
Each message sent to stream should use following JSON format:


````
{"dataTimestamp":"2018-12-07 22:56:36.589","vehicleId": "69b6d839-2273-407f-bdae-03f535596223","latitude":47.67,"longitude":-122.24,"speed":172.69,"fuelEfficiency":67.21,"destinationLatitude":47.62,"destinationLongitude":-122.11,"hasMoonRoof":true,"engineTemperature":415}
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



### TODO

Automate Kinesis Analytics App Deployment and Input Stream creation via Cloud formation template.
