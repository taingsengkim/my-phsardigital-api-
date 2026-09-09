package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseDetailResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseRowResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.AdminPurchaseSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.purchases.dto.PurchaseActivityResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Marketplace-wide order views for administrators.
 *
 * <p>Read-only by design in this first pass. Everything under {@code /api/v1/admin/**} is
 * already closed to the ADMIN role by the catch-all in {@code SecurityConfig}, so no rule
 * is added here — a new admin endpoint is shut by default rather than open by default.
 */
@RestController
@RequestMapping("/api/v1/admin/purchases")
@RequiredArgsConstructor
@Validated
public class AdminPurchaseController {

    private final AdminPurchaseService service;

    /**
     * The orders table.
     *
     * <p>Filters are repeatable parameters rather than one comma-joined string
     * ({@code ?status=PENDING&status=CONFIRMED}): Spring binds a list of enums directly,
     * so an unknown value fails at the edge as a 400 naming the bad constant instead of
     * being split and mis-parsed further in.
     *
     * @param startDate inclusive, {@code endDate} exclusive — pass the same date twice to
     *                  get nothing, and consecutive days to get exactly one
     */
    @GetMapping
    public Page<AdminPurchaseRowResponse> list(
            @RequestParam(required = false) List<PurchaseStatus> status,
            @RequestParam(required = false) List<PurchaseChannel> channel,
            @RequestParam(required = false) List<PaymentMethod> paymentMethod,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) String buyerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {

        return service.list(filter(status, channel, paymentMethod, sellerId, buyerId,
                startDate, endDate, search), page, size, sortBy, sortOrder);
    }

    /**
     * The KPI cards and status-tab badges.
     *
     * <p>Defaults to month-to-date, which is the window the cards are read as covering.
     * Growth compares against the preceding window of the same length.
     */
    @GetMapping("/summary")
    public AdminPurchaseSummaryResponse summary(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        LocalDateTime to = endDate != null ? endDate : LocalDateTime.now();
        LocalDateTime from = startDate != null
                ? startDate
                : to.toLocalDate().withDayOfMonth(1).atStartOfDay();
        if (!from.isBefore(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before endDate.");
        }
        return service.summarise(from, to);
    }

    @GetMapping("/{uuid}")
    public AdminPurchaseDetailResponse get(@PathVariable UUID uuid) {
        return service.get(uuid);
    }

    /** The order's lifecycle. Also embedded in the detail response, for the drawer. */
    @GetMapping("/{uuid}/activities")
    public List<PurchaseActivityResponse> activities(@PathVariable UUID uuid) {
        return service.activities(uuid);
    }

    /**
     * The filtered table as CSV.
     *
     * <p>Takes the same parameters as {@link #list} so an export matches the table it came
     * from. Paging is deliberately absent: the export is the whole result, capped inside
     * the service.
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) List<PurchaseStatus> status,
            @RequestParam(required = false) List<PurchaseChannel> channel,
            @RequestParam(required = false) List<PaymentMethod> paymentMethod,
            @RequestParam(required = false) String sellerId,
            @RequestParam(required = false) String buyerId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {

        String csv = service.exportCsv(filter(status, channel, paymentMethod, sellerId,
                buyerId, startDate, endDate, search), sortBy, sortOrder);

        String filename = "orders-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + ".csv";
        // A BOM so Excel opens UTF-8 correctly. Without it Khmer names in a shop or a
        // recipient field come out as mojibake on a default Windows install.
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static AdminPurchaseFilter filter(List<PurchaseStatus> status,
                                              List<PurchaseChannel> channel,
                                              List<PaymentMethod> paymentMethod,
                                              String sellerId,
                                              String buyerId,
                                              LocalDateTime startDate,
                                              LocalDateTime endDate,
                                              String search) {
        return new AdminPurchaseFilter(status, channel, paymentMethod,
                sellerId, buyerId, startDate, endDate, search);
    }
}
