FROM gcr.io/distroless/java21

COPY build/libs/dp-rapportering-all.jar /app.jar
CMD ["/app.jar"]
