FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:b84504b3360cccd9d561bcf304edb9000adc2796387335ba0bbc5ce26dc2f3a7

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-rapportering-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

