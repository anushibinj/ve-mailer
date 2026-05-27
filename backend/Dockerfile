# Build
FROM maven:3.8.6-openjdk-18-slim AS builder
WORKDIR /tmp/staging-area

ARG JAVA_OPTS
ENV JAVA_OPTS=$JAVA_OPTS

COPY . .
RUN mvn clean test package

# Package
FROM eclipse-temurin:18-jre-alpine AS runner
RUN apk add --no-cache curl tzdata

# Timezone — override at runtime with: docker run -e TZ=Europe/London ...
ENV TZ=Asia/Calcutta

COPY --from=builder /tmp/staging-area/target/veemailer-0.0.1-SNAPSHOT.jar app.jar
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=10 \
    CMD curl http://localhost:8080/ || exit 1
EXPOSE 8080
ENTRYPOINT exec java $JAVA_OPTS -Duser.timezone=$TZ -Djava.security.egd=file:/dev/./urandom -jar app.jar
