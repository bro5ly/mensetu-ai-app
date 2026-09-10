package com.interviewapp.profile;

import com.interviewapp.profile.UserProfileDtos.UpdateUserProfileRequest;
import com.interviewapp.profile.UserProfileDtos.UserProfileResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** サイドバー左下の「設定」から編集する、ユーザープロフィール(履歴書のような参考情報)。 */
@RestController
@RequestMapping("/api/profile")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    public UserProfileResponse get() {
        return service.get();
    }

    @PutMapping
    public UserProfileResponse update(@RequestBody UpdateUserProfileRequest request) {
        return service.update(request);
    }
}
