# Tradeez as one image (T31, DEC-084): the backend serving the built page beside the API, so both are one site.
# Host-neutral: any host that runs a Docker image and gives it a PostgreSQL. Secrets come from the host's
# environment at run time (DEPLOY in docs/DOCUMENTATION.md); none is in the image.
#
#   docker build -t tradeez .
#   docker run -p 8080:8080 -e DATABASE_URL=... -e DATABASE_USER=... -e DATABASE_PASSWORD=... \
#     -e JWT_SECRET=... -e COOKIES_SECURE=true tradeez

# 1. The page.
FROM node:24-alpine AS page
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY frontend/ ./
RUN npm run build

# 2. The server, with the page in its static folder.
FROM eclipse-temurin:25-jdk AS server
WORKDIR /backend
COPY backend/mvnw ./
COPY backend/.mvn .mvn
COPY backend/pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY backend/src src
# The curriculum, which the build copies from ../docs into the jar (pom.xml, T9).
COPY docs/ICT_CONCEPTS.md /docs/ICT_CONCEPTS.md
COPY --from=page /frontend/dist src/main/resources/static
# The tests need Docker for their database; they run before a merge (the fresh-clone check), not in the image.
RUN ./mvnw -B -q -DskipTests package && mv target/*.jar app.jar

# 3. What runs: a JRE, the jar, no root.
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 tradeez
WORKDIR /app
COPY --from=server /backend/app.jar app.jar
USER tradeez
# BUG-121: the deployed profile — no secret has a default, so a missing one stops the start (application-prod.yml).
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
