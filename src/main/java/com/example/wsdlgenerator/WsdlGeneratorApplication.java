package com.example.wsdlgenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class WsdlGeneratorApplication {
    public static void main(String[] args) {

       // CountryInfoServiceSoapType port = CountryTestServiceBean.SINGLETON.getPort();
        //ArrayOftContinent arrayOftContinent = port.listOfContinentsByName();
        //System.out.println(arrayOftContinent);
        SpringApplication.run(WsdlGeneratorApplication.class, args);
    }
}
