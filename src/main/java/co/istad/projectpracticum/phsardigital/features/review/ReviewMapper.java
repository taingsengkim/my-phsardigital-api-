package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Delegates the embedded shop block to {@link SellerProfileMapper} instead of
 * letting MapStruct derive its own. The derived version could not fill {@code id}
 * (it comes from {@code sellerId}) or the logo URL (it needs the file service), so
 * every review answered a shop with a null id and no logo.
 */
@Mapper(componentModel = "spring", uses = SellerProfileMapper.class)
public interface ReviewMapper {

    @Mapping(target = "replies", source = "replies")
    ReviewResponse toResponse(Review review);

}