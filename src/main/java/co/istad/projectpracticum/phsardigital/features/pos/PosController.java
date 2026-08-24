package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pos")
@RequiredArgsConstructor
public class PosController {

    private final PosService posService;

    /** Rings up a sale at the calling seller's own counter. */
    @PostMapping("/sales")
    @ResponseStatus(HttpStatus.CREATED)
    public PosSaleResponse sell(@Valid @RequestBody PosSaleRequest request) {
        return posService.sell(request);
    }
}
