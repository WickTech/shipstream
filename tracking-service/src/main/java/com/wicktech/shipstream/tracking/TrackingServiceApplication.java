package com.wicktech.shipstream.tracking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TrackingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(TrackingServiceApplication.class, args);
    }
}
