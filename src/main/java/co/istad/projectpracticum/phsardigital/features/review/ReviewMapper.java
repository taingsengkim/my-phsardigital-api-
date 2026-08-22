package co.istad.projectpracticum.phsardigital.features.review;

import co.istad.projectpracticum.phsardigital.features.listings.listing_attributes.ListingAttributeMapper;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewAuthorResponse;
import co.istad.projectpracticum.phsardigital.features.review.dto.ReviewResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfileMapper;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Delegates the embedded shop block to {@link SellerProfileMapper} instead of
 * letting MapStruct derive its own. The derived version could not fill {@code id}
 * (it comes from {@code sellerId}) or the logo URL (it needs the file service), so
 * every review answered a shop with a null id and no logo.
 *
 * <p>{@link ListingAttributeMapper} is delegated to for the same reason: a spec's label,
 * unit and group are read off the category's definition of it, which a derived mapping
 * cannot reach — so the reviewed product's specs came back as bare key/value pairs.
 */
@Mapper(componentModel = "spring", uses = {SellerProfileMapper.class, ListingAttributeMapper.class})
public abstract class ReviewMapper {

    @Autowired
    protected UserProfileMapper userProfileMapper;

    @Mapping(target = "replies", source = "replies")
    public abstract ReviewResponse toResponse(Review review);

    /**
     * Narrows the reviewer down to what a review card shows. MapStruct picks this up
     * for the {@code buyer} property because it is the only {@code UserProfile ->
     * ReviewAuthorResponse} method in scope, which is the point: there is no path by
     * which the full profile — email, phone, date of birth — reaches a review
     * response, whether that response is read by a shop owner or by an anonymous
     * visitor to the shop page.
     */
    public ReviewAuthorResponse toAuthor(UserProfile buyer) {
        if (buyer == null) {
            return null;
        }
        String displayName = buyer.getFullName() != null && !buyer.getFullName().isBlank()
                ? buyer.getFullName()
                : buyer.getUsername();
        return new ReviewAuthorResponse(
                buyer.getId(),
                displayName,
                userProfileMapper.avatarUrl(buyer));
    }
}
