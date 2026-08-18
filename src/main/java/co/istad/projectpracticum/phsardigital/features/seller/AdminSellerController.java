package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/sellers")
@RequiredArgsConstructor
public class AdminSellerController {

    private final AdminSellerService adminSellerService;

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
