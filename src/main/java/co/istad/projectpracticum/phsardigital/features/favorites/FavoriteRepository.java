package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite , UUID> {
    Page<Favorite> findByUserProfile(UserProfile userProfile, Pageable pageable);

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
