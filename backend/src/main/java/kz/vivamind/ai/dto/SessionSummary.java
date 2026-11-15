package kz.vivamind.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Итоговый отчёт для студента после интервью (раздел 8.3 ТЗ). Без числовых баллов. */
public record SessionSummary(
        @NotBlank String overall,
        @NotNull List<String> strengths,
        @NotNull List<String> gaps,
        @NotNull List<Reread> reread) {

    /** Что перечитать: файл, страницы и зачем. */
    public record Reread(String source, String pages, String why) {
    }
}
