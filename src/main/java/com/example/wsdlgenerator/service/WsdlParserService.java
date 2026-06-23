package com.example.wsdlgenerator.service;

import com.example.wsdlgenerator.model.WsdlMetadata;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.nio.file.Path;

@Service
public class WsdlParserService {

    public WsdlMetadata parse(Path wsdlFile) {
        Document doc = loadDocument(wsdlFile);
        Element root = doc.getDocumentElement();

        String targetNamespace = root.getAttribute("targetNamespace");

        String serviceName  = findFirstAttr(doc, "service",  "name");
        String portTypeName = findFirstAttr(doc, "portType", "name");
        String portName     = findFirstPortName(doc);

        if (serviceName  == null) serviceName  = "UnknownService";
        if (portTypeName == null) portTypeName = serviceName + "PortType";
        if (portName     == null) portName     = serviceName + "Port";

        return new WsdlMetadata(targetNamespace, serviceName, portTypeName, portName);
    }

    private Document loadDocument(Path wsdlFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE koruması — DOCTYPE'a izin ver, sadece external entity erişimi engelle
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setExpandEntityReferences(false);

            return factory.newDocumentBuilder().parse(wsdlFile.toFile());
        } catch (Exception e) {
            throw new RuntimeException("WSDL parse hatası: " + e.getMessage(), e);
        }
    }

    private String findFirstAttr(Document doc, String localName, String attrName) {
        // hem wsdl: prefix'li hem prefix'siz elementleri dene
        NodeList list = doc.getElementsByTagNameNS("*", localName);
        if (list.getLength() == 0) list = doc.getElementsByTagName(localName);
        if (list.getLength() > 0) {
            Element el = (Element) list.item(0);
            String val = el.getAttribute(attrName);
            if (!val.isBlank()) return val;
        }
        return null;
    }

    private String findFirstPortName(Document doc) {
        // <service><port name="..."> al
        NodeList services = doc.getElementsByTagNameNS("*", "service");
        if (services.getLength() == 0) services = doc.getElementsByTagName("service");
        if (services.getLength() > 0) {
            NodeList children = services.item(0).getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child instanceof Element el) {
                    String localName = el.getLocalName();
                    if ("port".equals(localName) || "endpoint".equals(localName)) {
                        String name = el.getAttribute("name");
                        if (!name.isBlank()) return name;
                    }
                }
            }
        }
        return null;
    }
}
