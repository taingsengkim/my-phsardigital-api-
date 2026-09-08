package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportRequest;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReportServiceImplTest {

    private static final String REPORTER_ID = "buyer-1";
    private static final String SHOP_ID = "seller-1";
    private static final String LISTING_ID = "3f6d9d1e-1a3f-4c2b-9a52-9d5d2b6f0e11";

    @Mock
    private ContentReportRepository reportRepository;
    @Mock
    private ReportTargetResolver targetResolver;
    @InjectMocks
    private ReportServiceImpl service;

    @Test
    void filingSnapshotsWhatTheTargetWasCalled() {
        when(targetResolver.resolve(ReportTargetType.LISTING, LISTING_ID))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("Fake AirPods", SHOP_ID));
        when(reportRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = signedInAs(REPORTER_ID)) {
            var filed = service.file(new ReportRequest(ReportTargetType.LISTING, LISTING_ID,
                    ReportReason.COUNTERFEIT, "  These are not real.  "));

            assertThat(filed.status()).isEqualTo(ReportStatus.OPEN);
            assertThat(filed.targetLabel()).isEqualTo("Fake AirPods");
            assertThat(filed.reporterId()).isEqualTo(REPORTER_ID);
            // Trimmed on the way in, so the queue does not render padded text.
            assertThat(filed.note()).isEqualTo("These are not real.");
        }
    }

    /** Filing must not take anything down: that is an admin's decision, not a reporter's. */
    @Test
    void filingLeavesTheReportOpenAndTouchesNothingElse() {
        when(targetResolver.resolve(any(), anyString()))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("Ratanak Store", SHOP_ID));
        when(reportRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        try (MockedStatic<AuthUtils> auth = signedInAs(REPORTER_ID)) {
            var filed = service.file(new ReportRequest(ReportTargetType.SELLER, SHOP_ID,
                    ReportReason.SCAM, null));

            assertThat(filed.status()).isEqualTo(ReportStatus.OPEN);
            assertThat(filed.reviewedBy()).isNull();
            assertThat(filed.reviewedAt()).isNull();
        }
    }

    @Test
    void otherWithoutANoteIsRefused() {
        try (MockedStatic<AuthUtils> auth = signedInAs(REPORTER_ID)) {
            assertThatThrownBy(() -> service.file(new ReportRequest(
                    ReportTargetType.LISTING, LISTING_ID, ReportReason.OTHER, "   ")))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(reportRepository, never()).save(any());
    }

    /** A seller tapping report on their own listing is a mistake, not a moderation case. */
    @Test
    void reportingYourOwnContentIsRefused() {
        when(targetResolver.resolve(any(), anyString()))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("My Own Shop", SHOP_ID));

        try (MockedStatic<AuthUtils> auth = signedInAs(SHOP_ID)) {
            assertThatThrownBy(() -> service.file(new ReportRequest(
                    ReportTargetType.SELLER, SHOP_ID, ReportReason.SPAM, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportingTheSameThingTwiceWhileItIsStillOpenConflicts() {
        when(targetResolver.resolve(any(), anyString()))
                .thenReturn(new ReportTargetResolver.ResolvedTarget("Fake AirPods", SHOP_ID));
        when(reportRepository.existsByReporterIdAndTargetTypeAndTargetIdAndStatus(
                REPORTER_ID, ReportTargetType.LISTING, LISTING_ID, ReportStatus.OPEN))
                .thenReturn(true);

        try (MockedStatic<AuthUtils> auth = signedInAs(REPORTER_ID)) {
            assertThatThrownBy(() -> service.file(new ReportRequest(
                    ReportTargetType.LISTING, LISTING_ID, ReportReason.COUNTERFEIT, null)))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.CONFLICT));
        }
        verify(reportRepository, never()).save(any());
    }

    /**
     * 404 rather than 403: who reported them is precisely what a reported seller would
     * like to establish, and a 403 would confirm the report is there.
     */
    @Test
    void somebodyElsesReportIsNotFound() {
        UUID uuid = UUID.randomUUID();
        ContentReport theirs = new ContentReport();
        theirs.setReporterId("someone-else");
        when(reportRepository.findById(eq(uuid))).thenReturn(Optional.of(theirs));

        try (MockedStatic<AuthUtils> auth = signedInAs(REPORTER_ID)) {
            assertThatThrownBy(() -> service.findMine(uuid))
                    .isInstanceOf(ResponseStatusException.class)
                    .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                            .isEqualTo(HttpStatus.NOT_FOUND));
        }
    }

    private static MockedStatic<AuthUtils> signedInAs(String userId) {
        MockedStatic<AuthUtils> auth = mockStatic(AuthUtils.class);
        auth.when(AuthUtils::extractUserId).thenReturn(userId);
        return auth;
    }
}
