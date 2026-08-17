package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class SellerProfileMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    /**
     * Written out rather than generated because the logo is stored as a file
     * reference but answered as a URL, and the URL scheme depends on the file's
     * visibility — something only {@link FileUploadService} knows.
     */
    public SellerProfileResponse toResponse(SellerProfile profile) {
        return new SellerProfileResponse(
                profile.getSellerId(),
                profile.getBusinessName(),
                profile.getBusinessType(),
                profile.getDescription(),
                profile.getLogoFile() != null ? profile.getLogoFile().getObjectName() : null,
                fileUploadService.getPreviewUrl(profile.getLogoFile()),
                profile.getAddress(),
                profile.getCity(),
                profile.getProvince(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getGoogleMapUrl(),
                profile.getIsActive()
        );
    }

    /**
     * The logo is deliberately not mapped here: swapping it deletes the object
     * behind the previous one, which has to happen after the row is flushed. The
     * service owns that sequence.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "logoFile", ignore = true)
    public abstract void updateFromRequest(SellerProfileUpdateRequest request,
                                           @MappingTarget SellerProfile sellerProfile);
}
