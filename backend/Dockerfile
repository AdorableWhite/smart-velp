# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install python3, yt-dlp and ffmpeg
RUN apk add --no-cache python3 py3-pip curl ffmpeg && \
    curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp && \
    chmod a+rx /usr/local/bin/yt-dlp && \
    /usr/local/bin/yt-dlp -U

COPY --from=build /app/target/*.jar app.jar

# Update environment variables for paths if needed
ENV VELP_PYTHON_PATH=python3
ENV VELP_YTDLP_PATH=/usr/local/bin/yt-dlp

EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
