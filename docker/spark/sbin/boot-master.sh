#!/usr/bin/env bash

lighttpd -f /etc/lighttpd/lighttpd.conf

/opt/spark/sbin/start-master.sh
/bin/bash
