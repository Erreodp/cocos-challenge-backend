package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.port.in.SearchInstrumentsUseCase;
import com.cocos.challenge.domain.model.Instrument;
import java.math.BigDecimal;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Instrumentos", description = "Búsqueda de activos del mercado")
public class InstrumentRestController {
    private final SearchInstrumentsUseCase search;

    public InstrumentRestController(SearchInstrumentsUseCase search) {
        this.search = search;
    }

    @GetMapping("/api/instruments")
    @Operation(summary = "Buscar instrumentos", description = "Busca por ticker o nombre sin distinguir mayúsculas.")
    public List<InstrumentResponse> search(@RequestParam String query) {
        return search.search(query).stream().map(InstrumentResponse::from).toList();
    }

    public record InstrumentResponse(long id, String ticker, String name, String type,
                                     BigDecimal lastPrice, BigDecimal previousClose) {
        static InstrumentResponse from(Instrument i) {
            return new InstrumentResponse(i.id(), i.ticker(), i.name(), i.type(),
                    i.lastPrice(), i.previousClose());
        }
    }
}
