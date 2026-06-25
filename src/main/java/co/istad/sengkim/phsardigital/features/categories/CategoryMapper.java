package co.istad.sengkim.phsardigital.features.categories;

import co.istad.sengkim.phsardigital.features.categories.dto.CategoryRequest;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryResponse;
import co.istad.sengkim.phsardigital.features.categories.dto.CategoryTreeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    @Mapping(target = "parentCategory", ignore = true)
    Category toCategory(CategoryRequest categoryRequest);
    Category toCategory(CategoryResponse categoryResponse);
    @Mapping(target = "parentUuid",source = "parentCategory.uuid")
    CategoryResponse toCategoryResponse(Category category);
    @Mapping(target = "children", expression = "java(new java.util.ArrayList<>())")
    CategoryTreeResponse toCategoryTreeResponse(Category category);
}
