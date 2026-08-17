package co.istad.projectpracticum.phsardigital.features.review.review_reply;

import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** See {@link co.istad.projectpracticum.phsardigital.features.review.ReviewMapper}. */
@Mapper(componentModel = "spring", uses = SellerProfileMapper.class)
public interface ReviewReplyMapper {

    @Mapping(target = "childReplies", source = "childReplies")
    ReviewReplyResponse toResponse(ReviewReply reply);
}