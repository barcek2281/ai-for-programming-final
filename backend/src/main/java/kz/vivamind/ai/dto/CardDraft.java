package kz.vivamind.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ответ LLM при генерации карточки вопроса (раздел 8.3 ТЗ). */
public record CardDraft(
        @NotBlank String question,
        @NotNull @Size(min = 3, max = 5) List<@NotBlank String> fundamentals,
        @NotNull List<String> misconceptions,
        @NotNull List<FollowUp> followUpIdeas,
        @NotNull @Valid Rubric rubric,
        @NotNull List<String> sourceChunkIds) {

    /** Рубрика SOLO 0–4 для конкретного вопроса: {"0": "...", ..., "4": "..."}. */
    public record Rubric(
            @JsonProperty("0") @NotBlank String level0,
            @JsonProperty("1") @NotBlank String level1,
            @JsonProperty("2") @NotBlank String level2,
            @JsonProperty("3") @NotBlank String level3,
            @JsonProperty("4") @NotBlank String level4) {

        public Map<String, String> toMap() {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("0", level0);
            map.put("1", level1);
            map.put("2", level2);
            map.put("3", level3);
            map.put("4", level4);
            return map;
        }
    }
}
