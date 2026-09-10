package com.interviewapp.mock;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MockQuestionFeedbackRepository extends JpaRepository<MockQuestionFeedback, UUID> {

    List<MockQuestionFeedback> findByReportIdOrderBySequenceNoAsc(UUID reportId);
}
