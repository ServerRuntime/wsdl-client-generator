FROM repo.ibb.gov.tr/repository/docker-hub/eclipse-temurin:25-jre-alpine

RUN apk add --no-cache openjdk25-jdk maven openssl

# IBB SSL sertifikası
RUN openssl s_client -connect repo.ibb.gov.tr:443 -showcerts </dev/null 2>/dev/null \
    | openssl x509 -outform PEM > /tmp/ibb-cert.pem && \
    keytool -import -noprompt -trustcacerts -alias ibb-nexus \
    -file /tmp/ibb-cert.pem \
    -keystore /usr/lib/jvm/java-25-openjdk/lib/security/cacerts \
    -storepass changeit

# Maven IBB mirror ayarı
RUN mkdir -p /root/.m2 && printf '<settings>\n  <mirrors>\n    <mirror>\n      <id>ibb-nexus</id>\n      <mirrorOf>*</mirrorOf>\n      <url>https://repo.ibb.gov.tr/repository/maven-public/</url>\n    </mirror>\n  </mirrors>\n</settings>' > /root/.m2/settings.xml

ENV TZ=Europe/Istanbul
ENV JAVA_HOME=/usr/lib/jvm/java-25-openjdk
ENV PATH="$JAVA_HOME/bin:$PATH"

WORKDIR /app
COPY target/wsdl-client-generator-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["/usr/lib/jvm/java-25-openjdk/bin/java", "-jar", "app.jar"]
