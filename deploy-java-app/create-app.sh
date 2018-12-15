if [ $# -lt 7 ]; then
  echo 1>&2 "$0: parameters missing"
  echo 1>&2 "Usage: create-app.sh {region} {app-name} {aws-account-id} {execution-role-arn} {stream-name} {bucket} {jarfile}"
  echo 1>&2 "        e.g. $ create-app.sh us-east-1 my-app-dec-24 123456789012 KinesisAnalyticsSampleExecRole my-input-stream my-s3-artifact-bucket my-jar-file-name"
  exit 2
fi

TEST_REGION=$1
aws logs create-log-group --log-group-name java-app-log-group-$TEST_REGION --region $TEST_REGION
APP_NAME=$2
ACCOUNT_ID=$3
AWS_ACCOUNT_ROLE_FOR_JAVA_APP=arn:aws:iam::$3:role/$4
INPUT_STREAM=$5
BUILT_JAR_S3_BUCKET_ARN=$6
APP_JAR_FILE_NAME=$7

aws logs create-log-stream --log-group-name java-app-log-group-$TEST_REGION --log-stream-name $APP_NAME-sample-$TEST_REGION-log --region $TEST_REGION 
sed -e "s/\${i}/1/" -e "s/\#{TEST_REGION}/$TEST_REGION/g;s/\#{ACCOUNT_ID}/$ACCOUNT_ID/g;s/\#{BUILT_JAR_S3_BUCKET_ARN}/$BUILT_JAR_S3_BUCKET_ARN/g;s/\#{APP_JAR_FILE_NAME}/$APP_JAR_FILE_NAME/g;s/\#{AWS_ACCOUNT_ROLE_FOR_JAVA_APP}/arn:aws:iam::$3:role\/$4/g;s/\#{APP_NAME}/$APP_NAME/g;s/\#{INPUT_STREAM}/$INPUT_STREAM/g" ./java-app-request.json > deploy-app-$TEST_REGION-$APP_NAME.tmp 

aws kinesisanalyticsv2 create-application --application-name $APP_NAME  --runtime-environment FLINK-1_6 \
--service-execution-role $AWS_ACCOUNT_ROLE_FOR_JAVA_APP \
--cli-input-json file://deploy-app-$TEST_REGION-$APP_NAME.tmp --region $TEST_REGION

aws kinesisanalyticsv2 start-application --application-name $APP_NAME  --run-configuration "{}" --region $TEST_REGION

aws kinesisanalyticsv2 describe-application --application-name $APP_NAME   --region $TEST_REGION

