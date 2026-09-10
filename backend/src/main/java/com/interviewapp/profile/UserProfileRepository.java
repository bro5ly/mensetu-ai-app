package com.interviewapp.profile;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {

    /** ローカル単一ユーザー前提のため、常にこれで唯一の行(あれば)を取得する。 */
    Optional<UserProfile> findFirstByOrderByCreatedAtAsc();
}
