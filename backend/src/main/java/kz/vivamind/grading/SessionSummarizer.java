package kz.vivamind.grading;

import kz.vivamind.ai.AiMessages;
import kz.vivamind.ai.AiMessages.GradedQuestion;
import kz.vivamind.ai.AiService;
import kz.vivamind.ai.dto.QuestionGrade;
import kz.vivamind.ai.dto.SessionSummary;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.common.Languages;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.course.CourseTopicRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSessionRepository;
import kz.vivamind.interview.SessionQuestion;
import kz.vivamind.interview.SessionQuestionRepository;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Завершение ИИ-оценки сессии: когда оценены все вопросы, LLM пишет текстовый отчёт (без баллов),
 * ScoreCalculator считает черновой балл, сессия переходит в AI_GRADED.
 */
@Service
public class SessionSummarizer {

    private static final Logger log = LoggerFactory.getLogger(SessionSummarizer.class);
    private static final int REFERENCES_PER_GAP = 2;

    /** Вопросы оцениваются параллельно, а итог по сессии должен собраться один раз. */
    private final Object[] locks = new Object[32];

    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository questions;
    private final ExamRepository exams;
    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final QuestionCardRepository cards;
    private final RetrievalService retrieval;
    private final AiService ai;
    private final TransactionTemplate tx;

    public SessionSummarizer(InterviewSessionRepository sessions, SessionQuestionRepository questions,
                             ExamRepository exams, CourseRepository courses, CourseTopicRepository topics,
                             QuestionCardRepository cards, RetrievalService retrieval, AiService ai,
                             TransactionTemplate tx) {
        this.sessions = sessions;
        this.questions = questions;
        this.exams = exams;
        this.courses = courses;
        this.topics = topics;
        this.cards = cards;
        this.retrieval = retrieval;
        this.ai = ai;
        this.tx = tx;
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
    }

    public void finalizeIfReady(Long sessionId) {
        synchronized (locks[(int) (sessionId % locks.length)]) {
            InterviewSession session = sessions.findById(sessionId).orElse(null);
            if (session == null || session.getStatus() != InterviewSession.Status.COMPLETED) {
                return;
            }
            List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(sessionId);
            if (all.stream().anyMatch(q -> q.getAiGrade() == null)) {
                return;                                        // ещё не все вопросы оценены
            }

            SessionSummary summary = null;
            try {
                summary = summarize(session, all);
            } catch (RuntimeException e) {
                log.warn("AI summary failed for session {}: {}", sessionId, e.getClass().getSimpleName());
            }
            SessionSummary finalSummary = summary;
            BigDecimal aiScore = ScoreCalculator.aiScore(all);

            tx.executeWithoutResult(status -> {
                InterviewSession locked = sessions.findByIdForUpdate(sessionId).orElseThrow();
                if (locked.getStatus() != InterviewSession.Status.COMPLETED) {
                    return;
                }
                locked.setAiSummary(finalSummary);
                locked.setAiScore(aiScore);
                if (finalSummary == null) {
                    locked.setNeedsReview(true);
                }
                locked.setStatus(InterviewSession.Status.AI_GRADED);
                sessions.save(locked);
            });
        }
    }

    private SessionSummary summarize(InterviewSession session, List<SessionQuestion> all) {
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        Course course = courses.findById(exam.getCourseId()).orElseThrow();

        List<GradedQuestion> graded = new ArrayList<>();
        Map<String, Excerpt> references = new LinkedHashMap<>();     // id фрагмента → фрагмент, без повторов
        for (SessionQuestion question : all) {
            QuestionCard card = cards.findById(question.getCardId()).orElseThrow();
            StoredGrade grade = question.getAiGrade();
            String topicTitle = topics.findById(card.getTopicId()).map(t -> t.getTitle()).orElse("");
            List<String> covered = grade.evidence().stream().map(QuestionGrade.Evidence::fundamental)
                    .filter(f -> f != null && !f.isBlank()).distinct().toList();
            graded.add(new GradedQuestion(topicTitle, card.getQuestion(), grade.soloLevel(), grade.rationale(),
                    covered, grade.missingFundamentals()));

            // Для каждого пробела подбираем, что перечитать, — из материалов темы этого вопроса.
            if (!grade.missingFundamentals().isEmpty()) {
                String query = card.getQuestion() + "\n" + String.join("\n", grade.missingFundamentals());
                for (Excerpt excerpt : retrieval.search(card.getTopicId(), query, REFERENCES_PER_GAP)) {
                    references.putIfAbsent(excerpt.id(), excerpt);
                }
            }
        }

        List<Excerpt> refs = List.copyOf(references.values());
        SessionSummary summary = ai.summarize(Languages.promptName(course.getLanguage()),
                AiMessages.summary(graded, refs), session.getId());

        // «Что перечитать» — только файлы, которые действительно есть в материалах курса.
        Set<String> knownFiles = refs.stream().map(r -> r.fileName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        List<SessionSummary.Reread> reread = summary.reread().stream()
                .filter(r -> r != null && r.source() != null && knownFiles.contains(r.source().toLowerCase(Locale.ROOT)))
                .toList();
        return new SessionSummary(summary.overall(), summary.strengths(), summary.gaps(), reread);
    }
}
