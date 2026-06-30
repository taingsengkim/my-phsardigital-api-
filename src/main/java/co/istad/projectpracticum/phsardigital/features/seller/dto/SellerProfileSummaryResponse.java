package co.istad.projectpracticum.phsardigital.features.seller.dto;

import jakarta.persistence.Id;

import java.util.List;

public record SellerProfileSummaryResponse(
        @Id
        String sellerId,
        String phoneNumber,
        String biography,
        List<String>socialLink
) {
}
