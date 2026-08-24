package co.istad.projectpracticum.phsardigital.features.cart;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface CartRepository extends JpaRepository<Cart, UUID> {
    // all carts for a buyer (one per shop)
    List<Cart> findByBuyerId(String buyerId);
    // the buyer's cart for a specific shop, if any
    Optional<Cart> findByBuyerIdAndSellerProfile_SellerId(String buyerId, String sellerId);

    /**
     * Serializes mutations and checkout through the cart's parent row. Callers must
     * keep the lookup and the complete mutation in the same transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cart c "
            + "WHERE c.buyerId = :buyerId "
            + "AND c.sellerProfile.sellerId = :sellerId")
    Optional<Cart> findByBuyerIdAndSellerIdForUpdate(
            @Param("buyerId") String buyerId,
            @Param("sellerId") String sellerId);
}
