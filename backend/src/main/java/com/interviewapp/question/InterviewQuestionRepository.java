package com.interviewapp.question;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewQuestionRepository extends JpaRepository<InterviewQuestion, UUID> {

    List<InterviewQuestion> findByCompanyIdOrderByDisplayOrderAscCreatedAtAsc(UUID companyId);

    long countByCompanyId(UUID companyId);
}
