package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
@Validated
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

    /**
     * The admin shop list. Unlike the public {@code GET /api/v1/sellers/top} board,
     * this lists every shop whether or not it has ever sold anything.
     *
     * @param status filters by moderation state; omit it to list every shop
     * @param search case-insensitive substring of the business name
     */
    @GetMapping
    public Page<AdminSellerResponse> list(
            @RequestParam(required = false) AdminSellerStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return adminSellerService.list(status, search, pageNumber, pageSize);
    }

    @PatchMapping("/{sellerId}/suspend")
    public AdminSellerResponse suspend(@PathVariable String sellerId,
                                       @Valid @RequestBody SuspendRequest request) {
        return adminSellerService.suspend(sellerId, request);
    }

    @PatchMapping("/{sellerId}/restore")
    public AdminSellerResponse restore(@PathVariable String sellerId) {
        return adminSellerService.restore(sellerId);
    }
}
