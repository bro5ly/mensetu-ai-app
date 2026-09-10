package com.interviewapp.profile;

import com.interviewapp.profile.UserProfileDtos.UpdateUserProfileRequest;
import com.interviewapp.profile.UserProfileDtos.UserProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * ユーザープロフィール(履歴書のような参考情報)の取得・更新。
 *
 * <p>ローカル単一ユーザー前提のため行は高々1つで、更新は「あれば上書き、無ければ作成」の
 * upsertにする(会社のようにIDで複数管理する必要が無いため、専用のupdateだけを公開する)。</p>
 */
@Service
@Transactional
public class UserProfileService {

    private final UserProfileRepository repository;

    public UserProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse get() {
        return UserProfileResponse.from(repository.findFirstByOrderByCreatedAtAsc().orElse(null));
    }

    public UserProfileResponse update(UpdateUserProfileRequest request) {
        UserProfile profile = repository.findFirstByOrderByCreatedAtAsc().orElseGet(UserProfile::new);
        profile.setResumeText(request.resumeText());
        // saveAndFlushで即座にUPDATE/INSERTを発行させる。@Transactionalメソッド内でsave()だけを
        // 呼ぶと実際のSQL発行(と@UpdateTimestamp/@CreationTimestampの値の確定)はトランザクション
        // コミット時まで遅延されるため、この時点で返すDTOのupdatedAtがnull(新規作成時)や更新前の
        // 古い値(更新時)のままになってしまう(CompanyService.createと同じ理由でflushする)。
        return UserProfileResponse.from(repository.saveAndFlush(profile));
    }

    /**
     * プロンプトに埋め込む用に候補者情報のテキストを返す。未登録/空文字なら null
     * (呼び出し側はnullなら「候補者情報」ブロック自体を「登録されていません」表示にする)。
     */
    @Transactional(readOnly = true)
    public String getResumeTextOrNull() {
        String text = repository.findFirstByOrderByCreatedAtAsc()
                .map(UserProfile::getResumeText)
                .orElse(null);
        return StringUtils.hasText(text) ? text.trim() : null;
    }
}
