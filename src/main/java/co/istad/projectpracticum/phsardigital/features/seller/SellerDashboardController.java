package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerDashboardOverviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The shop's home dashboard.
 *
 * <p>Always answers for the caller's own shop and takes no shop id, so there is no
 * route by which one seller reads another's takings — the reason this is not simply a
 * parameter on the public shop endpoints.
 */
@RestController
@RequestMapping("/api/v1/sellers/me")
@RequiredArgsConstructor
public class SellerDashboardController {

    private final SellerDashboardService sellerDashboardService;

    /**
     * Revenue, orders, inventory, a seven-day chart and best sellers in one request.
     *
     * <p>Replaces the client paging the order and listing lists to add them up itself,
     * which stopped being correct the moment a shop had more orders than a page holds.
     */
    @GetMapping("/dashboard")
    public SellerDashboardOverviewResponse dashboard() {
        return sellerDashboardService.getMyOverview();
    }
}
