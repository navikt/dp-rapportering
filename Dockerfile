FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:de3c8f4b0ded027aaccff268c02ac3800338545b35b39e9193eb97d5607045e3

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-rapportering-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

