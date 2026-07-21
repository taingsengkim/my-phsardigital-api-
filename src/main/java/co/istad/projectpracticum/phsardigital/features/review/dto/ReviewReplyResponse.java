package co.istad.projectpracticum.phsardigital.features.review.dto;

import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewReplyResponse(
        UUID uuid,
        String comment,
        SellerProfileResponse seller,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID parentReplyUuid,
        List<ReviewReplyResponse> childReplies
) {
}
