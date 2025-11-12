# Build stage
FROM amazoncorretto:21 AS build
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle* .
COPY settings.gradle* .

COPY src ./src

RUN chmod +x ./gradlew
RUN ./gradlew bootJar --no-daemon

# Runtime stage
FROM amazoncorretto:21
WORKDIR /app
COPY --from=build /app/build/libs/cryptopedia-server-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]