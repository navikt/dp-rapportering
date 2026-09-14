FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25@sha256:4f0da573eff60b068101b5894040c0793346c4ad41c783960984664bdc22b4ba

ENV LANG='nb_NO.UTF-8' LANGUAGE='nb_NO:nb' LC_ALL='nb_NO.UTF-8' TZ="Europe/Oslo"

COPY build/libs/dp-rapportering-all.jar /app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]

