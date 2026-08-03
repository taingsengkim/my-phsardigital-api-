package co.istad.projectpracticum.phsardigital.features.user;

import co.istad.projectpracticum.phsardigital.features.user.dto.AdminUserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileMapper userProfileMapper;

    private AdminUserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AdminUserServiceImpl(userProfileRepository, userProfileMapper);
    }

    @Test
    void listsNewestProfilesFirstWithBoundedPagination() {
        UserProfile profile = profile("user-1", "sokha@example.com");
        AdminUserResponse response = response(profile);
        when(userProfileRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        when(userProfileMapper.toAdminResponse(profile)).thenReturn(response);

        var result = service.findAll(null, 1, 20);

        assertThat(result.getContent()).containsExactly(response);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userProfileRepository).findAll(pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("createdAt").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        verify(userProfileRepository, never())
                .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        any(), any(), any());
    }

    @Test
    void searchesEmailAndFullNameWithTrimmedQuery() {
        UserProfile profile = profile("user-2", "dara@example.com");
        AdminUserResponse response = response(profile);
        when(userProfileRepository
                .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(profile)));
        when(userProfileMapper.toAdminResponse(profile)).thenReturn(response);

        var result = service.findAll("  dara  ", 0, 10);

        assertThat(result.getContent()).containsExactly(response);
        verify(userProfileRepository)
                .findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(
                        org.mockito.ArgumentMatchers.eq("dara"),
                        org.mockito.ArgumentMatchers.eq("dara"),
                        any(Pageable.class)
                );
    }

    private UserProfile profile(String id, String email) {
        UserProfile profile = new UserProfile(id);
        profile.setEmail(email);
        profile.setFullName("Sokha Chan");
        profile.setStatus(UserStatus.ACTIVE);
        profile.setCreatedAt(LocalDateTime.now());
        profile.setLastModifiedAt(LocalDateTime.now());
        return profile;
    }

    private AdminUserResponse response(UserProfile profile) {
        return new AdminUserResponse(
                profile.getId(),
                profile.getEmail(),
                profile.getFullName(),
                profile.getPhone(),
                profile.getAvatarUrl(),
                profile.getStatus(),
                profile.getDateOfBirth(),
                profile.getCreatedAt(),
                profile.getLastModifiedAt()
        );
    }
}
