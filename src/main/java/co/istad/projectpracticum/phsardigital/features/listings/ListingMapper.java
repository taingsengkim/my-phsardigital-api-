package co.istad.projectpracticum.phsardigital.features.listings;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryMapper;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.dto.ListingResponse;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.ListingImageMapper;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", uses = {ListingImageMapper.class, CategoryMapper.class})
public abstract class ListingMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(target = "thumbnailUri", expression = "java(fileUploadService.getPreviewUrl(listing.getThumbnailObjectName()))")
    public abstract ListingResponse toResponse(Listing listing);
}