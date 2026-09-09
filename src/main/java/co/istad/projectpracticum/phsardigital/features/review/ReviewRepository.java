package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review , UUID> {

    /** Backs {@code ReviewFileReferences} and the detach in {@code ReviewFileListener}. */
    boolean existsByPhoto_ObjectName(String objectName);

    List<Review> findAllByPhoto_ObjectName(String objectName);
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

    /**
     * One product's star rating, on the same terms as {@link #averageRatingForSeller} —
     * computed on read, and null rather than zero when nothing has been reviewed.
     */
    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.uuid = :listingUuid")
    Double averageRatingForListing(@Param("listingUuid") UUID listingUuid);

    long countByListing_Uuid(UUID listingUuid);

    /**
     * How many reviews a shop has at each star, for the breakdown bars on a storefront.
     *
     * <p>Grouped in the database rather than counted from a page of reviews, which would
     * describe only the twenty on screen and quietly mislabel them as the whole picture.
     *
     * @return {@code [rating, count]} for each star anybody has given; a star nobody has
     *         given is absent rather than a zero row
     */
    @Query("SELECT r.rating, COUNT(r) FROM Review r "
            + "WHERE r.seller.sellerId = :sellerId GROUP BY r.rating")
    List<Object[]> ratingBreakdownForSeller(@Param("sellerId") String sellerId);

    /** One product's breakdown, on the same terms as {@link #ratingBreakdownForSeller}. */
    @Query("SELECT r.rating, COUNT(r) FROM Review r "
            + "WHERE r.listing.uuid = :listingUuid GROUP BY r.rating")
    List<Object[]> ratingBreakdownForListing(@Param("listingUuid") UUID listingUuid);

    /**
     * Ratings for a whole page of products in one query, since a grid shows stars on
     * every card and asking per card is an N+1.
     *
     * @return {@code [listingUuid, averageRating, reviewCount]}; unreviewed products
     *         are absent rather than present with zeroes
     */
    @Query("SELECT r.listing.uuid, AVG(r.rating), COUNT(r) FROM Review r "
            + "WHERE r.listing.uuid IN :listingUuids "
            + "GROUP BY r.listing.uuid")
    List<Object[]> ratingsForListings(@Param("listingUuids") Collection<UUID> listingUuids);

    /**
     * Shops ranked by average score.
     *
     * <p>{@code HAVING} is what keeps the board honest: without a floor, one shop with
     * a single five-star review outranks a shop with two hundred at 4.8.
     *
     * @return {@code [sellerId, averageRating, reviewCount]} per shop, best first
     */
    @Query("SELECT r.seller.sellerId, AVG(r.rating), COUNT(r) FROM Review r "
            + "WHERE r.createdAt >= :since AND r.seller.isActive = true "
            + "GROUP BY r.seller.sellerId "
            + "HAVING COUNT(r) >= :minReviews "
            + "ORDER BY AVG(r.rating) DESC")
    List<Object[]> rankSellersByRating(@Param("since") LocalDateTime since,
                                       @Param("minReviews") long minReviews,
                                       Pageable pageable);

    /**
     * Ratings for a known set of shops, to fill in a board ranked on something else.
     *
     * @return {@code [sellerId, averageRating, reviewCount]}; unreviewed shops absent
     */
    @Query("SELECT r.seller.sellerId, AVG(r.rating), COUNT(r) FROM Review r "
            + "WHERE r.createdAt >= :since AND r.seller.sellerId IN :sellerIds "
            + "GROUP BY r.seller.sellerId")
    List<Object[]> ratingsForSellers(@Param("since") LocalDateTime since,
                                     @Param("sellerIds") Collection<String> sellerIds);

    /**
     * The same, over a shop's whole history rather than a period.
     *
     * <p>Separate from the overload above rather than called with a date far in the past:
     * a list of shops near somebody is not a leaderboard, and a shop that has been well
     * reviewed for years should not read as unrated because the window happened to be
     * quiet.
     *
     * @return {@code [sellerId, averageRating, reviewCount]}; unreviewed shops absent
     */
    @Query("SELECT r.seller.sellerId, AVG(r.rating), COUNT(r) FROM Review r "
            + "WHERE r.seller.sellerId IN :sellerIds "
            + "GROUP BY r.seller.sellerId")
    List<Object[]> ratingsForSellers(@Param("sellerIds") Collection<String> sellerIds);

}
