package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse;

/**
 * The shop's own executive summary.
 *
 * <p>Distinct from {@code AdminDashboardService}, which answers the same shape of
 * question for the whole marketplace. This one is always scoped to the calling seller
 * and never takes a shop id, so there is no route by which one shop reads another's
 * takings.
 */
public interface SellerDashboardService {

    /** Every figure the calling shop's home dashboard draws, in one pass. */
    SellerDashboardOverviewResponse getMyOverview();
}
