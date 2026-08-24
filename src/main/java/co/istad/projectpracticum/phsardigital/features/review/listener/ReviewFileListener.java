package co.istad.projectpracticum.phsardigital.features.review.listener;

import co.istad.projectpracticum.phsardigital.core.event.FileDeletedEvent;
import co.istad.projectpracticum.phsardigital.features.review.Review;
import co.istad.projectpracticum.phsardigital.features.review.ReviewRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detaches review photos whose underlying file is being deleted, so removing a file
 * never trips the foreign key on {@code reviews.photo_file_id}. The review itself stays.
 */
@Component
@RequiredArgsConstructor
public class ReviewFileListener {

    private final ReviewRepository reviewRepository;

    @EventListener
    @Transactional
    public void handle(FileDeletedEvent event) {
        List<Review> reviews = reviewRepository.findAllByPhoto_ObjectName(event.objectName());
        for (Review review : reviews) {
            review.setPhoto(null);
        }
    }
}
