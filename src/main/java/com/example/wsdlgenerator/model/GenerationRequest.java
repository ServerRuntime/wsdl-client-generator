package com.example.wsdlgenerator.model;

import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

public class GenerationRequest {

    // Prod WSDL
    private String wsdlUrlProd;
    private MultipartFile wsdlFileProd;

    // Test WSDL (opsiyonel — girilmezse prod ile aynı kabul edilir)
    private String wsdlUrlTest;
    private MultipartFile wsdlFileTest;

    @NotNull(message = "Java versiyonu seçiniz")
    private JavaVersion javaVersion;

    /** Kod üretimi hangi WSDL'den yapılsın: PROD (default) veya TEST */
    private String generateFrom = "PROD";

    private String groupId    = "com.example";
    private String artifactId = "soap-client";
    private String version    = "1.0.0";

    public boolean hasTestWsdl() {
        return (wsdlUrlTest != null && !wsdlUrlTest.isBlank())
            || (wsdlFileTest != null && !wsdlFileTest.isEmpty());
    }

    public String        getWsdlUrlProd()              { return wsdlUrlProd; }
    public void          setWsdlUrlProd(String v)       { this.wsdlUrlProd = v; }

    public MultipartFile getWsdlFileProd()              { return wsdlFileProd; }
    public void          setWsdlFileProd(MultipartFile v){ this.wsdlFileProd = v; }

    public String        getWsdlUrlTest()              { return wsdlUrlTest; }
    public void          setWsdlUrlTest(String v)       { this.wsdlUrlTest = v; }

    public MultipartFile getWsdlFileTest()              { return wsdlFileTest; }
    public void          setWsdlFileTest(MultipartFile v){ this.wsdlFileTest = v; }

    public JavaVersion   getJavaVersion()               { return javaVersion; }
    public void          setJavaVersion(JavaVersion v)  { this.javaVersion = v; }

    public String        getGroupId()                   { return groupId; }
    public void          setGroupId(String v)           { this.groupId = v; }

    public String        getArtifactId()                { return artifactId; }
    public void          setArtifactId(String v)        { this.artifactId = v; }

    public String        getVersion()                   { return version; }
    public void          setVersion(String v)           { this.version = v; }

    public String        getGenerateFrom()              { return generateFrom; }
    public void          setGenerateFrom(String v)      { this.generateFrom = (v != null ? v.toUpperCase() : "PROD"); }

    public boolean       isGenerateFromTest()           { return "TEST".equals(generateFrom); }
}
