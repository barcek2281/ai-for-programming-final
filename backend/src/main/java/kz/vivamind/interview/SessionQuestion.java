package kz.vivamind.interview;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kz.vivamind.grading.StoredGrade;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/** Вопрос внутри сессии: базовый вопрос карточки + углубления по нему. */
@Entity
@Table(name = "session_questions")
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor
public class SessionQuestion {

    public enum State {PENDING, ACTIVE, CLOSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long sessionId;
    private Long cardId;
    private int position;

    @Enumerated(EnumType.STRING)
    private State state;

    private int followupsAsked;
    /** Сколько раз подряд понимание было ABSENT. Два подряд закрывают вопрос. */
    private int absentStreak;

    /** Уровень SOLO 0..4 по мнению ИИ. */
    private Integer aiSoloLevel;
    private BigDecimal aiConfidence;

    @JdbcTypeCode(SqlTypes.JSON)
    private StoredGrade aiGrade;

    /** Итоговый уровень, если преподаватель изменил оценку ИИ. */
    private Integer finalSoloLevel;
    private String teacherComment;

    /** Уровень, который идёт в итог: преподавателя, а если он не менял — ИИ. */
    public Integer effectiveLevel() {
        return finalSoloLevel != null ? finalSoloLevel : aiSoloLevel;
    }
}
