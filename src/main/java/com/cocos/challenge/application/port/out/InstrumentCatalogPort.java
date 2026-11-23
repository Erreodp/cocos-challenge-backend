package com.cocos.challenge.application.port.out;

import com.cocos.challenge.domain.model.Instrument;
import java.util.List;
import java.util.Optional;

public interface InstrumentCatalogPort {
    List<Instrument> search(String query);
    Optional<Instrument> findById(long instrumentId);
}
