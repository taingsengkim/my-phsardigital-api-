package co.istad.projectpracticum.phsardigital.features.review.dto;

import co.istad.projectpracticum.phsardigital.features.file.dto.FileUploadResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID uuid,
        ListingResponse listing,
        UserProfile buyer,
        SellerProfileResponse seller,
        Integer rating,
        String comment,
        FileUploadResponse photo,
        Boolean isEdited,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<ReviewReplyResponse> replies // nested replies
) {
}
