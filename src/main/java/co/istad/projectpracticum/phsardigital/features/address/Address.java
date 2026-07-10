package co.istad.projectpracticum.phsardigital.features.address;

import co.istad.projectpracticum.phsardigital.config.config.BasedEntity;
import co.istad.projectpracticum.phsardigital.features.user.UserProfile;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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

    @Column(length = 50)
    private String label;

    @Column(length = 255)
    private String recipient;    // who receives the delivery

    @Column(length = 30)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String line1;

    @Column(columnDefinition = "TEXT")
    private String line2;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String province;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(name = "is_default")
    private Boolean isDefault = false;
}