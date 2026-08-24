package co.istad.projectpracticum.phsardigital.features.purchases;

import co.istad.projectpracticum.phsardigital.features.file.FileUpload;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * A landmark photo copied onto the order at checkout, on the same terms as
 * {@code shippingAddress}: the order keeps its own row rather than pointing at the
 * buyer's saved address, so editing or deleting that address later cannot rewrite
 * where a past delivery was going.
 *
 * <p>The file itself is shared with the address rather than duplicated in storage.
 * Deleting the upload therefore does clear it from the order — the alternative is
 * copying every image at checkout, which costs more than it protects.
 */
@Entity
@Table(name = "purchase_delivery_photos")
@Getter
@Setter
public class PurchaseDeliveryPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne
    @JoinColumn(name = "purchase_uuid", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id")
    private FileUpload file;

    @Column(length = 120)
    private String caption;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
