package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.seller.dto.AdminSellerResponse;
import co.istad.projectpracticum.phsardigital.features.seller.dto.SuspendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminSellerServiceImpl implements AdminSellerService {

    private final SellerProfileRepository sellerProfileRepository;

    @Override
    @Transactional
    public AdminSellerResponse suspend(String sellerId, SuspendRequest request) {
        SellerProfile profile = require(sellerId);

        profile.setIsActive(false);
        profile.setSuspendedBy(AuthUtils.extractUserId());
        profile.setSuspendedAt(LocalDateTime.now());
        profile.setSuspensionReason(request.reason());

        log.info("Shop {} suspended by {}", sellerId, profile.getSuspendedBy());
        return toResponse(sellerProfileRepository.save(profile));
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
        return toResponse(sellerProfileRepository.save(profile));
    }

    private SellerProfile require(String sellerId) {
        return sellerProfileRepository.findById(sellerId)
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
