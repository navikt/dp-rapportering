FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:ebef6e2aad6ca41a8257ef68fc20f507cd6ef8e5ec208e72e7bd7265f8bd3a26

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-rapportering-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

