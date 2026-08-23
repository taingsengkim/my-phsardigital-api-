package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.listings.ListingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite , UUID> {
    Page<Favorite> findByUserProfile(UserProfile userProfile, Pageable pageable);

    @EntityGraph(attributePaths = {
            "listing", "listing.category", "listing.sellerProfile", "listing.thumbnailFile"
    })
    @Query("SELECT f FROM Favorite f "
            + "WHERE f.userProfile = :userProfile "
            + "AND f.listing.status IN :statuses "
            + "AND f.listing.sellerProfile.isActive = true "
            + "AND f.listing.category.uuid IN :categoryUuids")
    Page<Favorite> findPublicByUserProfile(
            @Param("userProfile") UserProfile userProfile,
            @Param("statuses") Collection<ListingStatus> statuses,
            @Param("categoryUuids") Collection<UUID> categoryUuids,
            Pageable pageable);

    boolean existsByUserProfileAndListingUuid(UserProfile userProfile, UUID listingUuid);

    List<Favorite> findAllByUserProfileAndListingUuidIn(UserProfile userProfile, List<UUID> listingUuids);

    /**
     * Which of these listings the caller has already saved, in one query rather than one
     * per card — the same shape as the rating aggregate {@code ListingResponseFactory}
     * fetches beside it.
     */
    @Query("SELECT f.listing.uuid FROM Favorite f "
            + "WHERE f.userProfile.id = :userId AND f.listing.uuid IN :listingUuids")
    List<UUID> findFavouritedUuids(@Param("userId") String userId,
                                   @Param("listingUuids") Collection<UUID> listingUuids);


}
