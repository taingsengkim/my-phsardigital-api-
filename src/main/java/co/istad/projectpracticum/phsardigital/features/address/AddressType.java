package co.istad.projectpracticum.phsardigital.features.address;

/**
 * Which shape a saved address takes.
 *
 * <p>These are not two variations on one form. Out in the provinces an address is the
 * administrative chain and nothing else — there are no street numbers to give — so the
 * courier finds the place by village. In the city the chain alone is far too coarse, so
 * the address is a named place on a numbered street instead.
 *
 * <p>Both shapes share district, commune and village; each adds the fields only it can
 * fill. Enforced in {@code AddressServiceImpl}, which also refuses fields belonging to
 * the other shape rather than storing a half-filled form of each.
 */
public enum AddressType {

    /** province, district, commune, village. */
    PROVINCE,

    /** location name, street no., district, commune, village. */
    CITY
}
