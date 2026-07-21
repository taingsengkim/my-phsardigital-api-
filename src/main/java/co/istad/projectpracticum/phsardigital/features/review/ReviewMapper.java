package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewRequest;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "replies", source = "replies")
    ReviewResponse toResponse(Review review);

}