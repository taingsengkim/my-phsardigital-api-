package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportDetailResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportRowResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The investigation data added around a report: who filed it, what it names, and whether
 * this is the first time anybody has complained.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReportEnrichmentTest {

    private static final String REPORTER = "reporter-1";
    private static final UUID LISTING_UUID = UUID.randomUUID();
    private static final String SHOP_ID = "shop-1";

    @Mock private ContentReportRepository reportRepository;
    @Mock private ReportTargetHydrator targetHydrator;
    @Mock private UserProfileRepository userProfileRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private FileUploadService fileUploadService;

    private AdminReportServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminReportServiceImpl(reportRepository, targetHydrator,
                userProfileRepository, purchaseRepository, fileUploadService);

        when(targetHydrator.hydrate(any())).thenReturn(ReportTargetHydrator.Snapshot.empty());
        when(targetHydrator.detailsOf(any(), any())).thenReturn(null);
        when(userProfileRepository.findById(any())).thenReturn(Optional.empty());
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of());
        when(reportRepository.countByStatusForTarget(any(), any(), any())).thenReturn(List.of());
        when(reportRepository.countByStatusForTargets(anyCollection())).thenReturn(List.<Object[]>of());
        when(purchaseRepository.completedOrderIdsForBuyerAndListing(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(purchaseRepository.completedOrderIdsForBuyerAndSeller(any(), any(), any(), any()))
                .thenReturn(List.of());
        when(purchaseRepository.completedPurchasePairs(any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());
        when(purchaseRepository.completedShopPairs(any(), anyCollection(), anyCollection()))
                .thenReturn(List.of());
    }

    // -------------------------------------------------------------- detail view

    @Test
    @DisplayName("keeps every field the old response carried")
    void staysBackwardCompatible() {
        ContentReport report = listingReport();
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));

        AdminReportDetailResponse detail = service.findOne(report.getUuid());

        assertThat(detail.uuid()).isEqualTo(report.getUuid());
        assertThat(detail.reporterId()).isEqualTo(REPORTER);
        assertThat(detail.targetType()).isEqualTo(ReportTargetType.LISTING);
        assertThat(detail.targetId()).isEqualTo(LISTING_UUID.toString());
        assertThat(detail.targetLabel()).isEqualTo("Chanel perfume");
        assertThat(detail.reason()).isEqualTo(ReportReason.SCAM);
        assertThat(detail.status()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    @DisplayName("a deleted target answers null details rather than failing the request")
    void survivesADeletedTarget() {
        ContentReport report = listingReport();
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));

        AdminReportDetailResponse detail = service.findOne(report.getUuid());

        assertThat(detail.targetDetails()).isNull();
        assertThat(detail.targetLabel())
                .as("the snapshot is what keeps the report readable")
                .isEqualTo("Chanel perfume");
    }

    @Test
    @DisplayName("marks a reporter who completed an order for the reported product")
    void marksTheVerifiedBuyer() {
        ContentReport report = listingReport();
        UUID orderId = UUID.randomUUID();
        givenTargetIsLiveListing(report);
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));
        when(purchaseRepository.completedOrderIdsForBuyerAndListing(
                eq(REPORTER), eq(LISTING_UUID), eq(PurchaseStatus.COMPLETED), any()))
                .thenReturn(List.of(orderId));

        AdminReportDetailResponse detail = service.findOne(report.getUuid());

        assertThat(detail.reporter().isVerifiedBuyer()).isTrue();
        assertThat(detail.reporter().orderId()).isEqualTo(orderId.toString());
    }

    @Test
    @DisplayName("a reporter with no order is unverified, not accused of anything")
    void unverifiedReporterHasNoOrder() {
        ContentReport report = listingReport();
        givenTargetIsLiveListing(report);
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));

        AdminReportDetailResponse detail = service.findOne(report.getUuid());

        assertThat(detail.reporter().isVerifiedBuyer()).isFalse();
        assertThat(detail.reporter().orderId()).isNull();
    }

    @Test
    @DisplayName("the verified-buyer question does not apply to a reported review")
    void reviewReportsHaveNoBuyerMark() {
        ContentReport report = listingReport();
        report.setTargetType(ReportTargetType.REVIEW);
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));

        assertThat(service.findOne(report.getUuid()).reporter().isVerifiedBuyer()).isNull();
    }

    @Test
    @DisplayName("counts prior complaints, and separately the ones an admin upheld")
    void countsPriorReportsAndViolations() {
        ContentReport report = listingReport();
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));
        when(reportRepository.countByStatusForTarget(any(), any(), eq(report.getUuid())))
                .thenReturn(List.of(
                        new Object[]{ReportStatus.RESOLVED, 2L},
                        new Object[]{ReportStatus.DISMISSED, 3L},
                        new Object[]{ReportStatus.OPEN, 1L}));

        AdminReportDetailResponse detail = service.findOne(report.getUuid());

        assertThat(detail.targetPriorReportsCount()).isEqualTo(6);
        assertThat(detail.targetPriorViolationsCount())
                .as("dismissed complaints are not violations")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("signs a link for each piece of evidence, in the order it was attached")
    void answersEvidenceUrls() {
        ContentReport report = listingReport();
        report.getEvidence().add(evidence(report, "reports/a.jpg", 0));
        report.getEvidence().add(evidence(report, "reports/b.jpg", 1));
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));
        when(fileUploadService.getPreviewUrl(any(FileUpload.class)))
                .thenAnswer(call -> "signed:" + ((FileUpload) call.getArgument(0)).getObjectName());

        assertThat(service.findOne(report.getUuid()).evidenceUrls())
                .containsExactly("signed:reports/a.jpg", "signed:reports/b.jpg");
    }

    @Test
    @DisplayName("a report with no evidence answers an empty list, not null")
    void noEvidenceIsAnEmptyList() {
        ContentReport report = listingReport();
        when(reportRepository.findById(report.getUuid())).thenReturn(Optional.of(report));

        assertThat(service.findOne(report.getUuid()).evidenceUrls()).isEmpty();
    }

    // ---------------------------------------------------------------- the queue

    @Test
    @DisplayName("enriches a page in a fixed number of queries, not one per row")
    void enrichesThePageInBatches() {
        List<ContentReport> page = List.of(listingReport(), listingReport(), listingReport());
        when(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(page, Pageable.ofSize(20), page.size()));

        service.list(null, null, 0, 20);

        verify(targetHydrator, times(1)).hydrate(any());
        verify(userProfileRepository, times(1)).findAllById(anyIterable());
        verify(reportRepository, times(1)).countByStatusForTargets(anyCollection());
    }

    @Test
    @DisplayName("the queue reports prior complaints excluding the row itself")
    void queueExcludesTheRowFromItsOwnCount() {
        ContentReport report = listingReport();
        when(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report), Pageable.ofSize(20), 1));
        when(reportRepository.countByStatusForTargets(anyCollection())).thenReturn(List.<Object[]>of(
                new Object[]{ReportTargetType.LISTING, LISTING_UUID.toString(),
                        ReportStatus.OPEN, 4L}));

        AdminReportRowResponse row = service.list(null, null, 0, 20).getContent().getFirst();

        assertThat(row.targetPriorReportsCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("a target reported once shows no prior complaints rather than a negative count")
    void aLoneReportHasNoPriors() {
        ContentReport report = listingReport();
        when(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report), Pageable.ofSize(20), 1));
        when(reportRepository.countByStatusForTargets(anyCollection())).thenReturn(List.<Object[]>of(
                new Object[]{ReportTargetType.LISTING, LISTING_UUID.toString(),
                        ReportStatus.OPEN, 1L}));

        assertThat(service.list(null, null, 0, 20).getContent().getFirst()
                .targetPriorReportsCount()).isZero();
    }

    @Test
    @DisplayName("carries the thumbnail, price, shop and evidence count onto the row")
    void rowCarriesTriageFields() {
        ContentReport report = listingReport();
        report.getEvidence().add(evidence(report, "reports/a.jpg", 0));
        givenTargetIsLiveListing(report);
        when(reportRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report), Pageable.ofSize(20), 1));
        when(fileUploadService.getPreviewUrl(any(FileUpload.class))).thenReturn("https://thumb");

        UserProfile reporter = new UserProfile();
        ReflectionTestUtils.setField(reporter, "id", REPORTER);
        ReflectionTestUtils.setField(reporter, "fullName", "Sophea Meng");
        when(userProfileRepository.findAllById(anyIterable())).thenReturn(List.of(reporter));

        AdminReportRowResponse row = service.list(null, null, 0, 20).getContent().getFirst();

        assertThat(row.targetImageUrl()).isEqualTo("https://thumb");
        assertThat(row.targetPrice()).isEqualByComparingTo("1.50");
        assertThat(row.sellerName()).isEqualTo("Luxury Imports");
        assertThat(row.evidenceCount()).isEqualTo(1);
        assertThat(row.reporterName()).isEqualTo("Sophea Meng");
    }

    // ---------------------------------------------------------------- helpers

    /** Points the hydrator at a live listing for this report. */
    private void givenTargetIsLiveListing(ContentReport report) {
        SellerProfile shop = new SellerProfile(SHOP_ID);
        shop.setBusinessName("Luxury Imports");
        shop.setIsActive(true);

        Listing listing = new Listing();
        ReflectionTestUtils.setField(listing, "uuid", LISTING_UUID);
        ReflectionTestUtils.setField(listing, "title", "Chanel perfume");
        ReflectionTestUtils.setField(listing, "fullPrice", new BigDecimal("1.50"));
        ReflectionTestUtils.setField(listing, "thumbnailFile", new FileUpload());
        listing.setSellerProfile(shop);

        ReportTargetHydrator.Snapshot snapshot = org.mockito.Mockito
                .mock(ReportTargetHydrator.Snapshot.class);
        when(snapshot.listingOf(report)).thenReturn(listing);
        when(snapshot.shopBehind(report)).thenReturn(shop);
        when(targetHydrator.hydrate(any())).thenReturn(snapshot);
    }

    private static ContentReport listingReport() {
        ContentReport report = new ContentReport();
        report.setUuid(UUID.randomUUID());
        report.setReporterId(REPORTER);
        report.setTargetType(ReportTargetType.LISTING);
        report.setTargetId(LISTING_UUID.toString());
        report.setTargetLabel("Chanel perfume");
        report.setReason(ReportReason.SCAM);
        report.setStatus(ReportStatus.OPEN);
        return report;
    }

    private static ReportEvidence evidence(ContentReport report, String objectName, int order) {
        FileUpload file = new FileUpload();
        file.setObjectName(objectName);

        ReportEvidence attachment = new ReportEvidence();
        attachment.setReport(report);
        attachment.setFile(file);
        attachment.setSortOrder(order);
        return attachment;
    }
}
