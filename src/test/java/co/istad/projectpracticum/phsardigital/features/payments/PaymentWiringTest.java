package co.istad.projectpracticum.phsardigital.features.payments;

import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongClient;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.BakongProps;
import co.istad.projectpracticum.phsardigital.features.payments.khqr.KhqrGenerator;
import co.istad.projectpracticum.phsardigital.features.seller.SellerAccessGuard;
import co.istad.projectpracticum.phsardigital.features.subscription.SellerSubscriptionRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionActivation;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPaymentSettlement;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionPlanRepository;
import co.istad.projectpracticum.phsardigital.features.subscription.SubscriptionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Proves the beans this feature added actually wire together.
 *
 * <p>Worth its own test because the failure it guards against is a startup failure, and
 * the one integration test that would catch it — {@code PhsardigitalApplicationTests} —
 * needs a live database and so does not run on a developer machine without one. A
 * dependency cycle or a missing {@code RestClient.Builder} would therefore first show up
 * on the production VPS, during a deploy.
 *
 * <p>The cycle is the real risk. Subscribing starts a payment and settling a payment
 * activates a subscription, so {@code payments} and {@code subscription} each reach into
 * the other; {@link SubscriptionActivation} exists to break that, and this is what says
 * so out loud.
 */
class PaymentWiringTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            // Deliberately nothing that supplies a RestClient.Builder: this application
            // has no spring-boot-restclient on its classpath, so a BakongClient that
            // expected one would pass a test that quietly provided it and then fail on
            // the production VPS. It builds its own instead.
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(
                    BakongProps.class,
                    BakongClient.class,
                    KhqrGenerator.class,
                    PaymentSettler.class,
                    PaymentServiceImpl.class,
                    PaymentExpirySweeper.class,
                    SubscriptionActivation.class,
                    SubscriptionPaymentSettlement.class,
                    SubscriptionServiceImpl.class,
                    co.istad.projectpracticum.phsardigital.features.pos.PosPaymentSettlement.class)
            // The repositories and the access guard belong to features this one only
            // borrows; standing them up would drag in JPA and prove nothing extra.
            .withBean(PaymentRepository.class, () -> mock(PaymentRepository.class))
            .withBean(SellerSubscriptionRepository.class, () -> mock(SellerSubscriptionRepository.class))
            .withBean(SubscriptionPlanRepository.class, () -> mock(SubscriptionPlanRepository.class))
            .withBean(ListingRepository.class, () -> mock(ListingRepository.class))
            .withBean(SellerAccessGuard.class, () -> mock(SellerAccessGuard.class))
            .withBean(co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository.class,
                    () -> mock(co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository.class))
            .withBean(co.istad.projectpracticum.phsardigital.features.stock.StockLedger.class,
                    () -> mock(co.istad.projectpracticum.phsardigital.features.stock.StockLedger.class));

    @Test
    void paymentsAndSubscriptionsWireUpWithoutACycle() {
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded).hasSingleBean(PaymentServiceImpl.class);
            assertThat(loaded).hasSingleBean(PaymentSettler.class);
            assertThat(loaded).hasSingleBean(SubscriptionServiceImpl.class);
            // The settlement handler has to be discoverable by the settler, or a paid
            // subscription would be charged for and never granted.
            assertThat(loaded.getBean(PaymentSettler.class)
                    .handles(PaymentPurpose.SUBSCRIPTION)).isTrue();
        });
    }

    @Test
    void everyPaymentPurposeHasSomethingThatCanFulfilIt() {
        // Adding a purpose without its handler would let start() take money for
        // something nothing grants. It refuses at runtime; this catches it at build.
        context.run(loaded -> {
            PaymentSettler settler = loaded.getBean(PaymentSettler.class);
            for (PaymentPurpose purpose : PaymentPurpose.values()) {
                assertThat(settler.handles(purpose))
                        .as("no PaymentSettlement bean handles %s", purpose)
                        .isTrue();
            }
        });
    }

    @Test
    void aServerWithNoTokenStartsButReportsItselfUnconfigured() {
        // The state of a fresh checkout and of CI. It must boot: refusing to start
        // would take the whole API down over a feature most of it does not use.
        context.run(loaded -> {
            assertThat(loaded).hasNotFailed();
            assertThat(loaded.getBean(BakongProps.class).isConfigured()).isFalse();
        });
    }

    @Test
    void theConfiguredMerchantIdentityBindsFromProperties() {
        context.withPropertyValues(
                        "bakong.api-token=test-token",
                        "bakong.account-id=ratanak_thai@bkrt",
                        "bakong.merchant-name=Anajak Store",
                        "bakong.merchant-city=PHNOM PENH",
                        "bakong.merchant-id=1516169",
                        "bakong.acquiring-bank=ACLEDA Bank Plc",
                        "bakong.qr-validity=10m")
                .run(loaded -> {
                    assertThat(loaded).hasNotFailed();
                    BakongProps props = loaded.getBean(BakongProps.class);
                    assertThat(props.isConfigured()).isTrue();
                    assertThat(props.getQrValidity()).isEqualTo(Duration.ofMinutes(10));
                });
    }

    @Test
    void aMerchantNameTooLongForKhqrStopsTheServerStarting() {
        // 26 characters. KHQR allows 25, and a QR built on an over-long name is refused
        // by the banking app rather than by us — far too late to find out.
        context.withPropertyValues(
                        "bakong.api-token=test-token",
                        "bakong.account-id=ratanak_thai@bkrt",
                        "bakong.merchant-name=Anajak Store Phnom Penh Kh",
                        "bakong.merchant-id=1516169",
                        "bakong.acquiring-bank=ACLEDA Bank Plc")
                .run(loaded -> assertThat(loaded).hasFailed());
    }

    @Test
    void aMalformedBakongAccountIdStopsTheServerStarting() {
        context.withPropertyValues(
                        "bakong.api-token=test-token",
                        "bakong.account-id=no-at-sign-here",
                        "bakong.merchant-name=Anajak Store",
                        "bakong.merchant-id=1516169",
                        "bakong.acquiring-bank=ACLEDA Bank Plc")
                .run(loaded -> assertThat(loaded).hasFailed());
    }
}
