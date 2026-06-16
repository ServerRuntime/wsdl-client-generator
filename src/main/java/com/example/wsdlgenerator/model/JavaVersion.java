package com.example.wsdlgenerator.model;

public enum JavaVersion {
    JAVA_8 ("8",  "1.8", "jaxws-ri", false),
    JAVA_11("11", "11",  "jaxws-ri", false),
    JAVA_17("17", "17",  "cxf4",     true),
    JAVA_21("21", "21",  "cxf4",     true);

    private final String label;
    private final String sourceVersion;
    private final String toolchain;
    private final boolean jakarta;

    JavaVersion(String label, String sourceVersion, String toolchain, boolean jakarta) {
        this.label         = label;
        this.sourceVersion = sourceVersion;
        this.toolchain     = toolchain;
        this.jakarta       = jakarta;
    }

    public String  getLabel()         { return label; }
    public String  getSourceVersion() { return sourceVersion; }
    public String  getToolchain()     { return toolchain; }
    public boolean isJakarta()        { return jakarta; }
    public boolean usesCxf()          { return toolchain.startsWith("cxf"); }

    /** WS-Security context key prefix based on namespace */
    public String wsSecurityPrefix()  { return jakarta ? "jakarta.xml.ws" : "javax.xml.ws"; }
}
