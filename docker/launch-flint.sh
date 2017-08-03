#!/usr/bin/env sh

set -e
set -u

if [ $# -ne 2 ]; then
  echo "Must supply server socket address as command line parameter, e.g. flint.mydomain:8080" >&2
  exit 1
fi

API_VERSION=$1
SERVER_ADDRESS=$2

ENDPOINT_CONFIG_LOCATION=/var/www/localhost/htdocs/endpoints.json

cat << EOF > $ENDPOINT_CONFIG_LOCATION
{
  "serverUrl": "http://${SERVER_ADDRESS}/api/version/${API_VERSION}",
  "messagingUrl": "ws://${SERVER_ADDRESS}/api/version/${API_VERSION}"
}
EOF

echo "Endpoint addresses:"
cat $ENDPOINT_CONFIG_LOCATION

env LD_PRELOAD=/usr/lib/libjemalloc.so.2 java -Dconfig.file=conf/server.conf \
  -Dakka.loglevel=error -Dlog4j.configurationFile=conf/log4j2.xml \
  -jar flint-server-assembly.jar &

lighttpd -D -f /etc/lighttpd/lighttpd.conf
