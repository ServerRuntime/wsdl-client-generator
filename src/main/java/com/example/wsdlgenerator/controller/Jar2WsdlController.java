package com.example.wsdlgenerator.controller;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.*;

@RestController
@RequestMapping("/jar2wsdl")
public class Jar2WsdlController {

    // jobId → { entryName → bytes }
    private final ConcurrentHashMap<String, Map<String, byte[]>> jobs = new ConcurrentHashMap<>();

    @PostMapping("/extract")
    public ResponseEntity<?> extract(@RequestParam("jar") MultipartFile file) {
        if (file.isEmpty()) return bad("Dosya boş.");
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".jar"))
            return bad("Sadece .jar dosyaları desteklenir.");

        Map<String, byte[]> found = new LinkedHashMap<>();
        try (JarInputStream jis = new JarInputStream(file.getInputStream())) {
            JarEntry entry;
            while ((entry = jis.getNextJarEntry()) != null) {
                String name = entry.getName();
                if (!entry.isDirectory() && (name.toLowerCase().endsWith(".wsdl") || name.toLowerCase().endsWith(".xml") && name.toLowerCase().contains("wsdl"))) {
                    found.put(name, jis.readAllBytes());
                }
                jis.closeEntry();
            }
        } catch (Exception e) {
            return bad("JAR okunamadı: " + e.getMessage());
        }

        String jobId = UUID.randomUUID().toString();
        jobs.put(jobId, found);

        List<Map<String, String>> entries = found.keySet().stream()
            .map(n -> Map.of("name", n))
            .toList();

        return ResponseEntity.ok(Map.of("jobId", jobId, "entries", entries));
    }

    @GetMapping("/download")
    public ResponseEntity<?> downloadOne(@RequestParam String jobId,
                                         @RequestParam String name) throws IOException {
        Map<String, byte[]> job = jobs.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        String decoded = URLDecoder.decode(name, StandardCharsets.UTF_8);
        byte[] bytes = job.get(decoded);
        if (bytes == null) return ResponseEntity.notFound().build();

        String filename = decoded.contains("/") ? decoded.substring(decoded.lastIndexOf('/') + 1) : decoded;
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_XML)
            .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/download-all")
    public ResponseEntity<?> downloadAll(@RequestParam String jobId) throws IOException {
        Map<String, byte[]> job = jobs.get(jobId);
        if (job == null) return ResponseEntity.notFound().build();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> e : job.entrySet()) {
                String filename = e.getKey().contains("/")
                    ? e.getKey().substring(e.getKey().lastIndexOf('/') + 1)
                    : e.getKey();
                zos.putNextEntry(new ZipEntry(filename));
                zos.write(e.getValue());
                zos.closeEntry();
            }
        }

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wsdl-files.zip\"")
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(new ByteArrayResource(baos.toByteArray()));
    }

    private ResponseEntity<Map<String, String>> bad(String msg) {
        return ResponseEntity.badRequest().body(Map.of("message", msg));
    }
}
