package co.istad.sengkim.phsardigital.features.listings;
import co.istad.sengkim.phsardigital.features.categories.CategoryMapper;
import co.istad.sengkim.phsardigital.features.file.FileUploadService;
import co.istad.sengkim.phsardigital.features.listings.dto.ListingResponse;
import co.istad.sengkim.phsardigital.features.listings.listing_images.ListingImageMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {ListingImageMapper.class, CategoryMapper.class})
public abstract class ListingMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(target = "thumbnailUri", expression = "java(fileUploadService.getPreviewUrl(listing.getThumbnailObjectName()))")
    public abstract ListingResponse toResponse(Listing listing);
}