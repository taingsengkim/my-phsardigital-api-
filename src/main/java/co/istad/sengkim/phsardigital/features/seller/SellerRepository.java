package co.istad.sengkim.phsardigital.features.seller;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SellerRepository extends JpaRepository<SellerProfile, String> {
}
