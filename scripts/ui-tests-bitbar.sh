#!/bin/bash
set -euo pipefail
shopt -s inherit_errexit

APP_FILE=$1
TEST_FILE=$2
PROJECT_ID=$3
FRAMEWORK_ID=252 # Espresso ID
OS_TYPE=ANDROID
DEVICE_GROUP_ID=44081 # Trial Group

echo "Uploading test files"
APP_FILE_ID=`http --form -a $BITBAR_API_KEY: https://cloud.bitbar.com/api/me/files file@$APP_FILE | jq '.id'`
TEST_FILE_ID=`http --form -a $BITBAR_API_KEY: https://cloud.bitbar.com/api/me/files file@$TEST_FILE | jq '.id'`

echo "Uploaded app file $APP_FILE_ID"
echo "Uploaded test file $TEST_FILE_ID"

TEST_RUN_ID=`http --ignore-stdin -a $BITBAR_API_KEY: https://cloud.bitbar.com/api/me/runs osType=$OS_TYPE projectId=$PROJECT_ID files:="[{\"id\": \"$APP_FILE_ID\"}, {\"id\": \"$TEST_FILE_ID\"}]" instrumentationRunner="androidx.test.runner.AndroidJUnitRunner" frameworkId=$FRAMEWORK_ID deviceGroupId=$DEVICE_GROUP_ID withAnnotation="de.taz.app.android.suite.UiTestSuite" | jq '.id'`
echo "Test run started on bitbar with id $TEST_RUN_ID"

TEST_RUN_LINK=`http --ignore-stdin --form -a $BITBAR_API_KEY: https://cloud.bitbar.com/api/me/projects/$PROJECT_ID/runs/$TEST_RUN_ID | jq '.uiLink'`
echo "To monitor details visit $TEST_RUN_LINK"

while :
do
   echo "Polling test result..."
   sleep 15
   RESULT=`http --ignore-stdin --form -a $BITBAR_API_KEY: https://cloud.bitbar.com/api/me/projects/$PROJECT_ID/runs/$TEST_RUN_ID`
   STATE=`echo $RESULT | jq '.state'`
   SUCCEEDED=`echo $RESULT | jq '.successfulTestCaseCount'`
   TOTAL=`echo $RESULT | jq '.testCaseCount'`
   echo "Running state: $STATE Total tests: $TOTAL, $SUCCEEDED succeeded"
   if [[ "$STATE" == '"FINISHED"' ]]; then
      break
   fi
done

echo "Test results are done - visit $TEST_RUN_LINK for details"

if [[ "$SUCCEEDED" == "$TOTAL" ]]; then
    echo "Test ran successfully"
    exit 0
else
    echo "There were failures in tests"
    exit 1
fi
