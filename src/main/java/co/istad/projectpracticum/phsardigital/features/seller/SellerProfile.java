package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Getter
@Setter
@Table(name = "seller_profile")
@NoArgsConstructor
public class SellerProfile {

    public SellerProfile(String userId){
        this.sellerId = userId;
    }
    @Id
    private String sellerId;
    @Column(length = 20)
    private String phoneNumber;
    @Column(length = 1000)
    private String biography;
    @ElementCollection
    @CollectionTable(
            name = "seller_social_links",
            joinColumns = @JoinColumn(name = "seller_id")
    )
    @Column(name = "social_link")
    private List<String> socialLink;
    @OneToMany(mappedBy = "sellerProfile")
    private List<Listing>listings;
}
