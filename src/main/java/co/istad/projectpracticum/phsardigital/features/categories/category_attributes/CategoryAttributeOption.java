package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One permitted value of a SELECT or MULTI_SELECT attribute — "AMOLED", "XL", "Cotton".
 *
 * <p>{@link #value} is what a listing stores and what a facet filters on, so it is the
 * half that must stay stable; {@link #label} exists for the cases where the two differ,
 * such as storing {@code 8} while printing "8 GB".
 */
@Entity
@Table(name = "category_attribute_options",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"attribute_uuid", "value"})
        })
@Getter
@Setter
@NoArgsConstructor
public class CategoryAttributeOption extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "attribute_uuid", nullable = false)
    private CategoryAttribute attribute;

    @Column(nullable = false, length = 100)
    private String value;

    /** What a shopper reads. Falls back to {@link #value} when unset. */
    @Column(length = 150)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Protects option label/value edits made concurrently through schema replacement. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version = 0L;

    /** The label if there is one, otherwise the value — never null. */
    public String displayLabel() {
        return label == null || label.isBlank() ? value : label;
    }
}
