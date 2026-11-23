package com.cocos.challenge.adapter.in.spring;

import com.cocos.challenge.application.port.out.AccountPort;
import com.cocos.challenge.application.port.out.InstrumentCatalogPort;
import com.cocos.challenge.application.port.out.OrderPort;
import com.cocos.challenge.application.port.out.PortfolioPort;
import com.cocos.challenge.application.service.InstrumentApplicationService;
import com.cocos.challenge.application.service.OrderApplicationService;
import com.cocos.challenge.application.service.PortfolioApplicationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    InstrumentApplicationService instrumentApplicationService(InstrumentCatalogPort instruments) {
        return new InstrumentApplicationService(instruments);
    }

    @Bean
    PortfolioApplicationService portfolioApplicationService(AccountPort accounts, PortfolioPort portfolios) {
        return new PortfolioApplicationService(accounts, portfolios);
    }

    @Bean
    OrderApplicationService orderApplicationService(AccountPort accounts, InstrumentCatalogPort instruments,
                                                     PortfolioPort portfolios, OrderPort orders) {
        return new OrderApplicationService(accounts, instruments, portfolios, orders);
    }
}
