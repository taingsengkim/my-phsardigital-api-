package co.istad.projectpracticum.phsardigital.features.categories;

import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.CategoryAttributeService;
import co.istad.projectpracticum.phsardigital.features.categories.category_attributes.dto.CategoryAttributeSchemaResponse;
import co.istad.projectpracticum.phsardigital.features.categories.dto.CategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminCategoryControllerWebTest {

    @Mock
    private CategoryService categoryService;
    @Mock
    private CategoryAttributeService categoryAttributeService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminCategoryController(categoryService, categoryAttributeService))
                .build();
    }

    @Test
    void listsInactiveCategoriesOnTheAdminPath() throws Exception {
        CategoryResponse inactive = response(false);
        when(categoryService.findAllForAdmin(0, 25)).thenReturn(new PageImpl<>(
                List.of(inactive), PageRequest.of(0, 25), 1));

        mockMvc.perform(get("/api/v1/admin/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].uuid").value(inactive.uuid().toString()))
                .andExpect(jsonPath("$.content[0].isActive").value(false));
    }

    @Test
    void readsAnInactiveCategoryByUuidOnTheAdminPath() throws Exception {
        CategoryResponse inactive = response(false);
        when(categoryService.findByUuidForAdmin(inactive.uuid())).thenReturn(inactive);

        mockMvc.perform(get("/api/v1/admin/categories/{uuid}", inactive.uuid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(inactive.uuid().toString()))
                .andExpect(jsonPath("$.isActive").value(false));
    }

    @Test
    void readsAnInactiveCategorySchemaWithoutUsingThePublicAvailabilityGate() throws Exception {
        CategoryResponse inactive = response(false);
        CategoryAttributeSchemaResponse schema = new CategoryAttributeSchemaResponse(
                inactive.uuid(), inactive.slug(), inactive.name(), List.of(), List.of());
        when(categoryAttributeService.getSchemaForAdmin(inactive.uuid(), false))
                .thenReturn(schema);

        mockMvc.perform(get("/api/v1/admin/categories/{uuid}/attributes", inactive.uuid())
                        .param("includeInherited", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryUuid").value(inactive.uuid().toString()))
                .andExpect(jsonPath("$.categorySlug").value("draft-category"))
                .andExpect(jsonPath("$.attributes").isArray());

        verify(categoryAttributeService).getSchemaForAdmin(inactive.uuid(), false);
    }

    private static CategoryResponse response(boolean active) {
        return new CategoryResponse(
                UUID.randomUUID(), "Draft category", "draft-category", null,
                null, 1, 0, active, null);
    }
}
