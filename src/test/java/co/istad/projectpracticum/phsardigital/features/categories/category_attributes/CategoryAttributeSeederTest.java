package co.istad.projectpracticum.phsardigital.features.categories.category_attributes;

import co.istad.projectpracticum.phsardigital.features.categories.Category;
import co.istad.projectpracticum.phsardigital.features.categories.CategoryRepository;
import co.istad.projectpracticum.phsardigital.features.listings.ListingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryAttributeSeederTest {

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private CategoryAttributeRepository attributeRepository;
    @Mock
    private ListingRepository listingRepository;

    @Test
    void doesNotAttachRequiredRuntimeSchemaToAnAlreadyStockedSubtree() {
        Category phones = new Category();
        phones.setUuid(UUID.randomUUID());
        phones.setSlug("phones");
        when(categoryRepository.findMutableBySlugForUpdate("phones"))
                .thenReturn(Optional.of(phones));
        when(attributeRepository.existsByCategory_Uuid(phones.getUuid())).thenReturn(false);
        when(listingRepository.existsByCategory_UuidIn(anyCollection())).thenReturn(true);

        new CategoryAttributeSeeder(
                categoryRepository, attributeRepository, listingRepository)
                .run(mock(ApplicationArguments.class));

        verify(attributeRepository, never()).saveAll(org.mockito.ArgumentMatchers.anyList());
    }
}
