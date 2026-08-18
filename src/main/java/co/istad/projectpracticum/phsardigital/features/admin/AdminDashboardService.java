package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse;

public interface AdminDashboardService {

    /** Counts every dashboard figure, live. */
    AdminDashboardSummaryResponse getSummary();
}
