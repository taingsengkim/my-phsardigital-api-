package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.payments.dto.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /** The caller's own payments, newest first. */
    @GetMapping
    public Page<PaymentResponse> myPayments(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
        return paymentService.findMine(pageNumber, pageSize);
    }

    /** One payment as this server currently understands it; asks Bakong nothing. */
    @GetMapping("/{uuid}")
    public PaymentResponse findOne(@PathVariable UUID uuid) {
        return paymentService.findMine(uuid);
    }

    /**
     * Asks Bakong whether this payment arrived, and activates what it bought if so.
     *
     * <p>This is what a client polls while the QR is on screen — every few seconds is
     * reasonable — and it is safe to call as often as it likes: settling happens once
     * however many times it is asked. A POST rather than a GET because a successful
     * check does change things: it is what turns a payment into an active subscription.
     */
    @PostMapping("/{uuid}/verify")
    public PaymentResponse verify(@PathVariable UUID uuid) {
        return paymentService.verify(uuid);
    }
}
