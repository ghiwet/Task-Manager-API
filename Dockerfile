# Use official OpenJDK image as base
FROM eclipse-temurin:21-jdk

# Set working directory inside container
WORKDIR /app

# Copy the built jar from your local machine to the container
# Make sure you build your app first using `./mvnw clean package` or `./gradlew build`
COPY target/*.jar app.jar

# Expose the port your Spring Boot app listens on (default 8080)
EXPOSE 8080

# Run the jar file
ENTRYPOINT ["java","-jar","app.jar"]
