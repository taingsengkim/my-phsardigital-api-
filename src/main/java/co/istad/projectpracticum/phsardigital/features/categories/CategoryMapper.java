package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryTreeResponse;
import co.istad.projectpracticum.phsardigital.features.file.FileUploadService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Resolves the icon URL here rather than in the service, so every response path
 * gets it. Previously only the service's hand-written mapper filled {@code
 * iconUrl}, leaving it null on the tree and children endpoints.
 */
@Mapper(componentModel = "spring")
public abstract class CategoryMapper {

    @Autowired
    protected FileUploadService fileUploadService;

    @Mapping(target = "parentCategory", ignore = true)
    @Mapping(target = "iconFile", ignore = true)
    @Mapping(target = "level", ignore = true)
    @Mapping(target = "version", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "isDeleted", ignore = true)
    @Mapping(target = "childCategories", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastModifiedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "lastModifiedBy", ignore = true)
    public abstract Category toCategory(CategoryRequest categoryRequest);

    @Mapping(target = "parentUuid", source = "parentCategory.uuid")
    @Mapping(target = "iconUrl", expression = "java(iconUrl(category))")
    public abstract CategoryResponse toCategoryResponse(Category category);

    @Mapping(target = "children", expression = "java(new java.util.ArrayList<>())")
    @Mapping(target = "iconUrl", expression = "java(iconUrl(category))")
    public abstract CategoryTreeResponse toCategoryTreeResponse(Category category);

    protected String iconUrl(Category category) {
        if (category == null || category.getIconFile() == null) {
            return null;
        }
        return fileUploadService.getPreviewUrl(category.getIconFile());
    }
}
