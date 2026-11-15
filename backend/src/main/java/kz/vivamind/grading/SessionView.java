package kz.vivamind.grading;

import kz.vivamind.ai.dto.FollowUpType;
import kz.vivamind.appeal.Appeal;
import kz.vivamind.appeal.AppealRepository;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.course.CourseTopicRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.Message;
import kz.vivamind.interview.MessageRepository;
import kz.vivamind.interview.SessionQuestion;
import kz.vivamind.interview.SessionQuestionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Собирает всё о сессии в один объект: вопросы, сгруппированные по темам курса, транскрипт и сигналы.
 * Используется и экраном проверки преподавателя, и отчётом студента.
 */
@Component
public class SessionView {

    /** Сигналы честности. Это сигналы, не доказательства; на балл автоматически не влияют. */
    public record Signals(int pasteAttempts, int tabSwitches, int timeouts) {
    }

    public record TranscriptMessage(Long id, Message.Author author, Message.Kind kind, FollowUpType followUpType,
                                    String text, Integer responseMs, int pasteAttempts, int tabSwitches,
                                    boolean flagged, Instant createdAt) {
    }

    public record AppealDto(Long id, Long sessionId, String reason, Appeal.Status status, String resolution,
                            Instant createdAt, Instant resolvedAt) {
        public static AppealDto of(Appeal a) {
            return a == null ? null : new AppealDto(a.getId(), a.getSessionId(), a.getReason(), a.getStatus(),
                    a.getResolution(), a.getCreatedAt(), a.getResolvedAt());
        }
    }

    /** Вопрос сессии вместе с карточкой и репликами. */
    public record QuestionData(SessionQuestion question, QuestionCard card, List<Message> messages) {
    }

    /** Тема курса и вопросы сессии по ней. */
    public record TopicData(CourseTopic topic, List<QuestionData> questions) {
        public BigDecimal averageLevel() {
            return ScoreCalculator.averageLevel(questions.stream().map(q -> q.question().effectiveLevel()).toList());
        }
    }

    public record Data(InterviewSession session, Exam exam, Course course, List<TopicData> topics,
                       List<SessionQuestion> questions, Signals signals, Appeal appeal) {
    }

    private final ExamRepository exams;
    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final QuestionCardRepository cards;
    private final SessionQuestionRepository questions;
    private final MessageRepository messages;
    private final AppealRepository appeals;

    public SessionView(ExamRepository exams, CourseRepository courses, CourseTopicRepository topics,
                       QuestionCardRepository cards, SessionQuestionRepository questions,
                       MessageRepository messages, AppealRepository appeals) {
        this.exams = exams;
        this.courses = courses;
        this.topics = topics;
        this.cards = cards;
        this.questions = questions;
        this.messages = messages;
        this.appeals = appeals;
    }

    public Data load(InterviewSession session) {
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(session.getId());
        List<Message> history = messages.findBySessionIdOrderByIdAsc(session.getId());

        // Группируем вопросы по темам курса, темы — в порядке курса.
        Map<Long, List<QuestionData>> byTopic = new LinkedHashMap<>();
        for (SessionQuestion question : all) {
            QuestionCard card = cards.findById(question.getCardId()).orElseThrow();
            List<Message> ofQuestion = history.stream()
                    .filter(m -> question.getId().equals(m.getQuestionId())).toList();
            byTopic.computeIfAbsent(card.getTopicId(), t -> new ArrayList<>())
                    .add(new QuestionData(question, card, ofQuestion));
        }
        List<TopicData> topicBlocks = new ArrayList<>();
        for (CourseTopic topic : topics.findAllById(byTopic.keySet())) {
            topicBlocks.add(new TopicData(topic, byTopic.get(topic.getId())));
        }
        topicBlocks.sort(Comparator.comparingInt((TopicData t) -> t.topic().getPosition())
                .thenComparing(t -> t.topic().getId()));

        Appeal appeal = appeals.findFirstBySessionIdOrderByIdDesc(session.getId()).orElse(null);
        return new Data(session, exam, course, topicBlocks, all, signals(history), appeal);
    }

    public static Signals signals(List<Message> history) {
        int paste = 0;
        int tabs = 0;
        int timeouts = 0;
        for (Message m : history) {
            if (m.fromStudent()) {
                paste += m.getPasteAttempts();
                tabs += m.getTabSwitches();
                if (m.getKind() == Message.Kind.TIMEOUT) {
                    timeouts++;
                }
            }
        }
        return new Signals(paste, tabs, timeouts);
    }

    public static TranscriptMessage toTranscript(Message m) {
        boolean flagged = m.getTurnDecision() != null && m.getTurnDecision().offTopicOrInjection();
        return new TranscriptMessage(m.getId(), m.getAuthor(), m.getKind(), m.getFollowUpType(), m.getText(),
                m.getResponseMs(), m.getPasteAttempts(), m.getTabSwitches(), flagged, m.getCreatedAt());
    }
}
