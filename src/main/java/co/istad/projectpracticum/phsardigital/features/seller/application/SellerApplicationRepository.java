package co.istad.projectpracticum.phsardigital.features.seller.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SellerApplicationRepository extends JpaRepository<SellerApplication, UUID> {

    Optional<SellerApplication> findByApplicantIdAndStatus(String applicantId, ApplicationStatus status);

    boolean existsByApplicantIdAndStatus(String applicantId, ApplicationStatus status);

    Page<SellerApplication> findByStatus(ApplicationStatus status, Pageable pageable);
}