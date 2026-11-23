package com.cocos.challenge.application.service;

import com.cocos.challenge.application.port.in.SearchInstrumentsUseCase;
import com.cocos.challenge.application.port.out.InstrumentCatalogPort;
import com.cocos.challenge.domain.model.Instrument;
import java.util.List;

public class InstrumentApplicationService implements SearchInstrumentsUseCase {
    private final InstrumentCatalogPort catalog;

    public InstrumentApplicationService(InstrumentCatalogPort catalog) {
        this.catalog = catalog;
    }

    @Override
    public List<Instrument> search(String query) {
        if (query == null || query.isBlank()) {
            throw new ApplicationException.BadRequest("query no puede estar vacío");
        }
        return catalog.search(query);
    }
}
