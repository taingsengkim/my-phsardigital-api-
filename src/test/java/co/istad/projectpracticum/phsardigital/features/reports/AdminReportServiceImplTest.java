package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminReportServiceImplTest {

    private static final String ADMIN_ID = "admin-1";

    @Mock
    private ContentReportRepository reportRepository;
    @InjectMocks
    private AdminReportServiceImpl service;

    @Test
    void resolvingRecordsWhoDecidedAndWhen() {
        UUID uuid = UUID.randomUUID();
        ContentReport open = open(uuid);
        when(reportRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(open));
        when(reportRepository.save(open)).thenReturn(open);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var closed = service.resolve(uuid, new ReportDecisionRequest("Listing suspended."));

            assertThat(closed.status()).isEqualTo(ReportStatus.RESOLVED);
            assertThat(closed.reviewedBy()).isEqualTo(ADMIN_ID);
            assertThat(closed.reviewedAt()).isNotNull();
            assertThat(closed.decisionNote()).isEqualTo("Listing suspended.");
        }
    }

    @Test
    void dismissingClosesItWithoutResolving() {
        UUID uuid = UUID.randomUUID();
        ContentReport open = open(uuid);
        when(reportRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(open));
        when(reportRepository.save(open)).thenReturn(open);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            assertThat(service.dismiss(uuid, new ReportDecisionRequest(null)).status())
                    .isEqualTo(ReportStatus.DISMISSED);
        }
    }

    /**
     * The accusation is not overwritten by the answer to it — an admin reading the row
     * later needs both.
     */
    @Test
    void closingLeavesTheReportersOwnWordsAlone() {
        UUID uuid = UUID.randomUUID();
        ContentReport open = open(uuid);
        open.setNote("Sold me a brick in a box.");
        when(reportRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(open));
        when(reportRepository.save(open)).thenReturn(open);

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            var closed = service.resolve(uuid, new ReportDecisionRequest("Shop suspended."));

            assertThat(closed.note()).isEqualTo("Sold me a brick in a box.");
            assertThat(closed.decisionNote()).isEqualTo("Shop suspended.");
        }
    }

    /** Two admins on the same queue must not silently replace each other's reasoning. */
    @Test
    void closingAnAlreadyClosedReportConflicts() {
        UUID uuid = UUID.randomUUID();
        ContentReport closed = open(uuid);
        closed.setStatus(ReportStatus.DISMISSED);
        closed.setReviewedBy("admin-2");
        closed.setReviewedAt(LocalDateTime.now());
        when(reportRepository.findByUuidForUpdate(uuid)).thenReturn(Optional.of(closed));

        try (MockedStatic<AuthUtils> auth = authenticatedAdmin()) {
            assertThatThrownBy(() -> service.resolve(uuid, new ReportDecisionRequest(null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT));
        }
        verify(reportRepository, never()).save(any());
    }

    private static MockedStatic<AuthUtils> authenticatedAdmin() {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(ADMIN_ID);
        return auth;
    }

    private static ContentReport open(UUID uuid) {
        ContentReport report = new ContentReport();
        report.setUuid(uuid);
        report.setReporterId("buyer-1");
        report.setTargetType(ReportTargetType.LISTING);
        report.setTargetId(UUID.randomUUID().toString());
        report.setTargetLabel("Fake AirPods");
        report.setReason(ReportReason.COUNTERFEIT);
        report.setStatus(ReportStatus.OPEN);
        return report;
    }
}
