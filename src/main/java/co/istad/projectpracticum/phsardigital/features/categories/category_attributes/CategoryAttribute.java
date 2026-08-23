package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.categories.Category;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One spec a category expects its listings to carry: a shirt's size, a phone's refresh
 * rate. The category says what may be filled in; {@code ListingAttribute} is one product's
 * answer.
 *
 * <p>Attributes are inherited down the tree — an attribute on Electronics applies to
 * Phones underneath it, and a Phones attribute of the same code overrides it. So shared
 * specs like "brand" are declared once near the root instead of on every leaf.
 *
 * <p>{@link #groupName} is what turns a flat list into the sectioned spec table a product
 * page prints: Display, Performance, Camera, Battery.
 */
@Entity
@Table(name = "category_attributes",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"category_uuid", "code"})
        })
@Getter
@Setter
@NoArgsConstructor
public class CategoryAttribute extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(optional = false)
    @JoinColumn(name = "category_uuid", nullable = false)
    private Category category;

    /**
     * The stable identifier a listing stores against, e.g. {@code screen_size}. Renaming
     * the label leaves it alone, so existing listings keep matching their definition.
     */
    @Column(nullable = false, length = 100)
    private String code;

    /** What a shopper reads: "Screen size". */
    @Column(nullable = false, length = 150)
    private String label;

    /** The spec-table section, e.g. "Display". Null means it prints under "Other". */
    @Column(name = "group_name", length = 100)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 20)
    private AttributeDataType dataType = AttributeDataType.TEXT;

    /**
     * Printed after the value — {@code in}, {@code GB}, {@code mAh}. Kept out of the
     * value itself so a NUMBER stays numeric and can be compared and sorted.
     */
    @Column(length = 20)
    private String unit;

    /** A listing in this category cannot be saved without it. */
    @Column(name = "is_required", nullable = false)
    private Boolean required = false;

    /** Offered as a facet on the browse query. */
    @Column(name = "is_filterable", nullable = false)
    private Boolean filterable = false;

    /** Bounds for {@link AttributeDataType#NUMBER}; null means unbounded on that side. */
    @Column(name = "min_value")
    private Double minValue;

    @Column(name = "max_value")
    private Double maxValue;

    /** Position within the group. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Position of the group itself, so Display can be made to print before Battery. */
    @Column(name = "group_sort_order", nullable = false)
    private Integer groupSortOrder = 0;

    /**
     * Soft-deleted like the category itself: listings already carrying this attribute
     * keep their value, it simply stops being offered or required.
     */
    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    /** Detects concurrent schema edits instead of silently losing one administrator's work. */
    @Version
    @Column(nullable = false, columnDefinition = "bigint default 0")
    private Long version = 0L;

    /** The permitted values, for SELECT and MULTI_SELECT. Empty for every other type. */
    @OneToMany(mappedBy = "attribute", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<CategoryAttributeOption> options = new ArrayList<>();

    /** True when the type draws its values from {@link #options}. */
    public boolean isChoice() {
        return dataType == AttributeDataType.SELECT || dataType == AttributeDataType.MULTI_SELECT;
    }
}
