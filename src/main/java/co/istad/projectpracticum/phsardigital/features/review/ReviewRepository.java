package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review , UUID> {
    Page<Review> findByListing(Listing listing, Pageable pageable);

    Page<Review> findByBuyer(UserProfile buyer, Pageable pageable);

    Page<Review> findBySeller(SellerProfile seller, Pageable pageable);

    boolean existsByListingAndBuyer(Listing listing, UserProfile buyer);

    Optional<Review> findByUuidAndBuyer(UUID uuid, UserProfile buyer);

    /**
     * The shop's star rating, averaged over every review on every one of its
     * listings.
     *
     * <p>Computed on read rather than kept as a column on {@code SellerProfile}: a
     * stored counter has to be corrected on edit and on delete as well as on create,
     * and the one already commented out in {@code ReviewServiceImpl} shows how
     * quickly that gets forgotten. The query is a single indexed aggregate.
     *
     * @return the average, or null when the shop has no reviews yet — {@code AVG}
     *         over no rows is null, not zero, and a shop with no reviews genuinely
     *         has no rating rather than a rating of nought
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.seller.sellerId = :sellerId")
    Double averageRatingForSeller(@Param("sellerId") String sellerId);

    long countBySeller_SellerId(String sellerId);

}
