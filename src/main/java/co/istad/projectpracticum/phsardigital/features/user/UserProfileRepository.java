package co.istad.projectpracticum.phsardigital.features.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    Page<UserProfile> findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
            String email,
            String fullName,
            Pageable pageable
    );
}
