package co.istad.projectpracticum.phsardigital.features.review.review_reply;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReviewReplyRepository extends JpaRepository<ReviewReply, UUID> {
    List<ReviewReply> findByReview_UuidOrderByCreatedAtAsc(UUID reviewUuid);
}
