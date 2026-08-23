package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryAttributeRepository extends JpaRepository<CategoryAttribute, UUID> {

    /**
     * Every definition owned by any of these categories — the branch from a category up
     * to its root, fetched in one query rather than one per level.
     *
     * <p>The options come with them: a schema response prints them all, and loading them
     * per attribute is what would make a twelve-attribute phone schema thirteen queries.
     */
    @EntityGraph(attributePaths = {"options", "category"})
    List<CategoryAttribute> findAllByCategory_UuidInAndIsDeletedFalse(Collection<UUID> categoryUuids);

    @EntityGraph(attributePaths = {"options", "category"})
    List<CategoryAttribute> findAllByCategory_UuidAndIsDeletedFalse(UUID categoryUuid);

    Optional<CategoryAttribute> findByUuidAndIsDeletedFalse(UUID uuid);

    /** Includes soft-deleted rows so create can restore the stable definition UUID. */
    @EntityGraph(attributePaths = {"options", "category"})
    Optional<CategoryAttribute> findByCategory_UuidAndCodeIgnoreCase(UUID categoryUuid, String code);

    boolean existsByCategory_Uuid(UUID categoryUuid);

    long countByCategory_UuidAndIsDeletedFalse(UUID categoryUuid);
}
