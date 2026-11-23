package com.cocos.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
        title = "Cocos Challenge API",
        version = "1.0.0",
        description = "API de instrumentos, portfolios y órdenes de inversión en ARS. "
                + "Las órdenes LIMIT no simulan ejecución posterior."))
public class ChallengeCocosApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChallengeCocosApplication.class, args);
    }
}
