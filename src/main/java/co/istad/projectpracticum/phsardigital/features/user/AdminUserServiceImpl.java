package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminUserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper userProfileMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminUserResponse> findAll(String query, int page, int size) {
        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        Page<UserProfile> users;
        if (query == null || query.isBlank()) {
            users = userProfileRepository.findAll(pageable);
        } else {
            String normalizedQuery = query.trim();
            users = userProfileRepository
                    .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                            normalizedQuery,
                            normalizedQuery,
                            pageable
                    );
        }

        return users.map(userProfileMapper::toAdminResponse);
    }
}
