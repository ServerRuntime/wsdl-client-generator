package com.example.wsdlgenerator.service;

import com.example.wsdlgenerator.model.JavaVersion;
import org.apache.cxf.tools.common.ToolContext;
import org.apache.cxf.tools.wsdlto.WSDLToJava;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;

@Service
public class CodeGenService {

    private static final Logger log = LoggerFactory.getLogger(CodeGenService.class);

    public void generate(JavaVersion javaVersion, Path wsdlPath, Path sourceDir,
                         String targetPackage) throws Exception {
        Files.createDirectories(sourceDir);

        String[] args = {
            "-d",       sourceDir.toAbsolutePath().toString(),
            "-p",       targetPackage,
            "-verbose",
            "-exsh",    "true",
            wsdlPath.toAbsolutePath().toString()
        };

        log.info("WSDLToJava başlatılıyor: wsdl={} outDir={}", wsdlPath, sourceDir);

        WSDLToJava wsdl2java = new WSDLToJava(args);
        wsdl2java.run(new ToolContext());

        log.info("WSDLToJava tamamlandı");

        // CXF 4.x her zaman jakarta.* üretir; Java 8/11 için javax.* gerekli
        if (javaVersion == JavaVersion.JAVA_8 || javaVersion == JavaVersion.JAVA_11) {
            rewriteJakartaToJavax(sourceDir);
        }
    }

    private void rewriteJakartaToJavax(Path sourceDir) throws IOException {
        try (var stream = Files.walk(sourceDir)) {
            List<Path> javaFiles = stream
                .filter(p -> p.toString().endsWith(".java"))
                .toList();
            for (Path file : javaFiles) {
                String content = Files.readString(file);
                String updated = content
                    .replace("jakarta.xml.bind.", "javax.xml.bind.")
                    .replace("jakarta.xml.ws.",   "javax.xml.ws.")
                    .replace("jakarta.jws.",       "javax.jws.");
                if (!updated.equals(content)) {
                    Files.writeString(file, updated);
                }
            }
        }
        log.info("jakarta → javax namespace dönüşümü tamamlandı: {}", sourceDir);
    }
}
