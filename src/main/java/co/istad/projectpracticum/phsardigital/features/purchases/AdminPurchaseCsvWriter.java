package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;

import java.util.List;

/**
 * Turns admin order rows into CSV.
 *
 * <p>Its own class because two of the rules below are easy to get wrong and impossible to
 * notice from a spot check: quoting, and the formula guard.
 */
final class AdminPurchaseCsvWriter {

    static final String HEADER = String.join(",",
            "Order ID", "Placed at", "Status", "Channel", "Payment method", "Total",
            "Buyer", "Buyer phone", "Buyer email", "Shop", "Shop phone",
            "Products", "Units", "Confirmed at", "Completed at", "Cancelled at") + "\r\n";

    private AdminPurchaseCsvWriter() {
    }

    static String toRow(AdminPurchaseRowResponse row) {
        var buyer = row.buyer();
        var seller = row.seller();
        var items = row.items();
        return String.join(",",
                cell(row.uuid()),
                cell(row.createdAt()),
                cell(row.status()),
                cell(row.channel()),
                // Blank would read as "not paid". Online orders settle in cash with a
                // courier nobody here hears from, so the truthful value is "unconfirmed".
                row.paymentMethod() == null ? "Unconfirmed" : cell(row.paymentMethod()),
                cell(row.totalPrice()),
                buyer == null ? "Walk-in" : cell(buyer.name()),
                buyer == null ? "" : cell(buyer.phone()),
                buyer == null ? "" : cell(buyer.email()),
                seller == null ? "" : cell(seller.businessName()),
                seller == null ? "" : cell(seller.phone()),
                items == null ? "0" : cell(items.lineCount()),
                items == null ? "0" : cell(items.unitCount()),
                cell(row.confirmedAt()),
                cell(row.completedAt()),
                cell(row.cancelledAt())) + "\r\n";
    }

    /**
     * One field, quoted and escaped.
     *
     * <p>The leading apostrophe on {@code = + - @} is a formula guard, not decoration.
     * Half of what goes into this file is typed by the public — a recipient's name, a
     * delivery note — and a spreadsheet opening a cell that starts with {@code =} runs it
     * as a formula. That is how a hostile order note becomes code executing on the
     * machine of the administrator who exported the report.
     */
    private static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }

    static String toCsv(List<AdminPurchaseRowResponse> rows) {
        StringBuilder csv = new StringBuilder(HEADER);
        rows.forEach(row -> csv.append(toRow(row)));
        return csv.toString();
    }
}
