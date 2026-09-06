package com.interviewapp.company;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 質問生成プロンプトに手本として渡す質問バンクのサンプルを組み立てる。
 * カテゴリごとに均等に少数件だけ選び、プロンプトの肥大化を防ぐ。
 */
@Service
public class QuestionBankService {

    /** {@code docs/interview_app_db_api_design.md} に定義された内部カテゴリ。 */
    private static final List<String> CATEGORIES =
            List.of("SELF_PR", "MOTIVATION", "EXPERIENCE", "REVERSE_QUESTION");

    private static final int PER_CATEGORY = 2;

    private final QuestionBankRepository repository;

    public QuestionBankService(QuestionBankRepository repository) {
        this.repository = repository;
    }

    /** カテゴリごとに最大 {@value #PER_CATEGORY} 件、登録順(先頭)からバランスよく取り出す。 */
    public List<QuestionBankEntry> sampleForPrompt() {
        List<QuestionBankEntry> sample = new ArrayList<>();
        for (String category : CATEGORIES) {
            repository.findByInternalCategoryOrderByCreatedAtAsc(category).stream()
                    .limit(PER_CATEGORY)
                    .forEach(sample::add);
        }
        return sample;
    }
}
