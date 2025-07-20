# Stage 1: Build Java app
FROM maven:3.9.11-amazoncorretto-21 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime base - chỉ Chromium
FROM mcr.microsoft.com/playwright/java:v1.53.0-noble

# Cài Node.js nếu cần (Playwright CLI cần Node để chạy `npx`)
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Xoá Firefox và WebKit để giảm dung lượng
RUN rm -rf /ms-playwright/firefox /ms-playwright/webkit

# Cài lại Chromium (có thể đã cài sẵn, nhưng để chắc)
RUN sh -c '[ -d /ms-playwright/chromium ] || npx playwright install chromium'



WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

CMD ["java", "-jar", "app.jar"]
