FROM maven:3.9-eclipse-temurin-21-jammy
WORKDIR /app
COPY . .
EXPOSE 8080
CMD ["sh", "-c", "set -ex; echo 'Starting Maven Build'; mvn clean package -DskipTests; java -jar target/dbtraining-0.0.1-SNAPSHOT.jar"]