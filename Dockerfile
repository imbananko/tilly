FROM openjdk:17-jdk-slim

ENV APP_HOME=/usr/app/
ARG COMMIT_SHA_ARG='unknown'
ENV METADATA_COMMIT_SHA=$COMMIT_SHA_ARG

EXPOSE 8443

WORKDIR $APP_HOME

RUN mkdir -p /opt/cprof

COPY ./build/libs/tilly-1.1.jar ./app.jar
COPY ./build/resources/main/cprof /opt/cprof

CMD ["java", "-agentpath:/opt/cprof/profiler_java_agent.so=-logtostderr,-minloglevel=1,-cprof_service=tilly,-cprof_service_version=2.0.0,-cprof_enable_heap_sampling=true", "-jar", "app.jar"]
