package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Page<Category> findAllByIsDeletedFalse(Pageable pageable);

    Optional<Category> findBySlug(String slug);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);

    boolean existsByNameAndUuidNot(@Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters") String name, UUID id);

    boolean existsBySlugAndUuidNot(@Size(max = 150, message = "Slug must not exceed 150 characters") @Pattern(
                regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                message = "Slug must be lowercase alphanumeric with hyphens (e.g. my-category)"
        ) String slug, UUID id);

//    List<Category> findAllByIconFileObjectName(String iconFileObjectName);

    List<Category> findAllByIconFile_ObjectName(String iconFileObjectName);
}
