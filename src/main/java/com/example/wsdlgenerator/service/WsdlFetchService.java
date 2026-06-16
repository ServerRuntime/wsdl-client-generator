package com.example.wsdlgenerator.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;

@Service
public class WsdlFetchService {

    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS    = 30_000;

    public Path fetch(String wsdlUrl, MultipartFile wsdlFile, Path destFile) throws IOException {
        if (wsdlFile != null && !wsdlFile.isEmpty()) {
            Files.createDirectories(destFile.getParent());
            wsdlFile.transferTo(destFile);
            return destFile;
        }
        if (wsdlUrl != null && !wsdlUrl.isBlank()) {
            validateUrl(wsdlUrl);
            Files.createDirectories(destFile.getParent());
            URL url = URI.create(wsdlUrl.trim()).toURL();
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("Accept", "text/xml,application/xml,*/*");
            try (InputStream in = conn.getInputStream()) {
                Files.copy(in, destFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return destFile;
        }
        throw new IllegalArgumentException("WSDL URL veya dosya girilmelidir");
    }

    private void validateUrl(String url) {
        String lower = url.toLowerCase().trim();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw new IllegalArgumentException("Yalnızca HTTP/HTTPS URL kabul edilir");
        }
        if (lower.contains("localhost") || lower.contains("127.0.0.1")
         || lower.contains("169.254")   || lower.contains("::1")) {
            throw new IllegalArgumentException("Loopback/link-local adreslere izin verilmez");
        }
    }
}
