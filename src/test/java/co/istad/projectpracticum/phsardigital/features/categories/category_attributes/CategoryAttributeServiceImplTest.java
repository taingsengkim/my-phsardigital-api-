package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryAvailability;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeOptionRequest;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeRequest;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.UpdateCategoryAttributeRequest;
import co.istad.projectpracticum.phsardigital.features.listings.ListingSchemaImpactValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryAttributeServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryAttributeRepository attributeRepository;
    @Mock
    private CategoryAttributeResolver resolver;
    @Mock
    private CategoryAttributeMapper mapper;
    @Mock
    private CategoryAvailability categoryAvailability;
    @Mock
    private ListingSchemaImpactValidator listingSchemaImpactValidator;

    private CategoryAttributeServiceImpl service;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new CategoryAttributeServiceImpl(
                categoryRepository, attributeRepository, resolver, mapper,
                categoryAvailability, listingSchemaImpactValidator);
        category = new Category();
        category.setUuid(UUID.randomUUID());
        category.setName("Phones");
        category.setSlug("phones");
        category.setIsDeleted(false);
        lenient().when(categoryRepository.findByUuidAndIsDeletedFalse(category.getUuid()))
                .thenReturn(Optional.of(category));
        lenient().when(categoryRepository.findMutableByUuidForUpdate(category.getUuid()))
                .thenReturn(Optional.of(category));
    }

    @Test
    void restoresASoftDeletedDefinitionInsteadOfInsertingADuplicateCode() {
        UUID originalUuid = UUID.randomUUID();
        CategoryAttribute deleted = new CategoryAttribute();
        deleted.setUuid(originalUuid);
        deleted.setCategory(category);
        deleted.setCode("brand");
        deleted.setLabel("Old brand");
        deleted.setIsDeleted(true);

        when(attributeRepository.findByCategory_UuidAndCodeIgnoreCase(category.getUuid(), "brand"))
                .thenReturn(Optional.of(deleted));
        when(attributeRepository.saveAllAndFlush(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.create(category.getUuid(), List.of(textRequest("brand", "Brand")));

        assertThat(deleted.getUuid()).isEqualTo(originalUuid);
        assertThat(deleted.getIsDeleted()).isFalse();
        assertThat(deleted.getLabel()).isEqualTo("Brand");
        assertThat(deleted.getDataType()).isEqualTo(AttributeDataType.TEXT);
        verify(listingSchemaImpactValidator).revalidatePublishedListings(category);
    }

    @Test
    void publicSchemaDoesNotExposeAnInactiveCategory() {
        when(categoryAvailability.isEffectivelyActive(category)).thenReturn(false);

        assertThatThrownBy(() -> service.getSchema(category.getUuid(), true))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void adminSchemaCanReadAnInactiveNonDeletedCategory() {
        category.setIsActive(false);
        when(resolver.effectiveFor(category)).thenReturn(List.of());

        var response = service.getSchemaForAdmin(category.getUuid(), true);

        assertThat(response.categoryUuid()).isEqualTo(category.getUuid());
        assertThat(response.categorySlug()).isEqualTo("phones");
        assertThat(response.attributes()).isEmpty();
        verify(resolver).effectiveFor(category);
        verifyNoInteractions(categoryAvailability);
    }

    @Test
    void refusesToRenameTheStableAttributeCode() {
        CategoryAttribute attribute = activeAttribute("brand", "Brand");
        when(attributeRepository.findByUuidAndIsDeletedFalse(attribute.getUuid()))
                .thenReturn(Optional.of(attribute));

        UpdateCategoryAttributeRequest update = update("manufacturer", null);

        assertThatThrownBy(() -> service.update(category.getUuid(), attribute.getUuid(), update))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("stable identifier");
                });
    }

    @Test
    void acceptsResendingTheCurrentCodeAsAnIdempotentPatch() {
        CategoryAttribute attribute = activeAttribute("brand", "Brand");
        when(attributeRepository.findByUuidAndIsDeletedFalse(attribute.getUuid()))
                .thenReturn(Optional.of(attribute));
        when(attributeRepository.saveAndFlush(attribute)).thenReturn(attribute);

        service.update(category.getUuid(), attribute.getUuid(), update("brand", null));

        assertThat(attribute.getCode()).isEqualTo("brand");
    }

    @Test
    void refusesABlankLabelEvenWhenServiceValidationIsCalledDirectly() {
        CategoryAttribute attribute = activeAttribute("brand", "Brand");
        when(attributeRepository.findByUuidAndIsDeletedFalse(attribute.getUuid()))
                .thenReturn(Optional.of(attribute));

        assertThatThrownBy(() -> service.update(
                category.getUuid(), attribute.getUuid(), update(null, "   ")))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("must not be blank");
                });
    }

    @Test
    void refusesCommaContainingChoiceValuesBecauseMultiSelectUsesCommaForDisplay() {
        when(attributeRepository.findByCategory_UuidAndCodeIgnoreCase(category.getUuid(), "colours"))
                .thenReturn(Optional.empty());
        CategoryAttributeRequest request = new CategoryAttributeRequest(
                "colours", "Colours", null, AttributeDataType.MULTI_SELECT, null,
                false, true, null, null, 0, 0,
                List.of(new CategoryAttributeOptionRequest("Black, White", null, 0)));

        assertThatThrownBy(() -> service.create(category.getUuid(), List.of(request)))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(exception.getReason()).contains("must not contain a comma");
                });
    }

    @Test
    void explicitlyClearsNullableUnitAndNumericBounds() {
        CategoryAttribute attribute = activeAttribute("weight", "Weight");
        attribute.setDataType(AttributeDataType.NUMBER);
        attribute.setUnit("kg");
        attribute.setMinValue(1.0);
        attribute.setMaxValue(100.0);
        when(attributeRepository.findByUuidAndIsDeletedFalse(attribute.getUuid()))
                .thenReturn(Optional.of(attribute));
        when(attributeRepository.saveAndFlush(attribute)).thenReturn(attribute);

        service.update(category.getUuid(), attribute.getUuid(),
                new UpdateCategoryAttributeRequest(
                        null, null, null, null,
                        null, true,
                        null, null,
                        null, true,
                        null, true,
                        null, null, null));

        assertThat(attribute.getUnit()).isNull();
        assertThat(attribute.getMinValue()).isNull();
        assertThat(attribute.getMaxValue()).isNull();
    }

    private CategoryAttribute activeAttribute(String code, String label) {
        CategoryAttribute attribute = new CategoryAttribute();
        attribute.setUuid(UUID.randomUUID());
        attribute.setCategory(category);
        attribute.setCode(code);
        attribute.setLabel(label);
        attribute.setDataType(AttributeDataType.TEXT);
        attribute.setRequired(false);
        attribute.setFilterable(false);
        attribute.setSortOrder(0);
        attribute.setGroupSortOrder(0);
        attribute.setIsDeleted(false);
        return attribute;
    }

    private static CategoryAttributeRequest textRequest(String code, String label) {
        return new CategoryAttributeRequest(
                code, label, null, AttributeDataType.TEXT, null,
                false, false, null, null, 0, 0, List.of());
    }

    private static UpdateCategoryAttributeRequest update(String code, String label) {
        return new UpdateCategoryAttributeRequest(
                code, label, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
