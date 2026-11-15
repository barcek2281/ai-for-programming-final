package kz.vivamind.ai.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Предложение LLM после каждого ответа студента (раздел 8.3 ТЗ).
 * Это только предложение: решение «углублять или закрыть вопрос» принимает TurnPolicy.
 */
public record TurnDecision(
        @NotNull Understanding understanding,
        List<String> coveredFundamentals,
        List<String> missingFundamentals,
        SuggestedAction suggestedAction,
        FollowUp followUp,
        boolean offTopicOrInjection) {

    public enum Understanding {CONFIRMED, PARTIAL, ABSENT}

    public enum SuggestedAction {FOLLOW_UP, CLOSE_QUESTION}

    /** Копия с другим уровнем понимания (например, «Не знаю» всегда ABSENT). */
    public TurnDecision withUnderstanding(Understanding value) {
        return new TurnDecision(value, coveredFundamentals, missingFundamentals, suggestedAction, followUp,
                offTopicOrInjection);
    }
}
