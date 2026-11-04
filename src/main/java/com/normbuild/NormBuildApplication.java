package com.normbuild;

import com.normbuild.config.NormBuildAiProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
@EnableConfigurationProperties(NormBuildAiProperties.class)
public class NormBuildApplication {

    public static void main(String[] args) {
        SpringApplication.run(NormBuildApplication.class, args);
    }
}
