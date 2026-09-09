package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseItemSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSellerResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPurchaseCsvWriterTest {

    private static final UUID ORDER = UUID.fromString("8f29d10e-0000-0000-0000-000000000001");

    @ParameterizedTest
    @DisplayName("neutralises a field a spreadsheet would run as a formula")
    @ValueSource(strings = {
            "=1+1",
            "+1234567890",
            "-2+3",
            "@SUM(A1:A9)",
            "=HYPERLINK(\"http://evil.example\",\"click\")"
    })
    void neutralisesFormulaInjection(String hostileName) {
        String row = AdminPurchaseCsvWriter.toRow(rowWithBuyerNamed(hostileName));

        // Quoted and prefixed, so Excel treats it as the text somebody typed.
        assertThat(row).contains("\"'" + hostileName.replace("\"", "\"\"") + "\"");
        assertThat(row).doesNotContain(",=");
        assertThat(row).doesNotContain(",@");
    }

    @Test
    @DisplayName("escapes quotes and keeps a comma inside its own field")
    void escapesQuotesAndCommas() {
        String row = AdminPurchaseCsvWriter.toRow(
                rowWithBuyerNamed("Sok \"Bunny\" Dara, Jr"));

        assertThat(row).contains("\"Sok \"\"Bunny\"\" Dara, Jr\"");
    }

    @Test
    @DisplayName("keeps a newline from breaking the row into two")
    void containsEmbeddedNewline() {
        String row = AdminPurchaseCsvWriter.toRow(rowWithBuyerNamed("Line one\nLine two"));

        // The newline survives inside quotes; the row still ends with exactly one CRLF.
        assertThat(row).contains("\"Line one\nLine two\"");
        assertThat(row).endsWith("\r\n");
        assertThat(row.split("\r\n")).hasSize(1);
    }

    @Test
    @DisplayName("says 'unconfirmed' rather than leaving the payment column blank")
    void unconfirmedPaymentIsNotBlank() {
        String row = AdminPurchaseCsvWriter.toRow(row(null, null));

        assertThat(row).contains("Unconfirmed");
    }

    @Test
    @DisplayName("labels a counter sale rather than leaving the buyer blank")
    void counterSaleIsLabelled() {
        String row = AdminPurchaseCsvWriter.toRow(row(null, PaymentMethod.CASH));

        assertThat(row).contains("Walk-in");
    }

    @Test
    @DisplayName("header names every column the row writes, trailing empties included")
    void headerMatchesRowWidth() {
        // Split with -1: an unfinished order ends in three empty timestamp fields, and
        // String.split discards trailing empties by default — which would hide exactly
        // the mismatch this test exists to catch.
        int headerColumns = AdminPurchaseCsvWriter.HEADER.strip().split(",", -1).length;
        int rowColumns = AdminPurchaseCsvWriter.toRow(rowWithBuyerNamed("Dara"))
                .strip().split(",", -1).length;

        assertThat(headerColumns).isEqualTo(16);
        assertThat(rowColumns).isEqualTo(headerColumns);
    }

    private static AdminPurchaseRowResponse rowWithBuyerNamed(String name) {
        return row(AdminPurchaseBuyerResponse.summary(
                "usr_1", name, "+855 12 345 678", "dara@example.com", null),
                PaymentMethod.KHQR);
    }

    private static AdminPurchaseRowResponse row(AdminPurchaseBuyerResponse buyer,
                                                PaymentMethod paymentMethod) {
        return new AdminPurchaseRowResponse(
                ORDER,
                LocalDateTime.of(2026, 9, 10, 8, 30),
                PurchaseStatus.CONFIRMED,
                PurchaseChannel.ONLINE,
                paymentMethod,
                new BigDecimal("145.50"),
                buyer,
                new AdminPurchaseSellerResponse("str_1", "Phsar Tech", "+855 10 987 654", null, true),
                new AdminPurchaseItemSummaryResponse(3, 5, "Keyboard", null, true),
                null, null, null);
    }
}
