package com.interviewapp.mock;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockInterviewReportRepository extends JpaRepository<MockInterviewReport, UUID> {

    Optional<MockInterviewReport> findBySessionId(UUID sessionId);
}
