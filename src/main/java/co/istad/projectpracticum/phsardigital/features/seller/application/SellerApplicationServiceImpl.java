package co.istad.projectpracticum.phsardigital.features.seller.application;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.config.security.KeycloakAdminProps;
import co.istad.projectpracticum.phsardigital.features.auth.RoleEnum;
import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.file.FileVisibility;
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
import java.util.Set;
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
        app.setAddress(request.address());
        app.setCity(request.city());
        app.setProvince(request.province());
        app.setLatitude(request.latitude());
        app.setLongitude(request.longitude());
        app.setGoogleMapUrl(request.googleMapUrl());
        app.setLogoFile(resolveLogo(request.logoObjectName(), applicantId));
        app.setStatus(ApplicationStatus.PENDING);

        return toResponse(applicationRepository.save(app));
    }

    /**
     * Resolves the applicant's chosen logo, confirming they uploaded it so one
     * applicant cannot point at another's image — and then delete it out from under
     * them, since deleting a file detaches it from whatever references it.
     *
     * <p>Unlike the supporting documents this must be a <em>public</em> image: a logo
     * is rendered in an {@code <img>} tag on every shop page, and a presigned URL
     * expires. The mirror-image check in {@link #addDocument} refuses public files
     * for the same reason in reverse.
     */
    private FileUpload resolveLogo(String logoObjectName, String applicantId) {
        if (logoObjectName == null || logoObjectName.isBlank()) {
            return null;
        }
        FileUpload logo = fileUploadService.requireOwnedFile(logoObjectName, applicantId);
        if (logo.getVisibility() == FileVisibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload the shop logo through POST /api/v1/files/upload. "
                            + "Files uploaded as documents are not publicly readable and cannot be used as a logo.");
        }
        return logo;
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
        FileUpload file = fileUploadService.requireOwnedFile(request.objectName(), applicantId);

        // Identity documents are only protected while they live in the private
        // bucket. Accepting a file uploaded through the image endpoint would put a
        // scan of somebody's ID behind a permanent, anonymously readable URL.
        if (file.getVisibility() != FileVisibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Upload supporting documents through POST /api/v1/files/documents. "
                            + "Files uploaded as images are publicly readable and cannot be used here.");
        }

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

        // Verification is the point of this queue, so an application with no identity
        // document and no licence cannot be waved through — deliberately or by a
        // mis-click on the wrong row.
        List<SellerDocumentType> missing = missingDocuments(
                documentRepository.findByApplication_UuidOrderByUploadedAtAsc(app.getUuid()));
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot approve: the applicant has not supplied " + missing + ".");
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
            profile.setAddress(app.getAddress());
            profile.setCity(app.getCity());
            profile.setProvince(app.getProvince());
            profile.setLatitude(app.getLatitude());
            profile.setLongitude(app.getLongitude());
            profile.setGoogleMapUrl(app.getGoogleMapUrl());
            profile.setLogoFile(app.getLogoFile());
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
                a.getDescription(),
                a.getLogoFile() != null ? a.getLogoFile().getObjectName() : null,
                fileUploadService.getPreviewUrl(a.getLogoFile()),
                a.getAddress(), a.getCity(), a.getProvince(),
                a.getLatitude(), a.getLongitude(), a.getGoogleMapUrl(),
                a.getStatus(), a.getRejectionNote(), a.getReviewedAt(),
                documents.stream().map(this::toDocResponse).toList(),
                missingDocuments(documents),
                a.getCreatedAt()
        );
    }

    /**
     * Which of {@link SellerDocumentType#REQUIRED_FOR_APPROVAL} the applicant has not
     * attached yet. Computed from the documents already loaded for the response
     * rather than with its own query, so listing a page of applications does not add
     * one round trip per row.
     */
    private List<SellerDocumentType> missingDocuments(List<SellerApplicationDocument> documents) {
        Set<SellerDocumentType> supplied = documents.stream()
                .map(SellerApplicationDocument::getDocType)
                .collect(Collectors.toSet());

        return SellerDocumentType.REQUIRED_FOR_APPROVAL.stream()
                .filter(required -> !supplied.contains(required))
                .sorted()
                .toList();
    }

    /**
     * Signs a short-lived link rather than returning a permanent one: these are
     * identity documents, and the URL travels through referrer headers, browser
     * history and screenshots. Signing needs no database round trip, so this stays
     * cheap even when a whole page of applications is rendered.
     */
    private ApplicationDocumentResponse toDocResponse(SellerApplicationDocument doc) {
        return sellerApplicationMapper.toDocResponse(
                fileUploadService.getPrivateUrl(doc.getObjectName()), doc);
    }
}
