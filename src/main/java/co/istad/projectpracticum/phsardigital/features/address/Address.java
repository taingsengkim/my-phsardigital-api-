package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "addresses")
@Getter
@Setter
@NoArgsConstructor
public class Address extends BasedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private UserProfile userProfile;

    /**
     * Which fields below this address actually uses — see {@link AddressType}.
     *
     * <p>Nullable in the column only so rows written before the split still load; every
     * write goes through the service, which requires one.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", length = 20)
    private AddressType type;

    @Column(length = 50)
    private String label;

    @Column(length = 255)
    private String recipient;    // who receives the delivery

    @Column(length = 30)
    private String phone;

    // ---- CITY only ----

    /** The building, borey or shop the courier is looking for. */
    @Column(name = "location_name", length = 255)
    private String locationName;

    @Column(name = "street_no", length = 50)
    private String streetNo;

    // ---- PROVINCE only ----

    @Column(length = 100)
    private String province;

    // ---- both shapes ----

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String commune;

    @Column(length = 100)
    private String village;

    /**
     * The fields above rendered as one line, rebuilt by the service on every write.
     *
     * <p>It exists because a purchase copies where it is going as text rather than
     * pointing at this row, and that copy has to read the same whichever shape produced
     * it. Derived, never sent by a client.
     *
     * <p>The column is still called {@code line1}: it held the free-text first line
     * before addresses were split into shapes, and reusing it keeps rows written back
     * then readable — their old text simply stands in for the render.
     */
    @Column(name = "line1", nullable = false, columnDefinition = "TEXT")
    private String formattedAddress;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "is_default")
    private Boolean isDefault = false;

    /**
     * Landmark shots for the courier. Ordered so the buyer's sequence survives a
     * reload — normally the road in first, the door last.
     */
    @OneToMany(mappedBy = "address", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<AddressPhoto> photos = new ArrayList<>();
}
