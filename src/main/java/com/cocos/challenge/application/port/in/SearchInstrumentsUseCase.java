package com.cocos.challenge.application.port.in;

import com.cocos.challenge.domain.model.Instrument;
import java.util.List;

public interface SearchInstrumentsUseCase {
    List<Instrument> search(String query);
}
