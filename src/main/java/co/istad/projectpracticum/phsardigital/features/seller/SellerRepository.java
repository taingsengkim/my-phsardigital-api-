package co.istad.projectpracticum.phsardigital.features.seller;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * {@link JpaSpecificationExecutor} backs the admin shop list, whose two filters are
 * both optional — four query methods for the combinations, or one specification.
 */
public interface SellerRepository extends JpaRepository<SellerProfile, String>,
        JpaSpecificationExecutor<SellerProfile> {

    /** Exclusive; for the administrator's own suspension/restore write. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SellerProfile s WHERE s.sellerId = :sellerId")
    Optional<SellerProfile> findByIdForUpdate(@Param("sellerId") String sellerId);

    /**
     * Shared; for trading decisions that read the shop's active flag without changing
     * it. Concurrent orders hold this together, while an administrator's exclusive
     * suspension waits for them — so the two are still ordered, but checkouts for one
     * shop no longer queue behind each other.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT s FROM SellerProfile s WHERE s.sellerId = :sellerId")
    Optional<SellerProfile> findByIdForShare(@Param("sellerId") String sellerId);

    /** Shops that may actually trade — the SELLER role survives a suspension, {@code isActive} does not. */
    long countByIsActiveTrue();

    /**
     * Trading shops within {@code radiusKm} of a point, nearest first, with how far away
     * each one is in kilometres.
     *
     * <p>Native because the distance is trigonometry over two columns: JPQL has no
     * {@code asin}, and the alternative — loading every shop and sorting in Java — reads
     * the whole table to answer a question about one neighbourhood.
     *
     * <p>The bounding box is the cheap half of the filter. It is a plain range comparison
     * an index on {@code (latitude, longitude)} can serve, and it is computed by
     * {@code GeoBoundingBox} rather than here so the trigonometry behind it stays
     * testable without a database. It over-selects — the corners of a box fall outside
     * the circle it encloses — which is why the exact distance is filtered again in the
     * outer query. That filter has to live outside the subquery: PostgreSQL cannot see a
     * {@code SELECT} alias from the {@code WHERE} of the same level.
     *
     * <p>{@code least(1, ...)} guards the {@code asin}: for two points that are the same
     * to the last decimal, rounding can carry the square root a hair above 1, where
     * {@code asin} is undefined and PostgreSQL raises rather than answering zero.
     *
     * @param wrapsAntimeridian from {@code GeoBoundingBox#wrapsAntimeridian()} — when the
     *                          box straddles the 180th meridian its west edge is the
     *                          greater number, so "between" would match nothing
     * @return {@code [sellerId, distanceKm]} per shop, closest first
     */
    @Query(value = """
            SELECT near.seller_id, near.distance_km
            FROM (
                SELECT s.seller_id AS seller_id,
                       2 * 6371.0088 * asin(least(1, sqrt(
                           sin(radians(s.latitude - :latitude) / 2)
                             * sin(radians(s.latitude - :latitude) / 2)
                           + cos(radians(:latitude)) * cos(radians(s.latitude))
                             * sin(radians(s.longitude - :longitude) / 2)
                             * sin(radians(s.longitude - :longitude) / 2)
                       ))) AS distance_km
                FROM seller_profiles s
                WHERE s.is_active = TRUE
                  AND s.latitude IS NOT NULL
                  AND s.longitude IS NOT NULL
                  AND s.latitude BETWEEN :minLatitude AND :maxLatitude
                  AND ((:wrapsAntimeridian = FALSE
                        AND s.longitude BETWEEN :minLongitude AND :maxLongitude)
                    OR (:wrapsAntimeridian = TRUE
                        AND (s.longitude >= :minLongitude OR s.longitude <= :maxLongitude)))
            ) near
            WHERE near.distance_km <= :radiusKm
            ORDER BY near.distance_km
            """, nativeQuery = true)
    List<Object[]> findNearestActive(@Param("latitude") double latitude,
                                     @Param("longitude") double longitude,
                                     @Param("radiusKm") double radiusKm,
                                     @Param("minLatitude") double minLatitude,
                                     @Param("maxLatitude") double maxLatitude,
                                     @Param("minLongitude") double minLongitude,
                                     @Param("maxLongitude") double maxLongitude,
                                     @Param("wrapsAntimeridian") boolean wrapsAntimeridian,
                                     Pageable pageable);

    /** Both back the image cleanup in {@code SellerProfileFileListener}. */
    List<SellerProfile> findAllByLogoFile_ObjectName(String objectName);

    List<SellerProfile> findAllByCoverFile_ObjectName(String objectName);
}
