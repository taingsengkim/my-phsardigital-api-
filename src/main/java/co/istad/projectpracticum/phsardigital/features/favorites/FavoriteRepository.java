package co.istad.projectpracticum.phsardigital.features.favorites;

import co.istad.projectpracticum.phsardigital.features.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FavoriteRepository extends JpaRepository<Favorite , UUID> {
    Page<Favorite> findByUser(User user, Pageable pageable);

    boolean existsByUserAndListingUuid(User user, UUID listingUuid);

    List<Favorite> findAllByUserAndListingUuidIn(User user, List<UUID> listingUuids);


}
