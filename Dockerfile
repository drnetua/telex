# Build stage: JDK 25 + Node/pnpm, runs the Gradle bootJar (the SPA is built and bundled by Gradle).
FROM eclipse-temurin:25-jdk AS build
COPY --from=node:24-slim /usr/local/bin/node /usr/local/bin/node
COPY --from=node:24-slim /usr/local/lib/node_modules /usr/local/lib/node_modules
RUN ln -s /usr/local/lib/node_modules/npm/bin/npm-cli.js /usr/local/bin/npm \
    && ln -s /usr/local/lib/node_modules/corepack/dist/corepack.js /usr/local/bin/corepack \
    && corepack enable pnpm
WORKDIR /src
COPY . .
RUN ./gradlew --no-daemon :backend:app:bootJar -x test \
    && cp backend/app/build/libs/app.jar /app.jar

# Runtime stage: JRE 25 only.
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
