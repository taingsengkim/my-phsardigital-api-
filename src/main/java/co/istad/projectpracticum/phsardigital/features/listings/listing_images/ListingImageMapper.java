package co.istad.projectpracticum.phsardigital.features.listings.listing_images;

import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.listing_images.dto.ListingImageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class ListingImageMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(target = "uri", expression = "java(fileUploadService.getPreviewUrl(image.getObjectName()))")
    public abstract ListingImageResponse toResponse(ListingImage image);

    public abstract List<ListingImageResponse> toResponseList(List<ListingImage> images);
}