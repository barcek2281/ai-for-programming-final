package kz.vivamind.common;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Аудит вызовов LLM: модель, токены, время. Содержимое запросов и ответов НЕ хранится. */
@Entity
@Table(name = "llm_calls")
@Getter
@Setter
@NoArgsConstructor
public class LlmCallLog {

    public enum Purpose {CARD_GEN, TURN, QUESTION_GRADE, SUMMARY, EMBED, STT}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private Purpose purpose;

    private String model;
    private Long sessionId;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer latencyMs;
    private boolean ok;
    private String error;
    private Instant createdAt = Instant.now();
}
