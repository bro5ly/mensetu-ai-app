package com.interviewapp.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {

    Optional<ChatSession> findByQuestionIdAndMode(UUID questionId, SessionMode mode);

    List<ChatSession> findByCompanyIdAndModeOrderByCreatedAtDesc(UUID companyId, SessionMode mode);

    List<ChatSession> findByQuestionIdAndModeOrderByCreatedAtDesc(UUID questionId, SessionMode mode);
}
