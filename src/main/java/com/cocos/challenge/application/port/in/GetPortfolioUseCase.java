package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Portfolio;

public interface GetPortfolioUseCase {
    Portfolio getPortfolio(long userId);
}
