package com.example.wsdlgenerator.controller;

import com.example.wsdlgenerator.model.*;
import com.example.wsdlgenerator.service.GenerationOrchestrator;
import org.springframework.core.io.PathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/jobs")
public class GeneratorApiController {

    private final GenerationOrchestrator orchestrator;

    public GeneratorApiController(GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobStatusResponse> status(@PathVariable String jobId) {
        try {
            GenerationJob job = orchestrator.getJob(jobId);
            return ResponseEntity.ok(JobStatusResponse.from(job));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{jobId}/download")
    public ResponseEntity<PathResource> download(@PathVariable String jobId) {
        GenerationJob job = orchestrator.getJob(jobId);

        if (job.getStatus() != JobStatus.COMPLETED || job.getZipPath() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PathResource resource = new PathResource(job.getZipPath());
        String filename = job.getZipPath().getFileName().toString();

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    ContentDisposition.attachment().filename(filename).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM)
            .body(resource);
    }
}
