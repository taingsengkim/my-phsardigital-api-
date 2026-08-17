package co.istad.projectpracticum.phsardigital.features.seller.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    /** Backs the logo cleanup in {@code SellerProfileFileListener}. */
    List<SellerApplication> findAllByLogoFile_ObjectName(String objectName);

    Optional<SellerApplication> findByApplicantIdAndStatus(String applicantId, ApplicationStatus status);

    /**
     * The applicant's newest application whatever its status, so a rejected one —
     * and its rejection note — stays readable instead of vanishing behind a 404.
     */
    Optional<SellerApplication> findFirstByApplicantIdOrderByCreatedAtDesc(String applicantId);

    boolean existsByApplicantIdAndStatus(String applicantId, ApplicationStatus status);

    Page<SellerApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}
