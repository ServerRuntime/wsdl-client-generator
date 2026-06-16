package com.example.wsdlgenerator.model;

/** WSDL'den parse edilen servis metadata */
public class WsdlMetadata {

    private final String targetNamespace;
    private final String serviceName;
    private final String portTypeName;
    private final String portName;

    public WsdlMetadata(String targetNamespace, String serviceName,
                        String portTypeName, String portName) {
        this.targetNamespace = targetNamespace;
        this.serviceName     = serviceName;
        this.portTypeName    = portTypeName;
        this.portName        = portName;
    }

    public String getTargetNamespace() { return targetNamespace; }
    public String getServiceName()     { return serviceName; }
    public String getPortTypeName()    { return portTypeName; }
    public String getPortName()        { return portName; }
}
