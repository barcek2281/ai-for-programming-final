package kz.vivamind.grading;

import kz.vivamind.interview.SessionQuestion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Балл считает Java, не LLM (раздел 9 ТЗ):
 * aiScore    = mean(ai_solo_level) / 4 × 100
 * finalScore = mean(final_solo_level ?? ai_solo_level) / 4 × 100, округление до 0.1
 */
public final class ScoreCalculator {

    private static final int MAX_SOLO_LEVEL = 4;

    private ScoreCalculator() {
    }

    public static BigDecimal aiScore(List<SessionQuestion> questions) {
        return score(questions.stream().map(SessionQuestion::getAiSoloLevel).toList());
    }

    public static BigDecimal finalScore(List<SessionQuestion> questions) {
        return score(questions.stream().map(SessionQuestion::effectiveLevel).toList());
    }

    /** Балл 0–100 по списку уровней SOLO. Вопросы без уровня не учитываются; если уровней нет — null. */
    public static BigDecimal score(List<Integer> levels) {
        OptionalDouble mean = mean(levels);
        return mean.isEmpty() ? null : round(mean.getAsDouble() / MAX_SOLO_LEVEL * 100);
    }

    /** Средний уровень SOLO (0–4) — для сводки по темам курса. */
    public static BigDecimal averageLevel(List<Integer> levels) {
        OptionalDouble mean = mean(levels);
        return mean.isEmpty() ? null : round(mean.getAsDouble());
    }

    private static OptionalDouble mean(List<Integer> levels) {
        return levels.stream().filter(Objects::nonNull).mapToInt(Integer::intValue).average();
    }

    private static BigDecimal round(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }
}
