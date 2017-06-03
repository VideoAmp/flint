#!/usr/bin/env bash

SPARK_COMMON_OPTS="-Dspark.blockManager.port=7005 -Dspark.executor.port=7006"
SPARK_COMMON_OPTS+=" -Dspark.broadcast.factory=org.apache.spark.broadcast.HttpBroadcastFactory"
SPARK_COMMON_OPTS+=" -Dcom.sun.management.jmxremote.port=6000 -Dcom.sun.management.jmxremote.ssl=false"
SPARK_COMMON_OPTS+=" -Dcom.sun.management.jmxremote.authenticate=false"

HADOOP_CONF_DIR=/etc/hadoop/conf

SPARK_MASTER_OPTS=$SPARK_COMMON_OPTS

SPARK_WORKER_OPTS="-Dspark.worker.cleanup.enabled=true -Dspark.shuffle.service.enabled=true ${SPARK_COMMON_OPTS}"

SPARK_DAEMON_JAVA_OPTS="-Djava.net.preferIPv4Stack=true"
SPARK_DAEMON_MEMORY=2g

SPARK_PID_DIR="${SPARK_HOME}/run"
