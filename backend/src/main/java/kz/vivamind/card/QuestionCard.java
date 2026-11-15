package kz.vivamind.card;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kz.vivamind.ai.dto.FollowUp;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Карточка вопроса: базовый вопрос + всё, что нужно ИИ, чтобы вести углубления и оценивать.
 * Черновик создаёт LLM по цели обучения, преподаватель правит и утверждает.
 */
@Entity
@Table(name = "question_cards")
@Getter
@Setter
@NoArgsConstructor
public class QuestionCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private Long topicId;
    private Long outcomeId;

    private String question;

    /** 3–5 ключевых идей, которые студент должен объяснить. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> fundamentals;

    /** Типичные ошибки. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> misconceptions;

    /** Идеи углубляющих вопросов (запасной вариант, если LLM не предложила свой). */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<FollowUp> followUpIdeas;

    /** Рубрика SOLO для этого вопроса: {"0": "...", ..., "4": "..."}. */
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, String> rubric;

    /** id фрагментов vector_store, по которым карточка сгенерирована. */
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> sourceChunkIds;

    @Enumerated(EnumType.STRING)
    private CardStatus status;

    private boolean aiGenerated = true;
    private Long updatedBy;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();
}
