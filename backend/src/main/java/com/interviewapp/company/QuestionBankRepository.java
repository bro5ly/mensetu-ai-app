package com.interviewapp.company;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionBankRepository extends JpaRepository<QuestionBankEntry, UUID> {

    List<QuestionBankEntry> findByInternalCategoryOrderByCreatedAtAsc(String internalCategory);
}
