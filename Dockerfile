FROM repo.ibb.gov.tr/repository/docker-hub/eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/wsdl-client-generator-1.0.0.jar app.jar
ENV TZ=Europe/Istanbul
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
