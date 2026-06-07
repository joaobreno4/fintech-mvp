# =============================================================================
# Stage 1: Build
# =============================================================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

WORKDIR /build

# Cache de dependências: copia apenas o pom e baixa as libs antes do src
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copia o código-fonte e empacota o JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# =============================================================================
# Stage 2: Runtime
# =============================================================================
FROM eclipse-temurin:17-jre-alpine

# Cria usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Instala wget leve para healthcheck do container
RUN apk add --no-cache wget

WORKDIR /app

# Copia o artefato gerado no stage anterior
COPY --from=builder --chown=appuser:appgroup /build/target/*.jar app.jar

USER appuser

EXPOSE 8080

# Flags JVM otimizadas para containers com restrição de memória (WSL2)
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -Xmx512m \
  -Xms256m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.backgroundpreinitializer.ignore=true \
  -Dspring.jmx.enabled=false"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
