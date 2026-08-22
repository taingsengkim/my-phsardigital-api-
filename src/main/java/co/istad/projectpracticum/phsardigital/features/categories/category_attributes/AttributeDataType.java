package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

/**
 * What kind of value an attribute holds, and therefore what a seller may type into it.
 *
 * <p>Values are stored as text either way — a spec table prints them, it does not compute
 * with them. The type is what lets the API reject "Plasma" as a phone's panel or "big" as
 * its screen size, and what tells a storefront whether to draw a text box, a checkbox or
 * a dropdown.
 */
public enum AttributeDataType {

    /** Free text: a chipset name, a model number. */
    TEXT,

    /** A number, optionally bounded by the attribute's min/max. The unit lives beside it. */
    NUMBER,

    /** Yes or no: water resistant, dual SIM. Normalised to {@code true}/{@code false}. */
    BOOLEAN,

    /** Exactly one of the attribute's options — a shirt's size, a phone's panel. */
    SELECT,

    /** Any number of the attribute's options, stored comma-separated. */
    MULTI_SELECT
}
