#!/usr/bin/env bash

/usr/local/bin/refresh_na_26_db.sh /opt/geo

/opt/spark/sbin/start-slave.sh spark://$SPARK_MASTER_IP:7077
/bin/bash
