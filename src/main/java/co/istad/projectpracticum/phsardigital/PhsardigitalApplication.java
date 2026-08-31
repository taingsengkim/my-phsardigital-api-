package co.istad.projectpracticum.phsardigital;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableJpaAuditing
// Bakong publishes no webhook, so a payment nobody polls for is only ever noticed by
// PaymentExpirySweeper. Without scheduling, an abandoned checkout that was in fact paid
// would sit pending forever.
@EnableScheduling
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class PhsardigitalApplication {

    public static void main(String[] args) {
        SpringApplication.run(PhsardigitalApplication.class, args);
    }

}
