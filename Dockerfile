FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy

WORKDIR /app

COPY ./target/*.jar app.jar

EXPOSE 2605

CMD ["java", "--enable-preview", "-jar", "app.jar"]
