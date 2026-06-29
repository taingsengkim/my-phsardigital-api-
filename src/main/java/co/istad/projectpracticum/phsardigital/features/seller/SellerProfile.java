package co.istad.projectpracticum.phsardigital.features.seller;

import co.istad.projectpracticum.phsardigital.features.listings.Listing;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
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
        this.userId = userId;
    }
    @Id
    private String userId;
    private String phoneNumber;
    private String biography;
    private List<String> socialLink;
    @OneToMany(mappedBy = "sellerProfile")
    private List<Listing>listings;
}
