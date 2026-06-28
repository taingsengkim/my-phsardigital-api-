package co.istad.sengkim.phsardigital.features.seller.dto;

import jakarta.persistence.Id;

import java.util.List;

public record SellerProfileSummaryResponse(
        @Id
        String userId,
        String phoneNumber,
        String biography,
        List<String>socialLink
) {
}
