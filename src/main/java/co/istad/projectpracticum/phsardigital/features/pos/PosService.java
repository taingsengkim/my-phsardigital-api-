package co.istad.projectpracticum.phsardigital.features.pos;

import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleRequest;
import co.istad.projectpracticum.phsardigital.features.pos.dto.PosSaleResponse;

public interface PosService {

    /**
     * Rings up a counter sale for the calling seller: takes the stock immediately and
     * records a COMPLETED order. Retrying with the same {@code saleUuid} returns the
     * sale that was already made rather than making a second one.
     */
    PosSaleResponse sell(PosSaleRequest request);
}
