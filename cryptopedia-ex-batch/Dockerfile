# Build stage
FROM amazoncorretto:21 AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle* .
COPY settings.gradle* .

COPY cryptopedia-ex-batch ./cryptopedia-ex-batch
COPY cryptopedia-core ./cryptopedia-core
COPY cryptopedia-client ./cryptopedia-client

RUN chmod +x ./gradlew
RUN ./gradlew :cryptopedia-ex-batch:bootJar --no-daemon

# Runtime stage
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/cryptopedia-ex-batch/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
