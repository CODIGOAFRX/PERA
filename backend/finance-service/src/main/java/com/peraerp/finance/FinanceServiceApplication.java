package com.peraerp.finance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
@SpringBootApplication(scanBasePackages={"com.peraerp.finance","com.peraerp.platform"})
@EnableJpaAuditing
public class FinanceServiceApplication {
    public static void main(String[] args) { SpringApplication.run(FinanceServiceApplication.class,args); }
}
