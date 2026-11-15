package kz.vivamind.grading;

import kz.vivamind.ai.AiMessages;
import kz.vivamind.ai.AiService;
import kz.vivamind.ai.dto.QuestionGrade;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.common.Languages;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSessionRepository;
import kz.vivamind.interview.Message;
import kz.vivamind.interview.MessageRepository;
import kz.vivamind.interview.SessionQuestion;
import kz.vivamind.interview.SessionQuestionRepository;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Фоновая оценка закрытого вопроса по SOLO: LLM → проверка цитат → сохранение.
 * Это черновик: итоговый уровень и балл подтверждает преподаватель.
 */
@Service
public class QuestionGrader {

    private static final Logger log = LoggerFactory.getLogger(QuestionGrader.class);
    private static final int EXCERPTS_FOR_GRADE = 3;

    private final SessionQuestionRepository questions;
    private final InterviewSessionRepository sessions;
    private final MessageRepository messages;
    private final ExamRepository exams;
    private final CourseRepository courses;
    private final QuestionCardRepository cards;
    private final RetrievalService retrieval;
    private final AiService ai;
    private final EvidenceValidator evidenceValidator;
    private final SessionSummarizer summarizer;

    public QuestionGrader(SessionQuestionRepository questions, InterviewSessionRepository sessions,
                          MessageRepository messages, ExamRepository exams, CourseRepository courses,
                          QuestionCardRepository cards, RetrievalService retrieval, AiService ai,
                          EvidenceValidator evidenceValidator, SessionSummarizer summarizer) {
        this.questions = questions;
        this.sessions = sessions;
        this.messages = messages;
        this.exams = exams;
        this.courses = courses;
        this.cards = cards;
        this.retrieval = retrieval;
        this.ai = ai;
        this.evidenceValidator = evidenceValidator;
        this.summarizer = summarizer;
    }

    /** Вызывается после закрытия вопроса. Когда оценены все вопросы завершённой сессии — готовит итог. */
    @Async
    public void gradeAsync(Long questionId) {
        try {
            Long sessionId = grade(questionId);
            if (sessionId != null) {
                summarizer.finalizeIfReady(sessionId);
            }
        } catch (RuntimeException e) {
            log.error("Background grading of question {} failed", questionId, e);
        }
    }

    /** Повторная оценка вопросов, которые ИИ не смог оценить, и новая сборка итога по сессии. */
    @Async
    public void regradeAsync(Long sessionId, List<Long> questionIds) {
        try {
            questionIds.forEach(this::grade);
            summarizer.finalizeIfReady(sessionId);
        } catch (RuntimeException e) {
            log.error("Background regrading of session {} failed", sessionId, e);
        }
    }

    /** Оценивает один вопрос и возвращает id его сессии. */
    public Long grade(Long questionId) {
        SessionQuestion question = questions.findById(questionId).orElse(null);
        if (question == null) {
            return null;
        }
        InterviewSession session = sessions.findById(question.getSessionId()).orElseThrow();
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        QuestionCard card = cards.findById(question.getCardId()).orElseThrow();
        List<Message> dialogue = messages.findByQuestionIdOrderByIdAsc(questionId);

        StoredGrade stored;
        try {
            List<Excerpt> excerpts = retrieval.search(card.getTopicId(), card.getQuestion(), EXCERPTS_FOR_GRADE);
            QuestionGrade grade = ai.gradeQuestion(Languages.promptName(course.getLanguage()),
                    AiMessages.questionGrade(card, excerpts, dialogue), session.getId());
            List<String> studentMessages = dialogue.stream().filter(Message::fromStudent).map(Message::getText).toList();
            stored = evidenceValidator.validate(grade, studentMessages);
        } catch (RuntimeException e) {
            log.warn("AI grading failed for question {}: {}", questionId, e.getClass().getSimpleName());
            stored = StoredGrade.failure();
        }

        question.setAiSoloLevel(stored.soloLevel());
        question.setAiConfidence(stored.confidence() == null ? null
                : BigDecimal.valueOf(stored.confidence()).setScale(2, RoundingMode.HALF_UP));
        question.setAiGrade(stored);
        questions.save(question);

        if (stored.needsReview()) {
            sessions.markNeedsReview(session.getId());
        }
        return session.getId();
    }
}
