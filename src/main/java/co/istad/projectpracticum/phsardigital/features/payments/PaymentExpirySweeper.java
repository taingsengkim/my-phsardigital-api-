package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongClient;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongTransaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Closes out payments whose QR has lapsed — and catches the ones that were paid after
 * everybody stopped watching.
 *
 * <p>Confirmation normally happens because a client is polling while the payer has the
 * QR on screen. That covers the common case and none of the awkward ones: a seller who
 * scans the code, pays, and closes the tab before the transfer clears is never polled
 * for again, and Bakong will not volunteer the news. Expiring such a payment on the
 * strength of the clock alone would take their money and give them nothing.
 *
 * <p>So nothing here is written off unasked. Every lapsed payment gets one final check
 * against Bakong; only a payment Bakong has no record of becomes {@code EXPIRED}. That
 * costs one call per abandoned checkout, once, which is a cheap price for not losing
 * people's money.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentExpirySweeper {

    private final PaymentRepository paymentRepository;
    private final PaymentSettler paymentSettler;
    private final BakongClient bakongClient;
    private final BakongProps props;

    @Scheduled(fixedDelayString = "${bakong.sweep-interval:5m}")
    public void sweep() {
        if (!props.isConfigured()) {
            return;
        }

        // The grace period sits between the QR's own expiry and our writing it off, to
        // cover the gap between a bank debiting the payer and Bakong reporting it.
        LocalDateTime cutoff = LocalDateTime.now().minus(props.getExpiryGrace());
        List<Payment> lapsed = paymentRepository.findLapsed(
                PaymentStatus.PENDING, cutoff, PageRequest.of(0, props.getSweepBatchSize()));
        if (lapsed.isEmpty()) {
            return;
        }

        int settled = 0;
        int expired = 0;
        for (Payment payment : lapsed) {
            try {
                BakongTransaction transaction = bakongClient.checkByMd5(payment.getMd5());
                if (transaction.settled()) {
                    paymentSettler.settle(payment.getUuid(), transaction);
                    settled++;
                    log.info("Payment {} was paid after its QR lapsed and has been honoured",
                            payment.getUuid());
                } else {
                    paymentSettler.expire(payment.getUuid());
                    expired++;
                }
            } catch (RuntimeException failure) {
                // Bakong being unreachable is not evidence that nobody paid, so the
                // payment stays PENDING and the next sweep asks again. Leaving it open
                // is the conservative side of this decision.
                log.warn("Could not reconcile payment {}; leaving it pending",
                        payment.getUuid(), failure);
            }
        }

        log.info("Payment sweep: {} examined, {} honoured late, {} expired",
                lapsed.size(), settled, expired);
    }
}
