package kz.vivamind.interview;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kz.vivamind.ai.dto.FollowUpType;
import kz.vivamind.ai.dto.TurnDecision;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/** Одна реплика интервью: вопрос ИИ или ответ студента. */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message {

    public enum Author {AI, STUDENT}

    public enum Kind {BASE_QUESTION, FOLLOW_UP, ANSWER, DONT_KNOW, TIMEOUT, SYSTEM}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long questionId;

    @Enumerated(EnumType.STRING)
    private Author author;

    @Enumerated(EnumType.STRING)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    private FollowUpType followUpType;

    private String text;

    /** TEXT сейчас; VOICE — фаза 2. */
    private String inputMode = "TEXT";
    /** Аудиозапись ответа — фаза 2. */
    private String audioS3Key;

    // Сигналы честности (только для ответов студента). Это сигналы, не доказательства.
    private Integer responseMs;
    private int pasteAttempts;
    private int tabSwitches;

    /** Сырой ответ LLM по этому ответу студента — для аудита. */
    @JdbcTypeCode(SqlTypes.JSON)
    private TurnDecision turnDecision;

    private Instant createdAt = Instant.now();

    public boolean fromStudent() {
        return author == Author.STUDENT;
    }
}
