FROM openjdk:11-jre-slim

ENV APP_HOME=/usr/app/
ARG COMMIT_SHA_ARG='unknown'
ENV METADATA_COMMIT_SHA=$COMMIT_SHA_ARG

WORKDIR $APP_HOME
COPY ./build/libs/*.jar ./app.jar

CMD ["java", "-jar", "app.jar"]