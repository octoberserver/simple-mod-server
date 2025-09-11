FROM eclipse-temurin:21-jre-alpine
LABEL authors="october"

WORKDIR "/app"

COPY . .

RUN "chmod +x ./gradlew"

CMD ["./gradlew", "run"]