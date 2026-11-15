package kz.vivamind.exam;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

/** Экзамен по одной или нескольким темам курса. Студент входит по access_code. */
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
public class Exam {

    public enum Mode {PRACTICE, EXAM}

    public enum Status {DRAFT, PUBLISHED, CLOSED}

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long courseId;
    private String title;

    @Enumerated(EnumType.STRING)
    private Mode mode;

    private int questionsCount = 4;
    private int minFollowups = 1;
    private int maxFollowups = 3;
    private int answerTimeLimitSec = 120;
    private String accessCode;

    /** true — всем студентам одни и те же вопросы; false — у каждого свой случайный набор. */
    private boolean sameQuestions;

    @Enumerated(EnumType.STRING)
    private Status status;

    private Instant createdAt = Instant.now();

    /** Темы курса, которые покрывает экзамен (таблица exam_topics). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "exam_topics", joinColumns = @JoinColumn(name = "exam_id"))
    @Column(name = "topic_id")
    private Set<Long> topicIds = new LinkedHashSet<>();
}
