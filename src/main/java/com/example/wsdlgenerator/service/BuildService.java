package com.example.wsdlgenerator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class BuildService {

    private static final Logger log = LoggerFactory.getLogger(BuildService.class);

    @Value("${app.maven-executable:mvn.cmd}")
    private String mavenExecutable;

    public void build(Path workDir) throws IOException, InterruptedException {
        log.info("Maven executable: {}", mavenExecutable);

        List<String> cmd = List.of(
            mavenExecutable,
            "package",
            "-f", workDir.resolve("pom.xml").toAbsolutePath().toString(),
            "-DskipTests",
            "-DskipWsdlGenerate=true",
            "--batch-mode",
            "-q"
        );

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);

        // Maven'ın kullandığı JDK'yı Spring Boot'un çalıştığı JDK ile eşitle
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            pb.environment().put("JAVA_HOME", javaHome);
            log.info("JAVA_HOME → {}", javaHome);
        }

        Path logFile = workDir.resolve("build.log");
        pb.redirectOutput(logFile.toFile());

        Process process = pb.start();
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Build zaman aşımına uğradı (10 dakika)");
        }

        if (process.exitValue() != 0) {
            String buildLog = Files.readString(logFile);
            String tail = buildLog.length() > 3000
                ? buildLog.substring(buildLog.length() - 3000)
                : buildLog;
            throw new RuntimeException("Maven build başarısız:\n" + tail);
        }
    }
}
