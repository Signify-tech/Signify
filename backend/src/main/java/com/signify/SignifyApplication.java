package com.signify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SignifyApplication {

    public static void main(String[] args) {
        SpringApplication.run(SignifyApplication.class, args);
    }
}
