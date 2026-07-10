package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.features.seller.application.dto.*;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface SellerApplicationService {

    // applicant
    SellerApplicationResponse apply(SellerApplicationRequest request);
    SellerApplicationResponse getMyApplication();
    SellerApplicationResponse addDocument(AddDocumentRequest request);

    // admin
    Page<SellerApplicationResponse> list(ApplicationStatus status, int pageNumber, int pageSize);
    SellerApplicationResponse getOne(UUID uuid);
    SellerApplicationResponse approve(UUID uuid);
    SellerApplicationResponse reject(UUID uuid, RejectRequest request);
}