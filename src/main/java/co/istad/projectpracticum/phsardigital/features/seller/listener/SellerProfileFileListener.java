package co.istad.projectpracticum.phsardigital.features.seller.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplication;
import co.istad.projectpracticum.phsardigital.features.seller.application.SellerApplicationRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detaches shop logos and covers whose underlying file is being deleted, so removing a
 * file never trips the foreign key on {@code seller_profiles.logo_file_id},
 * {@code seller_profiles.cover_file_id} or {@code seller_applications.logo_file_id}.
 *
 * <p>Both tables are swept, not just the profile: an applicant can delete a logo
 * they uploaded while their application is still under review.
 */
@Component
@RequiredArgsConstructor
public class SellerProfileFileListener {

    private final SellerRepository sellerRepository;
    private final SellerApplicationRepository applicationRepository;

    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event) {
        List<SellerProfile> profiles = sellerRepository
                .findAllByLogoFile_ObjectName(event.objectName());
        for (SellerProfile profile : profiles) {
            profile.setLogoFile(null);
        }

        List<SellerProfile> covered = sellerRepository
                .findAllByCoverFile_ObjectName(event.objectName());
        for (SellerProfile profile : covered) {
            profile.setCoverFile(null);
        }

        List<SellerApplication> applications = applicationRepository
                .findAllByLogoFile_ObjectName(event.objectName());
        for (SellerApplication application : applications) {
            application.setLogoFile(null);
        }
    }
}
