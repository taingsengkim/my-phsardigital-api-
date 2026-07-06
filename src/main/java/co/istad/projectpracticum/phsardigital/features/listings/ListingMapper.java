package co.istad.projectpracticum.phsardigital.features.listings;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryMapper;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.dto.UpdateListingRequest;
import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeMapper;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImageMapper;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ThumbnailImageResponse;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {
        ListingImageMapper.class,
        CategoryMapper.class ,
        ListingAttributeMapper.class
})
public abstract class ListingMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(target = "thumbnailUri", source = "listing")
    public abstract ListingResponse toResponse(Listing listing);




    protected ThumbnailImageResponse mapThumbnail(Listing listing) {
        if (listing.getThumbnailFile() == null) return null;

        return new ThumbnailImageResponse(
                listing.getThumbnailFile().getId().toString(),
                listing.getThumbnailFile().getObjectName(),
                fileUploadService.getPreviewUrl(listing.getThumbnailFile().getObjectName())
        );
    }
}