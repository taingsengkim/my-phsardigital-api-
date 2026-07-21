package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite , UUID> {
    Page<Favorite> findByUserProfile(UserProfile userProfile, Pageable pageable);

    boolean existsByUserProfileAndListingUuid(UserProfile userProfile, UUID listingUuid);

    List<Favorite> findAllByUserProfileAndListingUuidIn(UserProfile userProfile, List<UUID> listingUuids);


}
