package co.istad.projectpracticum.phsardigital.features.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserProfile_IdOrderByIsDefaultDescCreatedAtDesc(String userId);

    Optional<Address> findByIdAndUserProfile_Id(UUID id, String userId);

    Optional<Address> findByUserProfile_IdAndIsDefaultTrue(String userId);

    long countByUserProfile_Id(String userId);

    /**
     * Clears the default flag on a user's other addresses. Done as one statement
     * rather than by loading and saving them, so promoting a new default cannot
     * briefly leave two rows flagged.
     */
    @Modifying
    @Query("UPDATE Address a SET a.isDefault = false "
            + "WHERE a.userProfile.id = :userId AND a.id <> :keepId AND a.isDefault = true")
    void clearOtherDefaults(@Param("userId") String userId, @Param("keepId") UUID keepId);

    /** Backs the landmark-photo detach in {@code AddressFileListener}. */
    List<Address> findAllByPhotos_File_ObjectName(String objectName);
}
