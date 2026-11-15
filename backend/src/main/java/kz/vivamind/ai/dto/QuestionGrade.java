package kz.vivamind.ai.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Оценка LLM за один вопрос по таксономии SOLO (раздел 8.3 ТЗ). Это черновик для преподавателя. */
public record QuestionGrade(
        @NotNull @Min(0) @Max(4) Integer soloLevel,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double confidence,
        @NotBlank String rationale,
        @NotNull List<Evidence> evidence,
        @NotNull List<String> missingFundamentals,
        @NotBlank String studentFeedback) {

    /** Дословная цитата из ответа студента и ключевая идея, которую она подтверждает. */
    public record Evidence(String quote, String fundamental) {
    }
}
