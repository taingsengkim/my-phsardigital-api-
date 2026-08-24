package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A landmark shot saved against a delivery address — the house, the gate, the turning
 * off the main road — so a courier can recognise the place rather than resolve a
 * street line. The caption is what usually does the work ("blue gate past the pagoda"),
 * which is why it sits next to the file rather than being inferred from it.
 *
 * <p>The file is referenced, never owned: deleting an address removes these rows but
 * leaves the upload alone, because an order placed to that address may still be
 * showing the same photo.
 */
@Entity
@Table(name = "address_photos")
@Getter
@Setter
public class AddressPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "address_id", nullable = false)
    private Address address;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id")
    private FileUpload file;

    @Column(length = 120)
    private String caption;

    /** Kept so the buyer's chosen order survives a reload. */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
