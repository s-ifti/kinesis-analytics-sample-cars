### Flink sample using Cars

This maven project implements a simple Flink app processing Cars input dataset.
You will need to use an IAM role that allows publishing cloudwatch metric, as this
sample app uses a cloudwatch metric sink to write avg speed of car seen within last 30 seconds. (Metric is written within namespace KDA/MyFlink/Events)

### Build

````
mvn package

target folder will contain kinesis-analytics-sample-cars-1.0.jar
````

### Deployment
This sample uses parallelism of 4, either reduce that in code or
use following configuration when creating Kinesis Analytics app
"ParallelismConfiguration": 
        {"AutoScalingEnabled": true, 
         "ConfigurationType": "CUSTOM", 
         "CurrentParallelism": 4, 
         "Parallelism": 4, 
         "ParallelismPerKPU": 2
         }

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



### AWS Create App cli JSON skeleton

You can use following as an example json passed to create-application CLI call to AWS


`````

{
  "ApplicationName": "mycar-sample",
    "ApplicationDescription": "cars sample",
    "RuntimeEnvironment": "FLINK-1_6",
    "ServiceExecutionRole": "arn:aws:iam::xxxxxxx:role/KinesisStreamAnalyticsTestRole",
    "ApplicationConfiguration": {
        "ApplicationCodeConfiguration": {
            "CodeContent": {
                "S3ContentLocation":{
                              "BucketARN": "arn:aws:s3:::xxxxxx-kda-apps",
                              "FileKey": "my-cars-flink-sample-1.0.jar"
                }
            },
            "CodeContentType": "ZIPFILE"
        },
       "FlinkApplicationConfiguration": 
      {"CheckpointConfiguration": 
        {"CheckpointInterval": 60000, 
         "CheckpointingEnabled": false, 
         "ConfigurationType": "CUSTOM", 
         "MinPauseBetweenCheckpoints": 5000}, 
       "MonitoringConfiguration": 
        {"ConfigurationType": "CUSTOM", 
         "LogLevel": "INFO", 
         "MetricsLevel": "TASK"}, 
       "ParallelismConfiguration": 
        {"AutoScalingEnabled": true, 
         "ConfigurationType": "CUSTOM", 
         "CurrentParallelism": 4, 
         "Parallelism": 4, 
         "ParallelismPerKPU": 2}
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
 "CloudWatchLoggingOptions": 
  [
    {"LogStreamARN": "arn:aws:logs:us-east-1:xxxxx:log-group:my-flink-group:log-stream:my-car-test"}
  ]
}

````
