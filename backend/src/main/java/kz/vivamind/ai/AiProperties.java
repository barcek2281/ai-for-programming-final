package kz.vivamind.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Слаги чат-моделей OpenRouter берутся из .env (LLM_FAST_MODEL, LLM_STRONG_MODEL), в коде их нет. */
@ConfigurationProperties("vivamind.ai")
public record AiProperties(String fastModel, String strongModel) {
}
