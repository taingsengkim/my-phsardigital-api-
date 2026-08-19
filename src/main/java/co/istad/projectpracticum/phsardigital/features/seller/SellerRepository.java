package co.istad.projectpracticum.phsardigital.features.seller;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SellerRepository extends JpaRepository<SellerProfile, String> {

    /** Both back the image cleanup in {@code SellerProfileFileListener}. */
    List<SellerProfile> findAllByLogoFile_ObjectName(String objectName);

    List<SellerProfile> findAllByCoverFile_ObjectName(String objectName);
}
