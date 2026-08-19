package co.istad.projectpracticum.phsardigital.features.seller;


import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SellerProfileUpdateRequest;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring")
public abstract class SellerProfileMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    /**
     * Written out rather than generated because the logo is stored as a file
     * reference but answered as a URL, and the URL scheme depends on the file's
     * visibility — something only {@link FileUploadService} knows.
     *
     * <p>Leaves the rating null. This is the method MapStruct reaches for whenever a
     * shop block is embedded in something else — a review, a reply — and those are
     * answered a page at a time, so costing each one an aggregate query would turn a
     * page of ten reviews into eleven round trips. Null there means "not computed on
     * this route", not "no reviews"; the shop's own endpoints use
     * {@link #toResponseWithRating} and always fill it in.
     */
    public SellerProfileResponse toResponse(SellerProfile profile) {
        return toResponseWithRating(profile, null, null);
    }

    /**
     * The shop block embedded in a listing, a review or a reply. Written out for the
     * same reason as {@link #toResponse}: only {@link FileUploadService} knows which
     * URL scheme the logo needs.
     */
    public SellerProfileSummaryResponse toSummary(SellerProfile profile) {
        if (profile == null) {
            return null;
        }
        return new SellerProfileSummaryResponse(
                profile.getSellerId(),
                profile.getBusinessName(),
                fileUploadService.getPreviewUrl(profile.getLogoFile()),
                profile.getPhoneNumber(),
                profile.getBiography(),
                profile.getSocialLink()
        );
    }

    /**
     * The same shop, with its star rating attached. Still the public view: a shopper
     * learns that a shop is inactive, never what it was accused of.
     *
     * @param averageRating raw average from the database, rounded here so every
     *                      caller answers the same 4.3 rather than one of them
     *                      answering 4.333333333333333
     * @param reviewCount   how many reviews back that average
     */
    public SellerProfileResponse toResponseWithRating(SellerProfile profile,
                                                      Double averageRating,
                                                      Long reviewCount) {
        return build(profile, averageRating, reviewCount, false);
    }

    /**
     * The shop as its own seller sees it — the public view plus why it was suspended.
     *
     * <p>A separate method rather than a flag, so disclosing a moderation decision is
     * something a caller asks for by name: a route that forgets to choose gets the
     * public view.
     */
    public SellerProfileResponse toOwnerResponse(SellerProfile profile,
                                                 Double averageRating,
                                                 Long reviewCount) {
        return build(profile, averageRating, reviewCount, true);
    }

    private SellerProfileResponse build(SellerProfile profile,
                                        Double averageRating,
                                        Long reviewCount,
                                        boolean discloseSuspension) {
        return new SellerProfileResponse(
                profile.getSellerId(),
                profile.getBusinessName(),
                profile.getBusinessType(),
                profile.getDescription(),
                profile.getLogoFile() != null ? profile.getLogoFile().getObjectName() : null,
                fileUploadService.getPreviewUrl(profile.getLogoFile()),
                profile.getCoverFile() != null ? profile.getCoverFile().getObjectName() : null,
                fileUploadService.getPreviewUrl(profile.getCoverFile()),
                profile.getAddress(),
                profile.getCity(),
                profile.getProvince(),
                profile.getLatitude(),
                profile.getLongitude(),
                profile.getGoogleMapUrl(),
                profile.getIsActive(),
                profile.getPhoneNumber(),
                profile.getBiography(),
                profile.getSocialLink(),
                round(averageRating),
                reviewCount,
                discloseSuspension ? profile.getSuspensionReason() : null,
                discloseSuspension ? profile.getSuspendedAt() : null
        );
    }

    private static Double round(Double averageRating) {
        if (averageRating == null) {
            return null;
        }
        return BigDecimal.valueOf(averageRating)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * The logo and cover are deliberately not mapped here: swapping either deletes the
     * object behind the previous one, which has to happen after the row is flushed. The
     * service owns that sequence.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "logoFile", ignore = true)
    @Mapping(target = "coverFile", ignore = true)
    public abstract void updateFromRequest(SellerProfileUpdateRequest request,
                                           @MappingTarget SellerProfile sellerProfile);
}
