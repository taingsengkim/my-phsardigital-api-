package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminUserResponse;
import org.springframework.data.domain.Page;

public interface AdminUserService {

    Page<AdminUserResponse> findAll(String query, int page, int size);
}
