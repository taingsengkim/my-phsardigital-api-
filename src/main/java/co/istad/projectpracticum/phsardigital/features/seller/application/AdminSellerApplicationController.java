package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.features.seller.application.dto.RejectRequest;
import co.istad.projectpracticum.phsardigital.features.seller.application.dto.SellerApplicationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/seller-applications")
@RequiredArgsConstructor
public class AdminSellerApplicationController {

    private final SellerApplicationService service;

    @GetMapping
    public Page<SellerApplicationResponse> list(
            @RequestParam(defaultValue = "PENDING") ApplicationStatus status,
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize) {
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