package kz.vivamind.interview;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import kz.vivamind.ai.dto.SessionSummary;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/** Одно прохождение экзамена студентом. */
@Entity
@Table(name = "interview_sessions")
@DynamicUpdate   // обновляем только изменённые колонки: сессию параллельно трогают интервью и фоновая оценка
@Getter
@Setter
@NoArgsConstructor
public class InterviewSession {

    /**
     * IN_PROGRESS → COMPLETED (ответы даны) → AI_GRADED (черновик оценки готов) → CONFIRMED (подтвердил преподаватель).
     * ABANDONED — экзамен закрыли, пока студент не закончил.
     */
    public enum Status {IN_PROGRESS, COMPLETED, AI_GRADED, CONFIRMED, ABANDONED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long examId;
    private Long studentId;

    @Enumerated(EnumType.STRING)
    private Status status;

    /** Когда студент согласился на обработку ответов. Без согласия сессия не создаётся. */
    private Instant consentAt;

    private int currentQuestionPos;

    /** Черновой балл по оценкам ИИ. Считает ScoreCalculator, не LLM. */
    private BigDecimal aiScore;

    /** Итоговый балл. Появляется только после подтверждения преподавателем. */
    private BigDecimal finalScore;

    @JdbcTypeCode(SqlTypes.JSON)
    private SessionSummary aiSummary;

    private boolean needsReview;
    private Long reviewedBy;
    private Instant reviewedAt;
    private String teacherComment;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
}
