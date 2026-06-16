package com.example.wsdlgenerator.service;

import com.example.wsdlgenerator.model.JavaVersion;
import com.example.wsdlgenerator.model.WsdlMetadata;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class ServiceBeanGeneratorService {

    /** WSDL serviceName'den bean sınıf adı üretir: "MuhasebeService" → "MuhasebeServiceBean" */
    public String beanClassName(String serviceName) {
        String base = toPascalCase(serviceName);
        return base.endsWith("Service") ? base + "Bean" : base + "ServiceBean";
    }

    public void generate(JavaVersion javaVersion,
                         WsdlMetadata meta,
                         String targetPackage,
                         String prodWsdlFilename,
                         String testWsdlFilename,
                         Path workDir) throws IOException {

        boolean jakarta      = javaVersion.isJakarta();
        String wsPackage     = jakarta ? "jakarta.xml.ws" : "javax.xml.ws";
        String secPrefix     = javaVersion.wsSecurityPrefix();

        String packageName   = targetPackage + ".util";
        String stubPackage   = targetPackage;
        String className     = beanClassName(meta.getServiceName());
        String portTypeClass = meta.getPortTypeName();
        String serviceClass  = meta.getServiceName();
        String namespace     = meta.getTargetNamespace();
        String portName      = meta.getPortName();

        String src = """
            package %s;

            import %s.BindingProvider;
            import %s.Service;
            import %s.%s;
            import javax.xml.namespace.QName;
            import java.net.URL;
            import java.util.Map;

            /**
             * Singleton SOAP client helper.
             * <p>
             * Ortam seçimi (env var öncelikli, system property fallback):
             * <ul>
             *   <li>SOA_ENVIRONMENT / soa.environment  →  "test" | "prod" (default: prod)</li>
             *   <li>SOA_WEBSERVICE_USERNAME / soa.webservice.username</li>
             *   <li>SOA_WEBSERVICE_PASSWORD / soa.webservice.password</li>
             * </ul>
             */
            public class %s {

                public static final %s SINGLETON = new %s();

                private final %s port;

                private %s() {
                    String env      = getConfigValue("SOA_ENVIRONMENT",        "soa.environment");
                    String username = getConfigValue("SOA_WEBSERVICE_USERNAME", "soa.webservice.username");
                    String password = getConfigValue("SOA_WEBSERVICE_PASSWORD", "soa.webservice.password");

                    String wsdlFile = "test".equalsIgnoreCase(env)
                        ? "wsdl/%s"
                        : "wsdl/%s";

                    URL wsdlUrl = getClass().getClassLoader().getResource(wsdlFile);
                    if (wsdlUrl == null) {
                        throw new IllegalStateException("WSDL bulunamadı: " + wsdlFile);
                    }

                    QName qname  = new QName("%s", "%s");
                    Service svc  = Service.create(wsdlUrl, qname);
                    this.port    = svc.getPort(%s.class);

                    Map<String, Object> ctx = ((BindingProvider) port).getRequestContext();
                    ctx.put("%s.security.auth.username", username);
                    ctx.put("%s.security.auth.password", password);
                }

                public %s getPort() {
                    return port;
                }

                private static String getConfigValue(String envKey, String propKey) {
                    String val = System.getenv(envKey);
                    if (val != null && !val.isEmpty()) return val;
                    return System.getProperty(propKey, "");
                }
            }
            """.formatted(
                packageName,
                wsPackage, wsPackage,
                stubPackage, portTypeClass,
                // class declaration
                className,
                className, className,
                portTypeClass,
                className,
                // wsdl file selection
                testWsdlFilename,
                prodWsdlFilename,
                // QName
                namespace, serviceClass,
                portTypeClass,
                // security keys
                secPrefix, secPrefix,
                // getPort return type
                portTypeClass
            );

        // src/main/java/{package path}/util/{ClassName}.java
        String packagePath = packageName.replace('.', '/');
        Path dir = workDir.resolve("src/main/java").resolve(packagePath);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(className + ".java"), src);
    }

    private String toPascalCase(String kebabOrDot) {
        StringBuilder sb = new StringBuilder();
        for (String part : kebabOrDot.split("[-_.]")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1));
            }
        }
        return sb.toString();
    }

    private String toPackagePart(String artifactId) {
        return artifactId.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
