package com.interviewapp.profile;

import java.time.Instant;

public final class UserProfileDtos {

    private UserProfileDtos() {
    }

    /** {@code resumeText} は未登録なら空文字("")を返す(nullではなくフロントが常に文字列として扱えるように)。 */
    public record UserProfileResponse(String resumeText, Instant updatedAt) {

        public static UserProfileResponse from(UserProfile profile) {
            if (profile == null) {
                return new UserProfileResponse("", null);
            }
            return new UserProfileResponse(
                    profile.getResumeText() == null ? "" : profile.getResumeText(), profile.getUpdatedAt());
        }
    }

    public record UpdateUserProfileRequest(String resumeText) {
    }
}
