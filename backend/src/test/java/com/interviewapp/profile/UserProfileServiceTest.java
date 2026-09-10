package com.interviewapp.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.interviewapp.profile.UserProfileDtos.UpdateUserProfileRequest;
import com.interviewapp.profile.UserProfileDtos.UserProfileResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserProfileRepository repository;

    private UserProfileService service;

    @BeforeEach
    void setUp() {
        service = new UserProfileService(repository);
    }

    @Test
    void 未登録なら空文字のresumeTextを返す() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());

        UserProfileResponse response = service.get();

        assertThat(response.resumeText()).isEmpty();
        assertThat(response.updatedAt()).isNull();
    }

    @Test
    void 登録済みならresumeTextを返す() {
        UserProfile profile = new UserProfile("〇〇大学〇〇学部。強みは継続力。");
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(profile));

        UserProfileResponse response = service.get();

        assertThat(response.resumeText()).isEqualTo("〇〇大学〇〇学部。強みは継続力。");
    }

    @Test
    void 未登録への更新は新規作成する() {
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        UserProfileResponse response = service.update(new UpdateUserProfileRequest("新しい経歴"));

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getResumeText()).isEqualTo("新しい経歴");
        assertThat(response.resumeText()).isEqualTo("新しい経歴");
    }

    @Test
    void 登録済みへの更新は既存行を上書きする() {
        UserProfile existing = new UserProfile("旧い経歴");
        when(repository.findFirstByOrderByCreatedAtAsc()).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(new UpdateUserProfileRequest("更新後の経歴"));

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(saved.getValue().getResumeText()).isEqualTo("更新後の経歴");
    }

    @Test
    void getResumeTextOrNullは未登録や空白ならnull() {
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new UserProfile("   ")))
                .thenReturn(Optional.of(new UserProfile(null)));

        assertThat(service.getResumeTextOrNull()).isNull();
        assertThat(service.getResumeTextOrNull()).isNull();
        assertThat(service.getResumeTextOrNull()).isNull();
    }

    @Test
    void getResumeTextOrNullは登録済みならトリムして返す() {
        when(repository.findFirstByOrderByCreatedAtAsc())
                .thenReturn(Optional.of(new UserProfile("  経歴テキスト  ")));

        assertThat(service.getResumeTextOrNull()).isEqualTo("経歴テキスト");
    }
}
