# Build stage
FROM amazoncorretto:21 AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle* .
COPY settings.gradle* .

COPY cryptopedia-ex-api ./cryptopedia-ex-api
COPY cryptopedia-core ./cryptopedia-core

RUN chmod +x ./gradlew
RUN ./gradlew :cryptopedia-ex-api:bootJar --no-daemon

# Runtime stage
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/cryptopedia-ex-api/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
