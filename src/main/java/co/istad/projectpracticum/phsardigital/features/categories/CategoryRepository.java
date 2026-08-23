package co.istad.projectpracticum.phsardigital.features.categories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Page<Category> findAllByIsDeletedFalse(Pageable pageable);

    List<Category> findAllByIsDeletedFalse(Sort sort);

    List<Category> findAllByIsDeletedFalseAndIsActiveTrue(Sort sort);

    Optional<Category> findBySlugAndIsDeletedFalse(String slug);

    Optional<Category> findByUuidAndIsDeletedFalse(UUID uuid);

    /** Serialises hierarchy edits involving the same nodes; {@code @Version} catches the rest. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.uuid = :uuid AND c.isDeleted = false")
    Optional<Category> findMutableByUuidForUpdate(@Param("uuid") UUID uuid);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Category c WHERE c.slug = :slug AND c.isDeleted = false")
    Optional<Category> findMutableBySlugForUpdate(@Param("slug") String slug);

    List<Category> findAllByParentCategory_UuidAndIsDeletedFalse(UUID parentUuid, Sort sort);

    List<Category> findAllByParentCategory_UuidAndIsDeletedFalseAndIsActiveTrue(UUID parentUuid, Sort sort);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndUuidNot(String slug, UUID id);

    boolean existsByParentCategoryIsNullAndNameIgnoreCaseAndIsDeletedFalse(String name);

    boolean existsByParentCategory_UuidAndNameIgnoreCaseAndIsDeletedFalse(UUID parentUuid, String name);

    boolean existsByParentCategoryIsNullAndNameIgnoreCaseAndIsDeletedFalseAndUuidNot(
            String name, UUID uuid);

    boolean existsByParentCategory_UuidAndNameIgnoreCaseAndIsDeletedFalseAndUuidNot(
            UUID parentUuid, String name, UUID uuid);

    List<Category> findAllByIconFile_ObjectName(String iconFileObjectName);
}
