package com.example.wsdlgenerator.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

public class GenerationJob {

    private final String id;
    private volatile JobStatus status;
    private volatile String message;
    private volatile int progress;       // 0-100
    private volatile Path zipPath;
    private volatile String errorDetail;
    private final LocalDateTime createdAt;
    private final JavaVersion javaVersion;
    private final String artifactId;

    public GenerationJob(String id, JavaVersion javaVersion, String artifactId) {
        this.id = id;
        this.javaVersion = javaVersion;
        this.artifactId = artifactId;
        this.status = JobStatus.PENDING;
        this.message = "İş kuyruğa alındı";
        this.progress = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void update(JobStatus status, String message, int progress) {
        this.status = status;
        this.message = message;
        this.progress = progress;
    }

    public void complete(Path zipPath) {
        this.status = JobStatus.COMPLETED;
        this.message = "Tamamlandı";
        this.progress = 100;
        this.zipPath = zipPath;
    }

    public void fail(String errorDetail) {
        this.status = JobStatus.FAILED;
        this.message = "Hata oluştu";
        this.progress = 0;
        this.errorDetail = errorDetail;
    }

    public String getId()              { return id; }
    public JobStatus getStatus()       { return status; }
    public String getMessage()         { return message; }
    public int getProgress()           { return progress; }
    public Path getZipPath()           { return zipPath; }
    public String getErrorDetail()     { return errorDetail; }
    public LocalDateTime getCreatedAt(){ return createdAt; }
    public JavaVersion getJavaVersion(){ return javaVersion; }
    public String getArtifactId()      { return artifactId; }
}
