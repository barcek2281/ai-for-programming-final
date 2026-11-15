package kz.vivamind.grading;

import kz.vivamind.ai.dto.QuestionGrade;

import java.util.List;

/**
 * То, что лежит в session_questions.ai_grade: QuestionGrade целиком (после проверки цитат)
 * плюс служебные поля — сколько цитат удалено и почему вопрос помечен на проверку.
 */
public record StoredGrade(
        Integer soloLevel,
        Double confidence,
        String rationale,
        List<QuestionGrade.Evidence> evidence,
        List<String> missingFundamentals,
        String studentFeedback,
        int removedQuotes,
        boolean needsReview,
        List<String> reviewReasons,
        boolean failed) {

    /** ИИ не смог оценить вопрос — преподаватель ставит уровень сам. */
    public static StoredGrade failure() {
        return new StoredGrade(null, null, null, List.of(), List.of(), null, 0, true,
                List.of("ИИ-оценка не удалась — поставьте уровень вручную"), true);
    }
}
