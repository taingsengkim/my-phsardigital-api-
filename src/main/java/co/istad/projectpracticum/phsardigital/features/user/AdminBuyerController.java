package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.ModerateBuyerRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * The admin buyer screen.
 *
 * <p>Distinct from {@code /api/v1/admin/users}, which lists every account including
 * shop owners: this one is scoped to buyers and carries their order totals.
 */
@RestController
@RequestMapping("/api/v1/admin/buyers")
@RequiredArgsConstructor
@Validated
public class AdminBuyerController {

    private final AdminBuyerService adminBuyerService;

    /**
     * @param status     omit to list every standing
     * @param search     matches name, email, or phone
     * @param joinedFrom {@code yyyy-MM-dd}, inclusive
     * @param joinedTo   {@code yyyy-MM-dd}, inclusive of that whole day
     */
    @GetMapping
    public Page<AdminBuyerResponse> list(
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false)
            @Size(max = 100, message = "Search must not exceed 100 characters")
            String search,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinedTo,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "Page number must be zero or greater")
            int pageNumber,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Page size must be at least 1")
            @Max(value = 100, message = "Page size must not exceed 100")
            int pageSize) {
        return adminBuyerService.list(status, search, joinedFrom, joinedTo, pageNumber, pageSize);
    }

    /** The four counters above the table. */
    @GetMapping("/summary")
    public AdminBuyerSummaryResponse summary() {
        return adminBuyerService.summary();
    }

    @PatchMapping("/{userId}/suspend")
    public AdminBuyerResponse suspend(@PathVariable String userId,
                                      @Valid @RequestBody ModerateBuyerRequest request) {
        return adminBuyerService.suspend(userId, request);
    }

    @PatchMapping("/{userId}/ban")
    public AdminBuyerResponse ban(@PathVariable String userId,
                                  @Valid @RequestBody ModerateBuyerRequest request) {
        return adminBuyerService.ban(userId, request);
    }

    @PatchMapping("/{userId}/restore")
    public AdminBuyerResponse restore(@PathVariable String userId) {
        return adminBuyerService.restore(userId);
    }
}
