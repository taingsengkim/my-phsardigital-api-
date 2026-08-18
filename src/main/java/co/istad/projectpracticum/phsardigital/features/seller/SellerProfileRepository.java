package co.istad.projectpracticum.phsardigital.features.seller;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerProfileRepository extends JpaRepository<SellerProfile , String> {

    /** Shops that may actually trade — the SELLER role survives a suspension, {@code isActive} does not. */
    long countByIsActiveTrue();
}
