package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.features.seller.application.dto.RejectRequest;
import co.istad.projectpracticum.phsardigital.features.seller.application.dto.SellerApplicationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@RequiredArgsConstructor
@Validated
public class AdminSellerApplicationController {

    private final SellerApplicationService service;

    /**
     * @param status filters by review state; omit it to list every application.
     */
    @GetMapping
    public Page<SellerApplicationResponse> list(
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return service.list(status, pageNumber, pageSize);
    }

    @GetMapping("/{uuid}")
    public SellerApplicationResponse getOne(@PathVariable UUID uuid) {
        return service.getOne(uuid);
    }

    @PatchMapping("/{uuid}/approve")
    public SellerApplicationResponse approve(@PathVariable UUID uuid) {
        return service.approve(uuid);
    }

    @PatchMapping("/{uuid}/reject")
    public SellerApplicationResponse reject(@PathVariable UUID uuid,
                                            @Valid @RequestBody RejectRequest request) {
        return service.reject(uuid, request);
    }
}
