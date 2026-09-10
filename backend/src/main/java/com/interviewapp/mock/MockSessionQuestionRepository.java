package com.interviewapp.mock;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockSessionQuestionRepository extends JpaRepository<MockSessionQuestion, UUID> {

    List<MockSessionQuestion> findBySessionIdOrderByDisplayOrderAsc(UUID sessionId);
}
