package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.features.seller.application.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/seller-applications")
@RequiredArgsConstructor
public class SellerApplicationController {

    private final SellerApplicationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SellerApplicationResponse apply(@Valid @RequestBody SellerApplicationRequest request) {
        return service.apply(request);
    }

    @GetMapping("/me")
    public SellerApplicationResponse myApplication() {
        return service.getMyApplication();
    }

    @PostMapping("/me/documents")
    public ApplicationDocumentResponse addDocument(@Valid @RequestBody AddDocumentRequest request) {
        return service.addDocument(request);
    }
}