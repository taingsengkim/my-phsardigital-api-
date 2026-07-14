package co.istad.projectpracticum.phsardigital.features.purchases;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    Page<Purchase> findByBuyerId(String buyerId, Pageable pageable);

    Optional<Purchase> findByUuidAndBuyerId(UUID uuid, String buyerId);

    Page<Purchase> findBySellerProfile_SellerId(String sellerId, Pageable pageable);
}