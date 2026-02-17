package com.buildquote;

import com.buildquote.config.IfcParserProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties(IfcParserProperties.class)
@EnableScheduling
@EnableAsync
public class BuildQuoteApplication {
    public static void main(String[] args) {
        SpringApplication.run(BuildQuoteApplication.class, args);
    }
}
