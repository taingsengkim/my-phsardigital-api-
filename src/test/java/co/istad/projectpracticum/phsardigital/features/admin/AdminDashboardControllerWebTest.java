package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.ApplicationSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.ListingSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.Money;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.PlanSubscriptionCount;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.PurchaseSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.SellerSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.SubscriptionSummary;
import static co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse.UserSummary;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerWebTest {

    @Mock
    private AdminDashboardService dashboardService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AdminDashboardController(dashboardService))
                .build();
    }

    @Test
    void publishesTheGroupedDashboardContract() throws Exception {
        AdminDashboardSummaryResponse response = new AdminDashboardSummaryResponse(
                new UserSummary(41),
                new SellerSummary(12, 9),
                new ListingSummary(125, 87),
                new ApplicationSummary(4),
                new PurchaseSummary(31, new Money(new BigDecimal("1234.50"), "USD")),
                new SubscriptionSummary(List.of(
                        new PlanSubscriptionCount("BASIC", "Basic", 2),
                        new PlanSubscriptionCount("STANDARD", "Standard", 0),
                        new PlanSubscriptionCount("PREMIUM", "Premium", 1))),
                Instant.parse("2026-08-22T08:30:00Z"));
        when(dashboardService.getSummary()).thenReturn(response);

        mockMvc.perform(get("/api/v1/admin/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users.total").value(41))
                .andExpect(jsonPath("$.sellers.total").value(12))
                .andExpect(jsonPath("$.sellers.active").value(9))
                .andExpect(jsonPath("$.listings.total").value(125))
                .andExpect(jsonPath("$.listings.publiclyAvailable").value(87))
                .andExpect(jsonPath("$.applications.pending").value(4))
                .andExpect(jsonPath("$.orders.completed").value(31))
                .andExpect(jsonPath("$.orders.completedGmv.amount").value(1234.50))
                .andExpect(jsonPath("$.orders.completedGmv.currencyCode").value("USD"))
                .andExpect(jsonPath("$.subscriptions.active").value(3))
                .andExpect(jsonPath("$.subscriptions.byPlan").isArray())
                .andExpect(jsonPath("$.subscriptions.byPlan[0].code").value("BASIC"))
                .andExpect(jsonPath("$.subscriptions.byPlan[0].displayName").value("Basic"))
                .andExpect(jsonPath("$.subscriptions.byPlan[0].count").value(2))
                .andExpect(jsonPath("$.subscriptions.byPlan[1].code").value("STANDARD"))
                .andExpect(jsonPath("$.subscriptions.byPlan[1].count").value(0))
                .andExpect(jsonPath("$.asOf").value("2026-08-22T08:30:00Z"))
                .andExpect(jsonPath("$.generatedAt").doesNotExist())
                .andExpect(jsonPath("$.activeSubscriptionsByPlan").doesNotExist());
    }
}
