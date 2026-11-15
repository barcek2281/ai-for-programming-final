package kz.vivamind.grading;

import kz.vivamind.ai.dto.QuestionGrade;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Проверка цитат-доказательств (раздел 9 ТЗ). Каждая цитата обязана быть подстрокой ответов студента
 * (после нормализации пробелов и регистра). Выдуманная цитата удаляется, уверенность падает на 0.2,
 * вопрос помечается на проверку преподавателем.
 */
@Component
public class EvidenceValidator {

    private static final double PENALTY_PER_REMOVED_QUOTE = 0.2;
    private static final double REVIEW_CONFIDENCE_THRESHOLD = 0.6;

    public StoredGrade validate(QuestionGrade grade, List<String> studentMessages) {
        List<String> normalizedMessages = studentMessages.stream().map(EvidenceValidator::normalize).toList();

        List<QuestionGrade.Evidence> kept = new ArrayList<>();
        int removed = 0;
        for (QuestionGrade.Evidence evidence : grade.evidence()) {
            String quote = evidence == null || evidence.quote() == null ? "" : normalize(evidence.quote());
            boolean found = !quote.isEmpty() && normalizedMessages.stream().anyMatch(m -> m.contains(quote));
            if (found) {
                kept.add(evidence);
            } else {
                removed++;
            }
        }

        double confidence = Math.max(0.0, grade.confidence() - PENALTY_PER_REMOVED_QUOTE * removed);
        confidence = BigDecimal.valueOf(confidence).setScale(2, RoundingMode.HALF_UP).doubleValue();

        List<String> reasons = new ArrayList<>();
        if (removed > 0) {
            reasons.add("Удалено цитат, которых нет в ответах студента: " + removed);
        }
        if (confidence < REVIEW_CONFIDENCE_THRESHOLD) {
            reasons.add("Низкая уверенность ИИ");
        }
        if (kept.isEmpty()) {
            reasons.add("Нет ни одной подтверждённой цитаты");
        }
        return new StoredGrade(grade.soloLevel(), confidence, grade.rationale(), kept,
                grade.missingFundamentals(), grade.studentFeedback(), removed, !reasons.isEmpty(), reasons, false);
    }

    /** Регистр и пробелы не важны; кавычки и многоточия по краям цитаты — тоже. */
    static String normalize(String text) {
        String s = text.replace("&lt;", "<").replace("&gt;", ">")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
        return s.replaceAll("^[\"'«»“”„…. ]+|[\"'«»“”„… ]+$", "");
    }
}
