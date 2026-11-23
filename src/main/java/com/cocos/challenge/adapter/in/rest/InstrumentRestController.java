package com.cocos.challenge.adapter.in.rest;

import com.cocos.challenge.application.port.in.SearchInstrumentsUseCase;
import com.cocos.challenge.domain.model.Instrument;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@Tag(name = "Instrumentos", description = "Búsqueda de activos del mercado")
public class InstrumentRestController {
    private final SearchInstrumentsUseCase search;

    public InstrumentRestController(SearchInstrumentsUseCase search) {
        this.search = search;
    }

    @GetMapping("/api/instruments")
    @Operation(summary = "Buscar instrumentos", description = "Busca por ticker o nombre sin distinguir mayúsculas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instrumentos coincidentes; lista vacía si no hay resultados"),
            @ApiResponse(responseCode = "400", description = "Parámetro query ausente o vacío",
                    content = @Content(schema = @Schema(implementation = ApiExceptionHandler.ApiError.class)))
    })
    public List<InstrumentResponse> search(
            @Parameter(description = "Texto parcial a buscar en ticker o nombre", example = "GGAL", required = true)
            @RequestParam String query) {
        return search.search(query).stream().map(InstrumentResponse::from).toList();
    }

    @Schema(description = "Instrumento y última cotización disponible")
    public record InstrumentResponse(
            @Schema(description = "Identificador del instrumento", example = "47")
            long id,
            @Schema(description = "Ticker de mercado", example = "GGAL")
            String ticker,
            @Schema(description = "Nombre del instrumento", example = "Grupo Financiero Galicia")
            String name,
            @Schema(description = "Tipo del instrumento", example = "ACCIONES")
            String type,
            @Schema(description = "Último precio de cierre en ARS; null si no hay cotización", example = "925.85", nullable = true)
            BigDecimal lastPrice,
            @Schema(description = "Cierre anterior en ARS; null si no está disponible", example = "921.80", nullable = true)
            BigDecimal previousClose) {
        static InstrumentResponse from(Instrument i) {
            return new InstrumentResponse(i.id(), i.ticker(), i.name(), i.type(),
                    i.lastPrice(), i.previousClose());
        }
    }
}
