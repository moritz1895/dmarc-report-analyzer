FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /app

RUN apk add --no-cache maven

# Download dependencies first (better layer caching)
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Build the application
COPY src ./src
RUN mvn package -DskipTests -q

# ---

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

# Non-root user for security
RUN addgroup -S dmarc && adduser -S dmarc -G dmarc
USER dmarc

COPY --from=build /app/target/*.jar app.jar

# Pure outbound-polling service (IMAP fetch, SMTP send, HTTPS to the Anthropic API) —
# no inbound port is ever opened, so nothing is EXPOSEd.

HEALTHCHECK --interval=1m --timeout=5s --start-period=30s --retries=3 \
    CMD pgrep -f app.jar || exit 1

ENTRYPOINT ["java", "-Xms64m", "-Xmx192m", "-jar", "app.jar"]
