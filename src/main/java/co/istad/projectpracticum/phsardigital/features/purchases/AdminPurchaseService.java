package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseDetailResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseActivityResponse;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Read-only marketplace-wide order views, for administrators. */
public interface AdminPurchaseService {

    Page<AdminPurchaseRowResponse> list(AdminPurchaseFilter filter, int pageNumber, int pageSize,
                                        String sortBy, String sortOrder);

    /**
     * The KPI row and the status-tab badges.
     *
     * @param from inclusive; {@code to} exclusive. Both are resolved by the controller,
     *             which defaults them to month-to-date.
     */
    AdminPurchaseSummaryResponse summarise(LocalDateTime from, LocalDateTime to);

    AdminPurchaseDetailResponse get(UUID uuid);

    /** The order's lifecycle, oldest first. */
    List<PurchaseActivityResponse> activities(UUID uuid);

    /**
     * Every order matching the filter, as CSV.
     *
     * <p>Takes the same {@link AdminPurchaseFilter} as {@link #list} so an export always
     * describes the table it was taken from.
     */
    String exportCsv(AdminPurchaseFilter filter, String sortBy, String sortOrder);
}
