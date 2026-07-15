package com.voltix;

import com.voltix.platform.config.VoltixProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(VoltixProperties.class)
public class VoltiXApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoltiXApplication.class, args);
    }
}
