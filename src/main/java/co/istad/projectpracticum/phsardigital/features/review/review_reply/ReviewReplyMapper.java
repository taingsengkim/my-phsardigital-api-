package co.istad.projectpracticum.phsardigital.features.review.review_reply;

import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewReplyResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewReplyMapper {

    @Mapping(target = "childReplies", source = "childReplies")
    ReviewReplyResponse toResponse(ReviewReply reply);
}