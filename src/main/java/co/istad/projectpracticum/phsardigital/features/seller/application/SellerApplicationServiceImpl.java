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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

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

    @Override
    public SellerApplicationResponse getMyApplication() {
        String applicantId = AuthUtils.extractUserId();
        SellerApplication app = applicationRepository
                .findByApplicantIdAndStatus(applicantId, ApplicationStatus.PENDING)
                .orElseThrow(() -> new ResponseStatusException(  HttpStatus.NOT_FOUND, "No pending application found."));
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

        SellerApplicationDocument doc = new SellerApplicationDocument();
        doc.setApplication(app);
        doc.setDocType(request.docType());
        doc.setObjectName(request.objectName());
        documentRepository.save(doc);
        String uri = fileUploadService.getPreviewUrl(doc.getObjectName());

        return sellerApplicationMapper.toDocResponse(uri,doc);
    }

    // admin
    @Override
    public Page<SellerApplicationResponse> list(ApplicationStatus status, int pageNumber, int pageSize) {
        return applicationRepository
                .findByStatus(status, PageRequest.of(pageNumber, pageSize))
                .map(this::toResponse);
    }

    @Override
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
        try {
            RoleRepresentation sellerRole = keycloak.realm(props.getTargetRealm())
                    .roles().get(RoleEnum.SELLER.name()).toRepresentation();
            keycloak.realm(props.getTargetRealm())
                    .users().get(app.getApplicantId())
                    .roles().realmLevel().add(List.of(sellerRole));
        } catch (Exception e) {
            log.error("Failed to grant SELLER role", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Could not grant seller role.");
        }

        // 2. create the SellerProfile from the application
        SellerProfile profile = new SellerProfile(app.getApplicantId());
        profile.setBusinessName(app.getBusinessName());
        profile.setBusinessType(app.getBusinessType());
        profile.setIsActive(true);
        sellerRepository.save(profile);

        // 3. mark the application approved
        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(AuthUtils.extractUserId());
        app.setReviewedAt(LocalDateTime.now());
        return toResponse(applicationRepository.save(app));
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

    private SellerApplication getApplicationOr404(UUID uuid) {
        return applicationRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Application not found."));
    }
    private SellerApplicationResponse toResponse(SellerApplication a) {
        return new SellerApplicationResponse(
                a.getUuid(), a.getApplicantId(), a.getBusinessName(), a.getBusinessType(),
                a.getDescription(), a.getCity(), a.getProvince(), a.getStatus(),
                a.getRejectionNote(), a.getCreatedAt()
        );
    }
}