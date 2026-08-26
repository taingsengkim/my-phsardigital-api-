package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSellerServiceImpl implements AdminSellerService {

    private final SellerRepository sellerRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminSellerResponse> list(AdminSellerStatus status, String search,
                                          int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return sellerRepository.findAll(matching(status, search), pageable)
                .map(this::toResponse);
    }

    /**
     * Both filters are optional, so an empty conjunction — every shop — is the correct
     * result of asking for neither.
     */
    private static Specification<SellerProfile> matching(AdminSellerStatus status, String search) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null) {
                switch (status) {
                    case ACTIVE -> predicates.add(builder.isTrue(root.get("isActive")));
                    // Keyed off the recorded suspension, not the flag: an unapproved
                    // shop is also inactive and has never been moderated.
                    case SUSPENDED -> predicates.add(builder.isNotNull(root.get("suspendedAt")));
                    case INACTIVE -> {
                        // A legacy row can hold null rather than false, and null fails
                        // isFalse, which would silently drop it from the list.
                        predicates.add(builder.or(
                                builder.isFalse(root.get("isActive")),
                                builder.isNull(root.get("isActive"))));
                        predicates.add(builder.isNull(root.get("suspendedAt")));
                    }
                }
            }

            if (search != null && !search.isBlank()) {
                predicates.add(builder.like(
                        builder.lower(root.get("businessName")), likePattern(search), '\\'));
            }

            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Escaped so a shop name containing {@code %} or {@code _} is searched literally. */
    private static String likePattern(String search) {
        String escaped = search.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    @Override
    @Transactional
    public AdminSellerResponse suspend(String sellerId, SuspendRequest request) {
        SellerProfile profile = require(sellerId);

        profile.setIsActive(false);
        profile.setSuspendedBy(AuthUtils.extractUserId());
        profile.setSuspendedAt(LocalDateTime.now());
        profile.setSuspensionReason(request.reason());

        log.info("Shop {} suspended by {}", sellerId, profile.getSuspendedBy());
        return toResponse(sellerRepository.save(profile));
    }

    @Override
    @Transactional
    public AdminSellerResponse restore(String sellerId) {
        SellerProfile profile = require(sellerId);

        profile.setIsActive(true);
        // Cleared rather than kept, so a shop suspended twice does not answer with the
        // first reason still attached.
        profile.setSuspendedBy(null);
        profile.setSuspendedAt(null);
        profile.setSuspensionReason(null);

        log.info("Shop {} restored by {}", sellerId, AuthUtils.extractUserId());
        return toResponse(sellerRepository.save(profile));
    }

    private SellerProfile require(String sellerId) {
        return sellerRepository.findByIdForUpdate(sellerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found"));
    }

    private AdminSellerResponse toResponse(SellerProfile profile) {
        return new AdminSellerResponse(
                profile.getSellerId(),
                profile.getBusinessName(),
                profile.getIsActive(),
                profile.getSuspendedBy(),
                profile.getSuspendedAt(),
                profile.getSuspensionReason(),
                profile.getCreatedAt());
    }
}
