package co.istad.projectpracticum.phsardigital.features.cart;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CartRepository extends JpaRepository<Cart, UUID> {
    // all carts for a buyer (one per shop)
    List<Cart> findByBuyerId(String buyerId);
    // the buyer's cart for a specific shop, if any
    Optional<Cart> findByBuyerIdAndSellerProfile_SellerId(String buyerId, String sellerId);
}