package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review , UUID> {
    Page<Review> findByListing(Listing listing, Pageable pageable);

    Page<Review> findByBuyer(UserProfile buyer, Pageable pageable);

    Page<Review> findBySeller(SellerProfile seller, Pageable pageable);

    boolean existsByListingAndBuyer(Listing listing, UserProfile buyer);

    Optional<Review> findByUuidAndBuyer(UUID uuid, UserProfile buyer);

}
