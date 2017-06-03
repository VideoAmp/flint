FROM anapsix/alpine-java:8_jdk_unlimited

ARG ARTIFACT
ARG SPARK_BINARY_VERSION
ARG NATIVE_LIB_VERSION

ENV LANG en_US.UTF-8
ENV LANGUAGE en_US:en
ENV LC_ALL en_US.UTF-8

RUN apk update
RUN apk add ca-certificates jq lighttpd procps python wget

# AWS cli is needed for refresh_na_26_db.sh script
RUN wget https://s3.amazonaws.com/aws-cli/awscli-bundle.zip
RUN unzip awscli-bundle.zip
RUN ./awscli-bundle/install -i /usr/local/aws -b /usr/local/bin/aws
RUN rm -rf awscli-bundle*

ENV R_PKG_RELEASE="3.3.1-r0"

RUN wget -O /etc/apk/keys/sgerrand.rsa.pub https://github.com/sgerrand/alpine-pkg-R/releases/download/${R_PKG_RELEASE}/sgerrand.rsa.pub
RUN wget -O /tmp/R-${R_PKG_RELEASE}.apk https://github.com/sgerrand/alpine-pkg-R/releases/download/${R_PKG_RELEASE}/R-${R_PKG_RELEASE}.apk
RUN apk add /tmp/R-${R_PKG_RELEASE}.apk
RUN rm /tmp/R-${R_PKG_RELEASE}.apk

EXPOSE 6000 7005 7006 7077 8080 8081 8088 8888

ENV SPARK_HOME="/opt/spark"
ENV SPARK_WORKER_PORT=8888
ENV SPARK_LOCAL_DIRS=""
ENV SPARK_WORKER_MEMORY=""
ENV SPARK_MASTER_URL=""

ADD $ARTIFACT /opt
RUN cd /opt && ln -s $(basename $ARTIFACT .tgz) spark

# https://github.com/apache/spark/commit/c2c107abad8b462218d33c70b946e840663228a1 added "--" after
# the "nohup" command in spark-daemon.sh. BusyBox doesn't like that, so remove it.
RUN sed -i 's/nohup --/nohup/' /opt/spark/sbin/spark-daemon.sh

COPY sbin/boot-master.sh /usr/local/sbin/boot-master.sh
COPY sbin/boot-worker.sh /usr/local/sbin/boot-worker.sh

COPY conf/spark-defaults.conf /opt/spark/conf/spark-defaults.conf
COPY conf/spark-env.sh /opt/spark/conf/spark-env.sh
RUN chmod 755 /opt/spark/conf/spark-env.sh

RUN apk add jq lighttpd

RUN find /opt/spark/jars -name '*.jar' -exec sha256sum {} \; | jq --raw-input 'split("  /opt/spark/jars/") | {name: .[1], signature: .[0]}' | jq --slurp . > /opt/spark/jars/MANIFEST.json
RUN echo 'server.port = 8088' >> /etc/lighttpd/lighttpd.conf
RUN echo 'dir-listing.activate = "enable"' >> /etc/lighttpd/lighttpd.conf
COPY setup/$SPARK_BINARY_VERSION /var/www/localhost/htdocs/setup/flint
RUN ln -s /opt/spark/jars /var/www/localhost/htdocs/jars
RUN ln -s /opt/spark/conf /var/www/localhost/htdocs/conf
