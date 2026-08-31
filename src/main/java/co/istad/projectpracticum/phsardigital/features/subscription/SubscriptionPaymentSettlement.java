package co.istad.projectpracticum.phsardigital.features.subscription;

import co.istad.projectpracticum.phsardigital.features.payments.Payment;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentPurpose;
import co.istad.projectpracticum.phsardigital.features.payments.PaymentSettlement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns a settled KHQR payment into an active subscription.
 *
 * <p>Runs inside the transaction that marks the payment paid, so the plan and the
 * receipt commit together: there is no window in which a seller has been charged but
 * has nothing, and none in which they have a plan nothing paid for.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionPaymentSettlement implements PaymentSettlement {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionActivation activation;

    @Override
    public PaymentPurpose purpose() {
        return PaymentPurpose.SUBSCRIPTION;
    }

    @Override
    public void settle(Payment payment) {
        // A retired plan is honoured: the seller paid for it while it was on offer, and
        // an admin retiring it in the meantime is not their problem. Only a plan that
        // has vanished from the catalogue entirely is a fault, and plans are never
        // deleted — so this throwing means the table was tampered with.
        SubscriptionPlan plan = planRepository.findById(payment.getReference())
                .orElseThrow(() -> {
                    log.error("Payment {} bought plan '{}', which is no longer in the "
                                    + "catalogue; the seller has been charged.",
                            payment.getUuid(), payment.getReference());
                    return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                            "The plan this payment bought no longer exists. Contact "
                                    + "support quoting " + payment.getUuid() + ".");
                });

        activation.activate(payment.getPayerId(), plan);
    }
}
