package com.example.wsdlgenerator.controller;

import com.example.wsdlgenerator.model.*;
import com.example.wsdlgenerator.service.GenerationOrchestrator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class GeneratorController {

    private final GenerationOrchestrator orchestrator;

    @Value("${app.url-input-enabled:true}")
    private boolean urlInputEnabled;

    public GeneratorController(GenerationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("javaVersions", JavaVersion.values());
        model.addAttribute("urlInputEnabled", urlInputEnabled);
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam(value = "wsdlUrlProd",  required = false) String wsdlUrlProd,
            @RequestParam(value = "wsdlFileProd", required = false) MultipartFile wsdlFileProd,
            @RequestParam(value = "wsdlUrlTest",  required = false) String wsdlUrlTest,
            @RequestParam(value = "wsdlFileTest", required = false) MultipartFile wsdlFileTest,
            @RequestParam("javaVersion")                            JavaVersion javaVersion,
            @RequestParam(value = "groupId",    defaultValue = "com.example") String groupId,
            @RequestParam(value = "artifactId", defaultValue = "soap-client") String artifactId,
            @RequestParam(value = "version",      defaultValue = "1.0.0") String version,
            @RequestParam(value = "generateFrom", defaultValue = "PROD")   String generateFrom,
            Model model) {

        boolean noProd = (wsdlUrlProd == null || wsdlUrlProd.isBlank())
                      && (wsdlFileProd == null || wsdlFileProd.isEmpty());
        if (noProd) {
            model.addAttribute("error", "En az Prod WSDL URL veya dosyası girilmelidir.");
            model.addAttribute("javaVersions", JavaVersion.values());
            return "index";
        }

        GenerationRequest req = new GenerationRequest();
        req.setWsdlUrlProd(wsdlUrlProd);
        req.setWsdlFileProd(wsdlFileProd);
        req.setWsdlUrlTest(wsdlUrlTest);
        req.setWsdlFileTest(wsdlFileTest);
        req.setJavaVersion(javaVersion);
        req.setGroupId(groupId);
        req.setArtifactId(artifactId);
        req.setVersion(version);
        req.setGenerateFrom(generateFrom);

        GenerationJob job = orchestrator.createJob(req);
        return "redirect:/status/" + job.getId();
    }

    @GetMapping("/status/{jobId}")
    public String status(@PathVariable String jobId, Model model) {
        model.addAttribute("job", orchestrator.getJob(jobId));
        return "status";
    }

    @GetMapping("/jar2wsdl")
    public String jar2wsdl() {
        return "jar2wsdl";
    }
}
