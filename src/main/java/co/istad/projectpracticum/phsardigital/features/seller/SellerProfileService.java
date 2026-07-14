package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SellerProfileService {

    SellerProfileResponse getPublicProfile(String sellerId);

    Page<ListingResponse> getSellerListings(String sellerId, Pageable pageable);

    SellerProfileResponse getMyProfile();

    SellerProfileResponse updateMyProfile(SellerProfileUpdateRequest request);

}
