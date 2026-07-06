package co.istad.projectpracticum.phsardigital.features.listings.listing_attributes;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ListingAttributeRepository extends JpaRepository<ListingAttribute , UUID> {
}
