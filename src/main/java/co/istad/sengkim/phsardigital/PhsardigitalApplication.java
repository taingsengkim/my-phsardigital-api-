package co.istad.sengkim.phsardigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class PhsardigitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhsardigitalApplication.class, args);
    }

}
