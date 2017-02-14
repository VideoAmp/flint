#!/usr/bin/env sh

env LD_PRELOAD=/usr/lib/libjemalloc.so.2 java -Dconfig.file=conf/server.conf \
  -Dakka.loglevel=error -Dlog4j.configurationFile=conf/log4j2.xml \
  -jar flint-server-assembly.jar &

lighttpd -D -f /etc/lighttpd/lighttpd.conf
