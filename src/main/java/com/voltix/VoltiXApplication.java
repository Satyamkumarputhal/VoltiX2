package com.voltix;

import com.voltix.platform.config.VoltixProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableConfigurationProperties(VoltixProperties.class)
@EnableTransactionManagement
public class VoltiXApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoltiXApplication.class, args);
    }
}
