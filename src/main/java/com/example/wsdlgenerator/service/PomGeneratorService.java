package com.example.wsdlgenerator.service;

import com.example.wsdlgenerator.model.JavaVersion;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class PomGeneratorService {

    public Path generate(JavaVersion javaVersion,
                         String groupId, String artifactId, String version,
                         String prodWsdlFilename, String testWsdlFilename,
                         String generateFrom,
                         String targetPackage,
                         Path workDir) throws IOException {

        String wsdlFilename = "test".equalsIgnoreCase(generateFrom) ? testWsdlFilename : prodWsdlFilename;

        String pom = switch (javaVersion) {
            case JAVA_8  -> buildJava8Pom(groupId, artifactId, version, wsdlFilename, targetPackage);
            case JAVA_11 -> buildJava11Pom(groupId, artifactId, version, wsdlFilename, targetPackage);
            case JAVA_17, JAVA_21 -> buildCxf4Pom(javaVersion, groupId, artifactId, version, wsdlFilename, targetPackage);
        };

        Path pomFile = workDir.resolve("pom.xml");
        Files.writeString(pomFile, pom);
        return pomFile;
    }

    // ── Java 8 ── javax namespace; build Java 17 JVM'de yapıldığından bağımlılıklar gerekli ─
    private String buildJava8Pom(String groupId, String artifactId, String version,
                                  String wsdlFilename, String targetPackage) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <packaging>jar</packaging>

                <properties>
                    <maven.compiler.source>1.8</maven.compiler.source>
                    <maven.compiler.target>1.8</maven.compiler.target>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <skipWsdlGenerate>false</skipWsdlGenerate>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>javax.xml.ws</groupId>
                        <artifactId>jaxws-api</artifactId>
                        <version>2.3.1</version>
                    </dependency>
                    <dependency>
                        <groupId>com.sun.xml.ws</groupId>
                        <artifactId>rt</artifactId>
                        <version>2.3.7</version>
                    </dependency>
                    <dependency>
                        <groupId>javax.xml.bind</groupId>
                        <artifactId>jaxb-api</artifactId>
                        <version>2.3.1</version>
                    </dependency>
                    <dependency>
                        <groupId>com.sun.xml.bind</groupId>
                        <artifactId>jaxb-impl</artifactId>
                        <version>2.3.9</version>
                    </dependency>
                </dependencies>

                <build>
                    %s
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.11.0</version>
                            <configuration>
                                <source>1.8</source>
                                <target>1.8</target>
                            </configuration>
                        </plugin>
                        %s
                    </plugins>
                </build>
            </project>
            """.formatted(groupId, artifactId, version, resourcesBlock(),
                          jaxwsMavenPlugin(wsdlFilename, targetPackage));
    }

    // ── Java 11 ── javax namespace, JAXB JDK'dan kaldırıldı ─
    private String buildJava11Pom(String groupId, String artifactId, String version,
                                   String wsdlFilename, String targetPackage) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <packaging>jar</packaging>

                <properties>
                    <maven.compiler.source>11</maven.compiler.source>
                    <maven.compiler.target>11</maven.compiler.target>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <skipWsdlGenerate>false</skipWsdlGenerate>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>javax.xml.ws</groupId>
                        <artifactId>jaxws-api</artifactId>
                        <version>2.3.1</version>
                    </dependency>
                    <dependency>
                        <groupId>com.sun.xml.ws</groupId>
                        <artifactId>rt</artifactId>
                        <version>2.3.7</version>
                    </dependency>
                    <dependency>
                        <groupId>javax.xml.bind</groupId>
                        <artifactId>jaxb-api</artifactId>
                        <version>2.3.1</version>
                    </dependency>
                    <dependency>
                        <groupId>com.sun.xml.bind</groupId>
                        <artifactId>jaxb-impl</artifactId>
                        <version>2.3.9</version>
                    </dependency>
                </dependencies>

                <build>
                    %s
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.11.0</version>
                            <configuration>
                                <source>11</source>
                                <target>11</target>
                            </configuration>
                        </plugin>
                        %s
                    </plugins>
                </build>
            </project>
            """.formatted(groupId, artifactId, version, resourcesBlock(),
                          jaxwsMavenPlugin(wsdlFilename, targetPackage));
    }

    // ── Java 17 & 21 ── jakarta namespace, CXF 4.x runtime ─
    private String buildCxf4Pom(JavaVersion jv,
                                  String groupId, String artifactId, String version,
                                  String wsdlFilename, String targetPackage) {
        String sv = jv.getSourceVersion();
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                                         https://maven.apache.org/xsd/maven-4.0.0.xsd">
                <modelVersion>4.0.0</modelVersion>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <packaging>jar</packaging>

                <properties>
                    <maven.compiler.source>%s</maven.compiler.source>
                    <maven.compiler.target>%s</maven.compiler.target>
                    <cxf.version>4.0.4</cxf.version>
                    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                    <skipWsdlGenerate>false</skipWsdlGenerate>
                </properties>

                <dependencies>
                    <dependency>
                        <groupId>jakarta.xml.ws</groupId>
                        <artifactId>jakarta.xml.ws-api</artifactId>
                        <version>4.0.1</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.cxf</groupId>
                        <artifactId>cxf-rt-frontend-jaxws</artifactId>
                        <version>${cxf.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.apache.cxf</groupId>
                        <artifactId>cxf-rt-transports-http</artifactId>
                        <version>${cxf.version}</version>
                    </dependency>
                    <dependency>
                        <groupId>org.glassfish.jaxb</groupId>
                        <artifactId>jaxb-runtime</artifactId>
                        <version>4.0.4</version>
                    </dependency>
                    <dependency>
                        <groupId>jakarta.activation</groupId>
                        <artifactId>jakarta.activation-api</artifactId>
                        <version>2.1.2</version>
                    </dependency>
                    <dependency>
                        <groupId>org.slf4j</groupId>
                        <artifactId>slf4j-simple</artifactId>
                        <version>2.0.9</version>
                    </dependency>
                </dependencies>

                <build>
                    %s
                    <plugins>
                        <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-compiler-plugin</artifactId>
                            <version>3.11.0</version>
                            <configuration>
                                <source>%s</source>
                                <target>%s</target>
                            </configuration>
                        </plugin>
                        %s
                        %s
                    </plugins>
                </build>
            </project>
            """.formatted(groupId, artifactId, version,
                          sv, sv,
                          resourcesBlock(),
                          sv, sv,
                          cxfCodegenPlugin(wsdlFilename, targetPackage),
                          shadePlugin());
    }

    private String jaxwsMavenPlugin(String wsdlFilename, String targetPackage) {
        return """
            <plugin>
                            <groupId>com.sun.xml.ws</groupId>
                            <artifactId>jaxws-maven-plugin</artifactId>
                            <version>2.3.7</version>
                            <executions>
                                <execution>
                                    <goals><goal>wsimport</goal></goals>
                                </execution>
                            </executions>
                            <configuration>
                                <skip>${skipWsdlGenerate}</skip>
                                <wsdlDirectory>${project.basedir}/src/main/resources/wsdl</wsdlDirectory>
                                <wsdlFiles>
                                    <wsdlFile>%s</wsdlFile>
                                </wsdlFiles>
                                <packageName>%s</packageName>
                                <sourceDestDir>${project.basedir}/src/main/java</sourceDestDir>
                                <keep>true</keep>
                            </configuration>
                        </plugin>""".formatted(wsdlFilename, targetPackage);
    }

    private String cxfCodegenPlugin(String wsdlFilename, String targetPackage) {
        return """
            <plugin>
                            <groupId>org.apache.cxf</groupId>
                            <artifactId>cxf-codegen-plugin</artifactId>
                            <version>4.0.4</version>
                            <executions>
                                <execution>
                                    <goals><goal>wsdl2java</goal></goals>
                                </execution>
                            </executions>
                            <configuration>
                                <skip>${skipWsdlGenerate}</skip>
                                <fork>false</fork>
                                <sourceRoot>${project.basedir}/src/main/java</sourceRoot>
                                <wsdlOptions>
                                    <wsdlOption>
                                        <wsdl>${project.basedir}/src/main/resources/wsdl/%s</wsdl>
                                        <packagenames>
                                            <packagename>%s</packagename>
                                        </packagenames>
                                        <extraargs>
                                            <extraarg>-exsh</extraarg>
                                            <extraarg>true</extraarg>
                                        </extraargs>
                                    </wsdlOption>
                                </wsdlOptions>
                            </configuration>
                        </plugin>""".formatted(wsdlFilename, targetPackage);
    }

    private String resourcesBlock() {
        return """
            <resources>
                        <resource>
                            <directory>src/main/resources</directory>
                            <includes>
                                <include>wsdl/**</include>
                            </includes>
                        </resource>
                    </resources>""";
    }

    private String shadePlugin() {
        return """
            <plugin>
                            <groupId>org.apache.maven.plugins</groupId>
                            <artifactId>maven-shade-plugin</artifactId>
                            <version>3.5.1</version>
                            <executions>
                                <execution>
                                    <phase>package</phase>
                                    <goals><goal>shade</goal></goals>
                                    <configuration>
                                        <transformers>
                                            <transformer implementation="org.apache.maven.plugins.shade.resource.AppendingTransformer">
                                                <resource>META-INF/cxf/bus-extensions.txt</resource>
                                            </transformer>
                                        </transformers>
                                        <filters>
                                            <filter>
                                                <artifact>*:*</artifact>
                                                <excludes>
                                                    <exclude>META-INF/*.SF</exclude>
                                                    <exclude>META-INF/*.DSA</exclude>
                                                    <exclude>META-INF/*.RSA</exclude>
                                                </excludes>
                                            </filter>
                                        </filters>
                                    </configuration>
                                </execution>
                            </executions>
                        </plugin>""";
    }
}
