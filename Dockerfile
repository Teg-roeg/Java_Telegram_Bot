FROM eclipse-temurin:21-jdk-jammy
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN apt-get update && apt-get install -y maven
RUN mvn clean package -DskipTests
CMD ["java", "-jar", "target/blitzfox-0.0.1-SNAPSHOT.jar"]