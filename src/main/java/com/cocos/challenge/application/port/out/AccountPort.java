package com.cocos.challenge.application.port.out;

import java.util.Optional;

public interface AccountPort {
    boolean exists(long userId);
    Optional<String> accountNumber(long userId);
}
