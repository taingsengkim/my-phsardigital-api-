package co.istad.projectpracticum.phsardigital.features.listings;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryMapper;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeMapper;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingSpecificationAssembler;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.dto.ListingSpecificationGroupResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImageMapper;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ThumbnailImageResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", uses = {
        ListingImageMapper.class,
        CategoryMapper.class ,
        ListingAttributeMapper.class,
        // The embedded shop block: its logoUri needs FileUploadService, which MapStruct
        // cannot generate itself.
        SellerProfileMapper.class
})
public abstract class ListingMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    /**
     * Named apart from the field MapStruct generates for the mapper it {@code uses}, so
     * the two never shadow each other in the generated subclass.
     */
    @Autowired
    protected ListingSpecificationAssembler specificationAssembler;

    @Mapping(target = "thumbnailUri", source = "listing")
    @Mapping(target = "specifications", expression = "java(specifications(listing))")
    public abstract ListingResponse toResponse(Listing listing);

    /**
     * The spec table. Built off the same rows as {@code listingAttributes} rather than
     * from the flat DTOs, because the ordering lives on the category's definitions and is
     * gone by the time an attribute has been mapped.
     */
    protected List<ListingSpecificationGroupResponse> specifications(Listing listing) {
        return specificationAssembler.assemble(listing.getListingAttributes());
    }

    protected ThumbnailImageResponse mapThumbnail(Listing listing) {
        if (listing.getThumbnailFile() == null) return null;

        return new ThumbnailImageResponse(
                listing.getThumbnailFile().getId().toString(),
                listing.getThumbnailFile().getObjectName(),
                fileUploadService.getPreviewUrl(listing.getThumbnailFile())
        );
    }
}