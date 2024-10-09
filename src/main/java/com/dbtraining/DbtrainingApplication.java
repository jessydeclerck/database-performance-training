package com.dbtraining;

import com.dbtraining.init.DataLoader;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DbtrainingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DbtrainingApplication.class, args);
    }

    @Bean
    @ConditionalOnProperty(name = "db.generate-data", havingValue = "true")
    public ApplicationRunner startupRunner(DataLoader dataLoader) {
        return args -> dataLoader.run();
    }

}
