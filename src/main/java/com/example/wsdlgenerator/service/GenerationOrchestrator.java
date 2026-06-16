package com.example.wsdlgenerator.service;

import com.example.wsdlgenerator.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GenerationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(GenerationOrchestrator.class);

    private final Map<String, GenerationJob> jobs = new ConcurrentHashMap<>();

    private final WsdlFetchService            wsdlFetchService;
    private final WsdlParserService           wsdlParserService;
    private final CodeGenService              codeGenService;
    private final PomGeneratorService         pomGeneratorService;
    private final ServiceBeanGeneratorService serviceBeanGeneratorService;
    private final BuildService                buildService;
    private final ZipPackageService           zipPackageService;

    @Value("${app.temp-dir:${java.io.tmpdir}/wsdl-generator}")
    private String tempDirBase;

    public GenerationOrchestrator(WsdlFetchService wsdlFetchService,
                                   WsdlParserService wsdlParserService,
                                   CodeGenService codeGenService,
                                   PomGeneratorService pomGeneratorService,
                                   ServiceBeanGeneratorService serviceBeanGeneratorService,
                                   BuildService buildService,
                                   ZipPackageService zipPackageService) {
        this.wsdlFetchService            = wsdlFetchService;
        this.wsdlParserService           = wsdlParserService;
        this.codeGenService              = codeGenService;
        this.pomGeneratorService         = pomGeneratorService;
        this.serviceBeanGeneratorService = serviceBeanGeneratorService;
        this.buildService                = buildService;
        this.zipPackageService           = zipPackageService;
    }

    public GenerationJob createJob(GenerationRequest request) {
        String jobId = UUID.randomUUID().toString();
        GenerationJob job = new GenerationJob(jobId, request.getJavaVersion(), request.getArtifactId());
        jobs.put(jobId, job);
        runAsync(job, request);
        return job;
    }

    public GenerationJob getJob(String jobId) {
        GenerationJob job = jobs.get(jobId);
        if (job == null) throw new IllegalArgumentException("Job bulunamadı: " + jobId);
        return job;
    }

    @Async("generatorExecutor")
    public void runAsync(GenerationJob job, GenerationRequest request) {
        try {
            Path baseTmp = Path.of(tempDirBase);
            Files.createDirectories(baseTmp);
            Path workDir = Files.createTempDirectory(baseTmp, "job-" + job.getId() + "-");

            // 1. WSDL indir
            job.update(JobStatus.FETCHING_WSDL, "WSDL(ler) indiriliyor...", 10);

            String artifactId = request.getArtifactId();

            // Prod WSDL
            String prodFilename = artifactId + "-Prod.wsdl";
            Path wsdlDir = workDir.resolve("src/main/resources/wsdl");
            Path prodPath = wsdlFetchService.fetch(
                request.getWsdlUrlProd(), request.getWsdlFileProd(),
                wsdlDir.resolve(prodFilename));

            // Test WSDL — girilmediyse prod kopyalanır
            String testFilename = artifactId + "-Test.wsdl";
            if (request.hasTestWsdl()) {
                wsdlFetchService.fetch(
                    request.getWsdlUrlTest(), request.getWsdlFileTest(),
                    wsdlDir.resolve(testFilename));
            } else {
                Files.copy(prodPath, wsdlDir.resolve(testFilename),
                           StandardCopyOption.REPLACE_EXISTING);
            }

            // 2. WSDL parse → metadata (her zaman Prod WSDL'den — canonical tanım)
            job.update(JobStatus.GENERATING_CODE, "WSDL analiz ediliyor...", 25);
            WsdlMetadata meta = wsdlParserService.parse(prodPath);
            log.info("Job {} [generateFrom={}] — namespace={} service={} portType={}",
                     job.getId(), request.getGenerateFrom(),
                     meta.getTargetNamespace(), meta.getServiceName(), meta.getPortTypeName());

            // 3. Kod üretimi (WSDLToJava programatik — Maven plugin yok)
            boolean fromTest  = request.isGenerateFromTest();
            Path codegenWsdl  = fromTest ? wsdlDir.resolve(testFilename) : prodPath;
            Path sourceDir    = workDir.resolve("src/main/java");

            // CXF'in namespace'den türettiği paketle eşleşmesi için aynı algoritmayı kullan
            String targetPackage = namespaceToPackage(meta.getTargetNamespace());

            job.update(JobStatus.GENERATING_CODE,
                       "Java stub sınıfları üretiliyor (" + (fromTest ? "Test" : "Prod") + " WSDL)...", 40);
            codeGenService.generate(request.getJavaVersion(), codegenWsdl, sourceDir, targetPackage);

            // 4. pom.xml üret
            job.update(JobStatus.GENERATING_CODE, "pom.xml oluşturuluyor...", 35);
            pomGeneratorService.generate(
                request.getJavaVersion(),
                request.getGroupId(), artifactId, request.getVersion(),
                prodFilename, testFilename,
                request.getGenerateFrom(),
                targetPackage,
                workDir);

            // 4. ServiceBean util sınıfı üret
            job.update(JobStatus.GENERATING_CODE, "ServiceBean util sınıfı üretiliyor...", 45);
            serviceBeanGeneratorService.generate(
                request.getJavaVersion(), meta,
                targetPackage,
                prodFilename, testFilename,
                workDir);

            // 5. Maven build
            job.update(JobStatus.BUILDING,
                       "Maven build çalışıyor (bu birkaç dakika sürebilir)...", 55);
            buildService.build(workDir);

            // 6. ZIP
            job.update(JobStatus.PACKAGING, "ZIP oluşturuluyor...", 88);
            Path zipPath = zipPackageService.packageZip(
                workDir, artifactId, request.getVersion());

            job.complete(zipPath);
            log.info("Job {} tamamlandı: {}", job.getId(), zipPath);

        } catch (Exception e) {
            log.error("Job {} başarısız: {}", job.getId(), e.getMessage(), e);
            job.fail(e.getMessage());
        }
    }

    /**
     * http://mali.ibb.gov.tr/muhasebe → tr.gov.ibb.mali.muhasebe
     * CXF'in namespace-to-package dönüşümüyle aynı algoritma.
     */
    static String namespaceToPackage(String namespace) {
        if (namespace == null || namespace.isBlank()) return "generated";
        String s = namespace.replaceFirst("^https?://", "").replaceFirst("^urn:", "");
        String[] parts = s.split("[/:]");
        if (parts.length == 0) return "generated";

        String[] domainParts = parts[0].split("\\.");
        List<String> segments = new ArrayList<>(Arrays.asList(domainParts));
        segments.removeIf(p -> p.equalsIgnoreCase("www"));
        Collections.reverse(segments);

        for (int i = 1; i < parts.length; i++) {
            String seg = parts[i].toLowerCase().replaceAll("[^a-z0-9]", "");
            if (!seg.isEmpty()) segments.add(seg);
        }

        return segments.stream()
            .map(p -> p.toLowerCase().replaceAll("[^a-z0-9]", ""))
            .filter(p -> !p.isEmpty())
            .collect(Collectors.joining("."));
    }

    public void cleanupExpiredJobs(int ttlMinutes) {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(ttlMinutes);
        jobs.values().removeIf(job -> {
            boolean expired = job.getCreatedAt().isBefore(cutoff)
                           && (job.getStatus() == JobStatus.COMPLETED
                            || job.getStatus() == JobStatus.FAILED);
            if (expired && job.getZipPath() != null) {
                try { Files.deleteIfExists(job.getZipPath()); } catch (Exception ignored) {}
            }
            return expired;
        });
    }
}
