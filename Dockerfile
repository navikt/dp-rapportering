FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:d80cd18fb4f468842f219250f8df37fd9fca3791baf832acd27d8ab19c5148f0

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-rapportering-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

