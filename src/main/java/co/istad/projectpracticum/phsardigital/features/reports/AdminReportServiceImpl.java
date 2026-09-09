package co.istad.projectpracticum.phsardigital.features.reports;

import co.istad.projectpracticum.phsardigital.config.security.AuthUtils;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseRepository;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseStatus;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportDetailResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.AdminReportRowResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportDecisionRequest;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportReporterResponse;
import co.istad.projectpracticum.phsardigital.features.reports.dto.ReportTargetDetailsResponse;
import co.istad.projectpracticum.phsardigital.features.seller.SellerProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import co.istad.projectpracticum.phsardigital.features.user.UserProfileRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What an admin does with the queue.
 *
 * <p>Closing a report does not touch what it complains about. Suspending the listing,
 * suspending the shop and deleting the review are separate endpoints that already exist,
 * and keeping them separate is what lets one report be resolved by an action taken
 * against a different target — five reports about one shop are usually answered by one
 * suspension, not five.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminReportServiceImpl implements AdminReportService {

    /** Only a completed order proves somebody actually received the thing they complain about. */
    private static final PurchaseStatus SETTLED = PurchaseStatus.COMPLETED;

    /** One order is enough to mark a reporter verified; the newest is the relevant one. */
    private static final Pageable MOST_RECENT = PageRequest.of(0, 1);

    private final ContentReportRepository reportRepository;
    private final ReportTargetHydrator targetHydrator;
    private final UserProfileRepository userProfileRepository;
    private final PurchaseRepository purchaseRepository;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminReportRowResponse> list(ReportStatus status, ReportTargetType targetType,
                                             int pageNumber, int pageSize) {
        Pageable pageable = PageRequest.of(
                pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ContentReport> page = reportRepository.findAll(matching(status, targetType), pageable);
        return new PageImpl<>(toRows(page.getContent()), pageable, page.getTotalElements());
    }

    /**
     * Both filters are optional, so an empty conjunction — every report — is the correct
     * result of asking for neither.
     */
    private static Specification<ContentReport> matching(ReportStatus status,
                                                         ReportTargetType targetType) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            if (targetType != null) {
                predicates.add(builder.equal(root.get("targetType"), targetType));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * A page of reports as queue rows.
     *
     * <p>Everything added to the row is batched across the page: the targets in at most
     * three queries, the reporters in one, the prior-report counts in one, and the
     * verified-buyer marks in two. A page of twenty costs the same handful of round trips
     * as a page of one — which is the only way this stays as cheap as the bare queue it
     * replaces.
     */
    private List<AdminReportRowResponse> toRows(List<ContentReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        ReportTargetHydrator.Snapshot targets = targetHydrator.hydrate(reports);
        Map<String, UserProfile> reporters = reportersFor(reports);
        Map<String, Long> priorReports = priorReportCountsFor(reports);
        VerifiedBuyers verified = verifiedBuyersFor(reports, targets);

        List<AdminReportRowResponse> rows = new ArrayList<>();
        for (ContentReport report : reports) {
            Listing listing = targets.listingOf(report);
            SellerProfile shop = targets.shopBehind(report);
            UserProfile reporter = reporters.get(report.getReporterId());

            rows.add(new AdminReportRowResponse(
                    report.getUuid(),
                    report.getReporterId(),
                    report.getTargetType(),
                    report.getTargetId(),
                    report.getTargetLabel(),
                    report.getReason(),
                    report.getNote(),
                    report.getStatus(),
                    report.getCreatedAt(),
                    report.getReviewedBy(),
                    report.getReviewedAt(),
                    report.getDecisionNote(),

                    listing == null || listing.getThumbnailFile() == null
                            ? null
                            : fileUploadService.getPreviewUrl(listing.getThumbnailFile()),
                    listing == null ? null : listing.getFullPrice(),
                    shop == null ? null : shop.getSellerId(),
                    shop == null ? null : shop.getBusinessName(),
                    report.getEvidence().size(),
                    reporter == null ? null : reporter.getFullName(),
                    verified.mark(report, targets),
                    // Excludes this report, matching the detail page.
                    Math.max(0, priorReports.getOrDefault(targetKey(report), 0L) - 1)));
        }
        return rows;
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportDetailResponse findOne(UUID uuid) {
        return toDetail(require(uuid));
    }

    @Override
    @Transactional
    public AdminReportDetailResponse resolve(UUID uuid, ReportDecisionRequest request) {
        return close(uuid, ReportStatus.RESOLVED, request);
    }

    @Override
    @Transactional
    public AdminReportDetailResponse dismiss(UUID uuid, ReportDecisionRequest request) {
        return close(uuid, ReportStatus.DISMISSED, request);
    }

    /**
     * Closing is refused on a report that is already closed rather than allowed to
     * overwrite it. Two admins working the same queue would otherwise silently replace
     * each other's reasoning, and the second one would never know the first had looked.
     */
    private AdminReportDetailResponse close(UUID uuid, ReportStatus outcome,
                                            ReportDecisionRequest request) {
        ContentReport report = reportRepository.findByUuidForUpdate(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found."));
        if (report.getStatus() != ReportStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This report was already " + report.getStatus().name().toLowerCase()
                            + " by " + report.getReviewedBy() + ".");
        }

        String adminId = AuthUtils.extractUserId();
        report.setStatus(outcome);
        report.setReviewedBy(adminId);
        report.setReviewedAt(LocalDateTime.now());
        report.setDecisionNote(trimToNull(request == null ? null : request.note()));

        log.info("Report {} against {} {} {} by {}", report.getUuid(), report.getTargetType(),
                report.getTargetId(), outcome.name().toLowerCase(), adminId);
        return toDetail(reportRepository.save(report));
    }

    /** The investigation view: the complaint, its evidence, who filed it, and the history. */
    private AdminReportDetailResponse toDetail(ContentReport report) {
        List<ContentReport> one = List.of(report);
        ReportTargetHydrator.Snapshot targets = targetHydrator.hydrate(one);
        ReportTargetDetailsResponse details = targetHydrator.detailsOf(report, targets);

        Map<ReportStatus, Long> history = new HashMap<>();
        for (Object[] row : reportRepository.countByStatusForTarget(
                report.getTargetType(), report.getTargetId(), report.getUuid())) {
            history.put((ReportStatus) row[0], ((Number) row[1]).longValue());
        }
        long priorTotal = history.values().stream().mapToLong(Long::longValue).sum();

        return new AdminReportDetailResponse(
                report.getUuid(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getTargetLabel(),
                report.getReason(),
                report.getNote(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getReviewedBy(),
                report.getReviewedAt(),
                report.getDecisionNote(),

                evidenceUrls(report),
                reporterOf(report, targets),
                details,
                priorTotal,
                history.getOrDefault(ReportStatus.RESOLVED, 0L));
    }

    /**
     * Signed at the moment of answering, so an admin opening the drawer gets links with a
     * full expiry window rather than ones already halfway through it.
     */
    private List<String> evidenceUrls(ContentReport report) {
        return report.getEvidence().stream()
                .map(attachment -> fileUploadService.getPreviewUrl(attachment.getFile()))
                .toList();
    }

    private ReportReporterResponse reporterOf(ContentReport report,
                                              ReportTargetHydrator.Snapshot targets) {
        UserProfile reporter = userProfileRepository.findById(report.getReporterId()).orElse(null);
        UUID orderId = verifyingOrderOf(report, targets);

        return new ReportReporterResponse(
                report.getReporterId(),
                reporter == null ? null : reporter.getFullName(),
                reporter == null ? null : reporter.getEmail(),
                reporter == null ? null : reporter.getPhone(),
                report.getTargetType() == ReportTargetType.REVIEW ? null : orderId != null,
                orderId == null ? null : orderId.toString(),
                reportRepository.countByReporterId(report.getReporterId()));
    }

    /**
     * The completed order that makes this reporter a first-hand witness, if there is one.
     *
     * <p>A complaint about a listing is verified by having bought that listing; one about
     * a shop, by having bought from that shop. A complaint about a review is not verified
     * at all — the reporter is usually the shop being criticised, or a passer-by, and
     * neither buys anything to be entitled to object.
     */
    private UUID verifyingOrderOf(ContentReport report, ReportTargetHydrator.Snapshot targets) {
        List<UUID> orders = switch (report.getTargetType()) {
            case LISTING -> {
                Listing listing = targets.listingOf(report);
                yield listing == null ? List.of() : purchaseRepository
                        .completedOrderIdsForBuyerAndListing(
                                report.getReporterId(), listing.getUuid(), SETTLED, MOST_RECENT);
            }
            case SELLER -> purchaseRepository.completedOrderIdsForBuyerAndSeller(
                    report.getReporterId(), report.getTargetId(), SETTLED, MOST_RECENT);
            case REVIEW -> List.of();
        };
        return orders.isEmpty() ? null : orders.getFirst();
    }

    // ------------------------------------------------------------ page batching

    private Map<String, UserProfile> reportersFor(List<ContentReport> reports) {
        Set<String> ids = new HashSet<>();
        reports.forEach(report -> ids.add(report.getReporterId()));
        Map<String, UserProfile> reporters = new HashMap<>();
        userProfileRepository.findAllById(ids)
                .forEach(profile -> reporters.put(profile.getId(), profile));
        return reporters;
    }

    /** Total reports per target on this page, this one included — the caller subtracts it. */
    private Map<String, Long> priorReportCountsFor(List<ContentReport> reports) {
        Set<String> targetIds = new HashSet<>();
        reports.forEach(report -> targetIds.add(report.getTargetId()));

        Map<String, Long> counts = new HashMap<>();
        for (Object[] row : reportRepository.countByStatusForTargets(targetIds)) {
            String key = row[0] + "/" + row[1];
            counts.merge(key, ((Number) row[3]).longValue(), Long::sum);
        }
        return counts;
    }

    private static String targetKey(ContentReport report) {
        return report.getTargetType() + "/" + report.getTargetId();
    }

    /**
     * The verified-buyer marks for a whole page, in two queries rather than one per row.
     */
    private VerifiedBuyers verifiedBuyersFor(List<ContentReport> reports,
                                             ReportTargetHydrator.Snapshot targets) {
        Set<String> buyerIds = new HashSet<>();
        Set<UUID> listingIds = new HashSet<>();
        Set<String> sellerIds = new HashSet<>();

        for (ContentReport report : reports) {
            if (report.getTargetType() == ReportTargetType.REVIEW) {
                continue;
            }
            buyerIds.add(report.getReporterId());
            if (report.getTargetType() == ReportTargetType.SELLER) {
                sellerIds.add(report.getTargetId());
            } else {
                Listing listing = targets.listingOf(report);
                if (listing != null) {
                    listingIds.add(listing.getUuid());
                }
            }
        }

        Set<String> boughtListing = new HashSet<>();
        if (!buyerIds.isEmpty() && !listingIds.isEmpty()) {
            purchaseRepository.completedPurchasePairs(SETTLED, buyerIds, listingIds)
                    .forEach(pair -> boughtListing.add(pair[0] + "/" + pair[1]));
        }
        Set<String> boughtFromShop = new HashSet<>();
        if (!buyerIds.isEmpty() && !sellerIds.isEmpty()) {
            purchaseRepository.completedShopPairs(SETTLED, buyerIds, sellerIds)
                    .forEach(pair -> boughtFromShop.add(pair[0] + "/" + pair[1]));
        }
        return new VerifiedBuyers(boughtListing, boughtFromShop);
    }

    /** Which reporter-and-target pairs on this page have a completed order behind them. */
    private record VerifiedBuyers(Set<String> boughtListing, Set<String> boughtFromShop) {

        Boolean mark(ContentReport report, ReportTargetHydrator.Snapshot targets) {
            return switch (report.getTargetType()) {
                // Not applicable rather than false: nobody buys anything to be entitled
                // to object to a review.
                case REVIEW -> null;
                case SELLER -> boughtFromShop.contains(
                        report.getReporterId() + "/" + report.getTargetId());
                case LISTING -> {
                    Listing listing = targets.listingOf(report);
                    yield listing != null && boughtListing.contains(
                            report.getReporterId() + "/" + listing.getUuid());
                }
            };
        }
    }

    private ContentReport require(UUID uuid) {
        return reportRepository.findById(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found."));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
