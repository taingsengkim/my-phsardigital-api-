package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.auth.RoleEnum;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.seller.SellerRepository;
import co.istad.projectpracticum.phsardigital.features.seller.application.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SellerApplicationServiceImpl implements SellerApplicationService {

    private final SellerApplicationRepository applicationRepository;
    private final SellerApplicationDocumentRepository documentRepository;
    private final SellerRepository sellerRepository;
    private final Keycloak keycloak;
    private final KeycloakAdminProps props;
    private final SellerApplicationMapper sellerApplicationMapper;
    private final FileUploadService fileUploadService;

    // applicant

    @Override
    @Transactional
    public SellerApplicationResponse apply(SellerApplicationRequest request) {
        String applicantId = AuthUtils.extractUserId();

        // block duplicate pending applications
        if (applicationRepository.existsByApplicantIdAndStatus(applicantId, ApplicationStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "You already have a pending application.");
        }
        // block if already an approved seller
        if (sellerRepository.existsById(applicantId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You are already a seller.");
        }

        SellerApplication app = new SellerApplication();
        app.setApplicantId(applicantId);
        app.setBusinessName(request.businessName());
        app.setBusinessType(request.businessType());
        app.setDescription(request.description());
        app.setCity(request.city());
        app.setProvince(request.province());
        app.setStatus(ApplicationStatus.PENDING);

        return toResponse(applicationRepository.save(app));
    }

    /**
     * Returns the applicant's most recent application whatever its status. Scoping
     * this to PENDING hid the rejection note from the only person it is written for.
     */
    @Override
    @Transactional(readOnly = true)
    public SellerApplicationResponse getMyApplication() {
        String applicantId = AuthUtils.extractUserId();
        SellerApplication app = applicationRepository
                .findFirstByApplicantIdOrderByCreatedAtDesc(applicantId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "You have not applied to become a seller yet."));
        return toResponse(app);
    }

    @Override
    @Transactional
    public ApplicationDocumentResponse addDocument(AddDocumentRequest request) {
        String applicantId = AuthUtils.extractUserId();
        SellerApplication app = applicationRepository
                .findByApplicantIdAndStatus(applicantId, ApplicationStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No pending application to attach documents to."));

        // Confirms the object exists and belongs to this applicant, so a client
        // cannot attach another applicant's ID card by guessing its object name.
        fileUploadService.requireOwnedFile(request.objectName(), applicantId);

        SellerApplicationDocument doc = new SellerApplicationDocument();
        doc.setApplication(app);
        doc.setDocType(request.docType());
        doc.setObjectName(request.objectName());
        documentRepository.save(doc);

        return toDocResponse(doc);
    }

    // admin

    @Override
    @Transactional(readOnly = true)
    public Page<SellerApplicationResponse> list(ApplicationStatus status, int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<SellerApplication> page = status == null
                ? applicationRepository.findAll(pageable)
                : applicationRepository.findByStatus(status, pageable);

        // One document query for the whole page instead of one per application.
        Map<UUID, List<SellerApplicationDocument>> documentsByApplication =
                documentsFor(page.getContent().stream().map(SellerApplication::getUuid).toList());

        return page.map(app -> toResponse(app,
                documentsByApplication.getOrDefault(app.getUuid(), List.of())));
    }

    @Override
    @Transactional(readOnly = true)
    public SellerApplicationResponse getOne(UUID uuid) {
        return toResponse(getApplicationOr404(uuid));
    }

    @Override
    @Transactional
    public SellerApplicationResponse approve(UUID uuid) {
        SellerApplication app = getApplicationOr404(uuid);
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not pending.");
        }

        // 1. grant SELLER realm role in Keycloak
        RoleRepresentation sellerRole;
        try {
            sellerRole = keycloak.realm(props.getTargetRealm())
                    .roles().get(RoleEnum.SELLER.name()).toRepresentation();
            keycloak.realm(props.getTargetRealm())
                    .users().get(app.getApplicantId())
                    .roles().realmLevel().add(List.of(sellerRole));
        } catch (Exception e) {
            log.error("Failed to grant SELLER role", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not grant seller role.");
        }

        try {
            // 2. create the SellerProfile from the application
            SellerProfile profile = new SellerProfile(app.getApplicantId());
            profile.setBusinessName(app.getBusinessName());
            profile.setBusinessType(app.getBusinessType());
            profile.setDescription(app.getDescription());
            profile.setCity(app.getCity());
            profile.setProvince(app.getProvince());
            profile.setIsActive(true);
            sellerRepository.save(profile);

            // 3. mark the application approved
            app.setStatus(ApplicationStatus.APPROVED);
            app.setReviewedBy(AuthUtils.extractUserId());
            app.setReviewedAt(LocalDateTime.now());
            return toResponse(applicationRepository.saveAndFlush(app));
        } catch (RuntimeException databaseFailure) {
            // Without this the rollback leaves a user holding SELLER in Keycloak
            // with no seller profile behind it.
            revokeSellerRole(app.getApplicantId(), sellerRole);
            throw databaseFailure;
        }
    }

    @Override
    @Transactional
    public SellerApplicationResponse reject(UUID uuid, RejectRequest request) {
        SellerApplication app = getApplicationOr404(uuid);
        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application is not pending.");
        }
        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectionNote(request.rejectionNote());
        app.setReviewedBy(AuthUtils.extractUserId());
        app.setReviewedAt(LocalDateTime.now());
        return toResponse(applicationRepository.save(app));
    }

    private void revokeSellerRole(String applicantId, RoleRepresentation sellerRole) {
        try {
            keycloak.realm(props.getTargetRealm())
                    .users().get(applicantId)
                    .roles().realmLevel().remove(List.of(sellerRole));
        } catch (Exception compensationFailure) {
            log.error("Could not revoke SELLER role for {} after a failed approval",
                    applicantId, compensationFailure);
        }
    }

    private SellerApplication getApplicationOr404(UUID uuid) {
        return applicationRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found."));
    }

    private Map<UUID, List<SellerApplicationDocument>> documentsFor(List<UUID> applicationUuids) {
        if (applicationUuids.isEmpty()) {
            return Collections.emptyMap();
        }
        return documentRepository.findByApplication_UuidIn(applicationUuids).stream()
                .collect(Collectors.groupingBy(doc -> doc.getApplication().getUuid()));
    }

    private SellerApplicationResponse toResponse(SellerApplication a) {
        return toResponse(a, documentRepository.findByApplication_UuidOrderByUploadedAtAsc(a.getUuid()));
    }

    private SellerApplicationResponse toResponse(SellerApplication a,
                                                 List<SellerApplicationDocument> documents) {
        return new SellerApplicationResponse(
                a.getUuid(), a.getApplicantId(), a.getBusinessName(), a.getBusinessType(),
                a.getDescription(), a.getCity(), a.getProvince(), a.getStatus(),
                a.getRejectionNote(), a.getReviewedAt(),
                documents.stream().map(this::toDocResponse).toList(),
                a.getCreatedAt()
        );
    }

    private ApplicationDocumentResponse toDocResponse(SellerApplicationDocument doc) {
        return sellerApplicationMapper.toDocResponse(
                fileUploadService.getPreviewUrl(doc.getObjectName()), doc);
    }
}
