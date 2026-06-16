package com.example.wsdlgenerator.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipPackageService {

    public Path packageZip(Path workDir, String artifactId, String version) throws IOException {
        Path zipFile = workDir.getParent().resolve(artifactId + "-" + version + "-client.zip");

        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // pom.xml
            addFile(zos, workDir.resolve("pom.xml"), "pom.xml");

            // Local .m2'ye kurulum scripti (Windows .bat + cross-platform .sh)
            addBytes(zos, installBat(artifactId, version), "install-local-m2.bat");
            addBytes(zos, installSh(artifactId, version), "install-local-m2.sh");

            // WSDL dosyaları — Maven standart dizini
            Path wsdlDir = workDir.resolve("src/main/resources/wsdl");
            if (Files.exists(wsdlDir)) {
                try (var stream = Files.walk(wsdlDir, 1)) {
                    stream.filter(p -> !Files.isDirectory(p))
                          .forEach(p -> addSilent(zos, p,
                              "src/main/resources/wsdl/" + p.getFileName()));
                }
            }

            // Tüm Java kaynak dosyaları (stub'lar + ServiceBean)
            Path javaDir = workDir.resolve("src/main/java");
            if (Files.exists(javaDir)) {
                try (var stream = Files.walk(javaDir)) {
                    stream.filter(p -> !Files.isDirectory(p) && p.toString().endsWith(".java"))
                          .forEach(p -> {
                              String rel = "src/main/java/" + javaDir.relativize(p).toString()
                                              .replace('\\', '/');
                              addSilent(zos, p, rel);
                          });
                }
            }

            // JAR (shaded, -original hariç)
            Path targetDir = workDir.resolve("target");
            if (Files.exists(targetDir)) {
                try (var stream = Files.walk(targetDir, 1)) {
                    stream.filter(p -> p.toString().endsWith(".jar")
                                   && !p.getFileName().toString().contains("original")
                                   && !p.getFileName().toString().contains("sources")
                                   && !p.getFileName().toString().contains("javadoc"))
                          .forEach(p -> addSilent(zos, p, "lib/" + p.getFileName()));
                }
            }

            // build.log (debug için)
            Path buildLog = workDir.resolve("build.log");
            if (Files.exists(buildLog)) addFile(zos, buildLog, "build.log");
        }

        return zipFile;
    }

    private void addFile(ZipOutputStream zos, Path file, Object entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName.toString()));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private void addSilent(ZipOutputStream zos, Path file, Object entryName) {
        try { addFile(zos, file, entryName); } catch (IOException e) { throw new RuntimeException(e); }
    }

    private void addBytes(ZipOutputStream zos, String content, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String installBat(String artifactId, String version) {
        String jarName = artifactId + "-" + version + ".jar";
        return "@echo off\r\n"
             + "setlocal\r\n"
             + "echo Installing " + jarName + " into local Maven repository (.m2)...\r\n"
             + "mvn install:install-file \"-Dfile=lib\\" + jarName + "\" \"-DpomFile=pom.xml\"\r\n"
             + "if %ERRORLEVEL% NEQ 0 (\r\n"
             + "    echo.\r\n"
             + "    echo Install FAILED.\r\n"
             + "    pause\r\n"
             + "    exit /b %ERRORLEVEL%\r\n"
             + ")\r\n"
             + "echo.\r\n"
             + "echo Done.\r\n"
             + "pause\r\n";
    }

    private String installSh(String artifactId, String version) {
        String jarName = artifactId + "-" + version + ".jar";
        return "#!/bin/sh\n"
             + "set -e\n"
             + "echo \"Installing " + jarName + " into local Maven repository (.m2)...\"\n"
             + "mvn install:install-file \"-Dfile=lib/" + jarName + "\" \"-DpomFile=pom.xml\"\n"
             + "echo \"Done.\"\n";
    }
}
