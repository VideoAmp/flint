#!/bin/bash

set -e
set -u

function usage() {
  echo "Usage: $(basename $0) [-p] <spark-distfile> <build-number>"
  echo "  -p  Push image"
  exit 1
}

if [ $# -eq 3 ]; then
  if [ $1 = "-p" ]; then
    PUSH=true
    shift
  else
    usage
  fi
elif [ $# -ne 2 ]; then
  usage
else
  PUSH=false
fi

cd $(dirname $0)

ARTIFACT=$1
BUILD_NUMBER=$2

DIST_NAME_PAT="spark-([.\d]+(?:-SNAPSHOT)?)-bin-([.\d]+-cdh([.\d]+))([-.\w]+)?-vamp_b(.*)"

ARTIFACT_BASENAME=$(basename $ARTIFACT .tgz)
SPARK_VERSION=$(echo $ARTIFACT_BASENAME | perl -pe "s/$DIST_NAME_PAT/\1/")
SPARK_BINARY_VERSION=${SPARK_VERSION:0:3}
HADOOP_VERSION=$(echo $ARTIFACT_BASENAME | perl -pe "s/$DIST_NAME_PAT/\2/")
CDH_VERSION=$(echo $ARTIFACT_BASENAME | perl -pe "s/$DIST_NAME_PAT/\3/")
BRANCH=$(echo $ARTIFACT_BASENAME | perl -pe "s/$DIST_NAME_PAT/\4/")
SPARK_BUILD_TAG=$(echo $ARTIFACT_BASENAME | perl -pe "s/$DIST_NAME_PAT/\5/")
NATIVE_LIB_VERSION=cdh$CDH_VERSION
DOCKER_TAG=$SPARK_VERSION-$HADOOP_VERSION$BRANCH-b$SPARK_BUILD_TAG-$BUILD_NUMBER

echo "Building videoamp/spark:$DOCKER_TAG"

docker build --build-arg ARTIFACT=$ARTIFACT --build-arg SPARK_BINARY_VERSION=$SPARK_BINARY_VERSION --build-arg NATIVE_LIB_VERSION=$NATIVE_LIB_VERSION -t "videoamp/spark:$DOCKER_TAG" .

if [ $PUSH = "true" ]; then
  docker push videoamp/spark:$DOCKER_TAG
fi
