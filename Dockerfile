FROM ghcr.io/navikt/baseimages/temurin:21

COPY build/libs/*-all.jar app.jar

CMD ["java", "-jar", "app.jar"]