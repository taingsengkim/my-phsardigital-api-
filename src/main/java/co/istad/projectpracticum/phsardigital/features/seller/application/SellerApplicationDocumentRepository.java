package co.istad.projectpracticum.phsardigital.features.seller.application;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SellerApplicationDocumentRepository extends JpaRepository<SellerApplicationDocument, UUID> {
}