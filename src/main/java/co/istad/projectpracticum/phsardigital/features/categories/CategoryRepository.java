package co.istad.projectpracticum.phsardigital.features.categories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Page<Category> findAllByIsDeletedFalse(Pageable pageable);

    List<Category> findAllByIsDeletedFalse(Sort sort);

    Optional<Category> findBySlugAndIsDeletedFalse(String slug);

    Optional<Category> findByUuidAndIsDeletedFalse(UUID uuid);

    List<Category> findAllByParentCategory_UuidAndIsDeletedFalse(UUID parentUuid, Sort sort);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameAndUuidNot(String name, UUID id);

    boolean existsBySlugAndUuidNot(String slug, UUID id);

    List<Category> findAllByIconFile_ObjectName(String iconFileObjectName);
}
