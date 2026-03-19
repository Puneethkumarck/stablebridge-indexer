package com.stablebridge.indexer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StablebridgeIndexerApplication {

    public static void main(String[] args) {
        SpringApplication.run(StablebridgeIndexerApplication.class, args);
    }
}
