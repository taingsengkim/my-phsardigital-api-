package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.AdminBuyerSummaryResponse;
import co.istad.projectpracticum.phsardigital.features.user.dto.ModerateBuyerRequest;
import org.springframework.data.domain.Page;

import java.time.LocalDate;

/**
 * The admin's view of buyer accounts and the moderation acts available on them.
 *
 * <p>A buyer is an account with no shop. There is no buyer role to filter on — roles
 * live in Keycloak, not here — so the absence of a {@code SellerProfile} is the
 * marketplace's own definition, and it is applied identically by every method below.
 */
public interface AdminBuyerService {

    /**
     * @param status     filters by standing; null lists every buyer
     * @param search     case-insensitive substring of name, email, or phone
     * @param joinedFrom earliest join date, inclusive; null for no lower bound
     * @param joinedTo   latest join date, inclusive of the whole day; null for none
     */
    Page<AdminBuyerResponse> list(UserStatus status, String search,
                                  LocalDate joinedFrom, LocalDate joinedTo,
                                  int pageNumber, int pageSize);

    /** The counters above the table, over the same buyer population as {@link #list}. */
    AdminBuyerSummaryResponse summary();

    /** A temporary hold an admin expects to lift. */
    AdminBuyerResponse suspend(String userId, ModerateBuyerRequest request);

    /** Permanent removal. Separate from suspension so the two stay tellable apart. */
    AdminBuyerResponse ban(String userId, ModerateBuyerRequest request);

    /** Returns a suspended or banned account to good standing, clearing the trail. */
    AdminBuyerResponse restore(String userId);
}
