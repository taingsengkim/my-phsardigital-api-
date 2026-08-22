package co.istad.projectpracticum.phsardigital.features.admin;

import co.istad.projectpracticum.phsardigital.features.admin.dto.AdminDashboardSummaryResponse;

public interface AdminDashboardService {

    /** Builds a consistent point-in-time snapshot of the dashboard figures. */
    AdminDashboardSummaryResponse getSummary();
}
