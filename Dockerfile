FROM node:22-alpine AS frontend
WORKDIR /app
ARG VITE_BASE_PATH=/
ENV VITE_BASE_PATH=$VITE_BASE_PATH
COPY package*.json ./
RUN npm install
COPY index.html vite.config.ts tsconfig.json ./
COPY public ./public
COPY src ./src
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS backend
WORKDIR /app
COPY backend/pom.xml ./pom.xml
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/dist ./src/main/resources/static
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
ENV PORT=8787
COPY --from=backend /app/target/ai-agent-portfolio-api-0.1.0.jar ./app.jar
EXPOSE 8787
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
