package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeOptionResponse;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * {@code inherited} is mapped false here and flipped by
 * {@link CategoryAttributeResponse#asInherited()}, because whether a definition is
 * inherited is not a property of the definition — it depends on which category is being
 * asked about.
 */
@Mapper(componentModel = "spring")
public abstract class CategoryAttributeMapper {

    @Mapping(target = "group", source = "groupName")
    @Mapping(target = "inherited", constant = "false")
    @Mapping(target = "ownerCategoryUuid", source = "category.uuid")
    @Mapping(target = "ownerCategorySlug", source = "category.slug")
    @Mapping(target = "ownerCategoryName", source = "category.name")
    public abstract CategoryAttributeResponse toResponse(CategoryAttribute attribute);

    @Mapping(target = "label", expression = "java(option.displayLabel())")
    public abstract CategoryAttributeOptionResponse toResponse(CategoryAttributeOption option);
}
