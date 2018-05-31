# Licensed to the Apache Software Foundation (ASF) under one or more contributor
# license agreements; and to You under the Apache License, Version 2.0.

FROM anapsix/alpine-java:8_jdk

RUN sed -i s/#networkaddress.cache.ttl=-1/networkaddress.cache.ttl=10/ ${JAVA_HOME}/jre/lib/security/java.security
RUN sed -i s/networkaddress.cache.negative.ttl=10/networkaddress.cache.negative.ttl=0/ ${JAVA_HOME}/jre/lib/security/java.security

RUN mkdir /logs

COPY transformEnvironment.sh /
RUN chmod +x transformEnvironment.sh

COPY copyJMXFiles.sh /
RUN chmod +x copyJMXFiles.sh
