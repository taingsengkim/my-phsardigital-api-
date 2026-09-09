package co.istad.projectpracticum.phsardigital.features.purchases.dto;

import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseActivityAction;
import co.istad.projectpracticum.phsardigital.features.purchases.PurchaseActorType;

import java.time.LocalDateTime;

/**
 * One entry on an order's lifecycle timeline, oldest first.
 *
 * <p>Derived from the timestamps the order carries, not read from an audit log — there
 * is no audit log. That has a consequence the UI must respect: the timeline is complete
 * for state changes and silent about everything else, and an order placed before those
 * timestamp columns existed shows only the events it can prove.
 *
 * @param actorName who acted, or null when nothing recorded it — see
 *                  {@link PurchaseActorType#UNKNOWN}
 */
public record PurchaseActivityResponse(
        LocalDateTime timestamp,
        PurchaseActivityAction action,
        PurchaseActorType actorType,
        String actorName,
        String description
) {
}
