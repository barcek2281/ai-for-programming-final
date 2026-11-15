package kz.vivamind.grading;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import kz.vivamind.ai.dto.SessionSummary;
import kz.vivamind.appeal.Appeal;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.auth.User;
import kz.vivamind.auth.UserRepository;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.CourseAccess;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.exam.ExamService;
import kz.vivamind.grading.SessionView.AppealDto;
import kz.vivamind.grading.SessionView.Signals;
import kz.vivamind.grading.SessionView.TranscriptMessage;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSessionRepository;
import kz.vivamind.interview.SessionQuestion;
import kz.vivamind.interview.SessionQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Человек в контуре: преподаватель видит транскрипт, черновик оценки ИИ и сигналы,
 * может изменить уровень по любому вопросу и подтверждает итог. Без подтверждения итогового балла нет.
 */
@Service
public class ReviewService {

    public record TopicLevel(Long topicId, String title, BigDecimal avgLevel) {
    }

    /** Строка таблицы результатов экзамена. */
    public record SessionRow(Long id, String studentName, String studentEmail, InterviewSession.Status status,
                             BigDecimal aiScore, BigDecimal finalScore, boolean needsReview, Signals signals,
                             List<TopicLevel> topicLevels, boolean openAppeal, Instant startedAt,
                             Instant finishedAt) {
    }

    public record QuestionReview(Long id, int position, String question, List<String> fundamentals,
                                 Map<String, String> rubric, SessionQuestion.State state, int followupsAsked,
                                 Integer aiSoloLevel, BigDecimal aiConfidence, StoredGrade grade,
                                 Integer finalSoloLevel, String teacherComment, List<TranscriptMessage> messages) {
    }

    public record TopicReview(Long topicId, String title, BigDecimal avgLevel, List<QuestionReview> questions) {
    }

    public record ReviewDto(Long sessionId, Long examId, String examTitle, Long courseId, String courseTitle,
                            Exam.Mode mode, InterviewSession.Status status, String studentName,
                            String studentEmail, BigDecimal aiScore, BigDecimal finalScore,
                            BigDecimal previewScore, boolean needsReview, String teacherComment,
                            SessionSummary summary, Signals signals, List<TopicReview> topics, AppealDto appeal,
                            Instant startedAt, Instant finishedAt, Instant reviewedAt) {
    }

    public record LevelRequest(@NotNull @Min(0) @Max(4) Integer finalSoloLevel, String comment) {
    }

    /** finalScore — необязательно: преподаватель может поставить итоговый балл сам, вместо расчётного. */
    public record ConfirmRequest(String comment, BigDecimal finalScore) {
    }

    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository questions;
    private final ExamRepository exams;
    private final UserRepository users;
    private final CourseAccess access;
    private final ExamService examService;
    private final SessionView sessionView;
    private final QuestionGrader grader;
    private final TransactionTemplate tx;

    public ReviewService(InterviewSessionRepository sessions, SessionQuestionRepository questions,
                         ExamRepository exams, UserRepository users, CourseAccess access,
                         ExamService examService, SessionView sessionView, QuestionGrader grader,
                         TransactionTemplate tx) {
        this.tx = tx;
        this.sessions = sessions;
        this.questions = questions;
        this.exams = exams;
        this.users = users;
        this.access = access;
        this.examService = examService;
        this.sessionView = sessionView;
        this.grader = grader;
    }

    // ---------- Таблица результатов ----------

    public List<SessionRow> sessionsOfExam(Long examId, AuthUser teacher) {
        Exam exam = examService.owned(examId, teacher);
        List<CourseTopic> examTopics = examService.orderedTopics(exam);
        List<SessionRow> rows = new ArrayList<>();
        for (InterviewSession session : sessions.findByExamIdOrderByStartedAtDesc(examId)) {
            SessionView.Data data = sessionView.load(session);
            User student = users.findById(session.getStudentId()).orElseThrow();
            // Средний уровень SOLO по каждой теме экзамена — видно, какую тему студент понял хуже.
            List<TopicLevel> levels = examTopics.stream().map(topic -> new TopicLevel(topic.getId(), topic.getTitle(),
                    data.topics().stream().filter(t -> t.topic().getId().equals(topic.getId()))
                            .findFirst().map(SessionView.TopicData::averageLevel).orElse(null))).toList();
            boolean openAppeal = data.appeal() != null && data.appeal().getStatus() == Appeal.Status.OPEN;
            rows.add(new SessionRow(session.getId(), student.getFullName(), student.getEmail(), session.getStatus(),
                    session.getAiScore(), session.getFinalScore(), session.isNeedsReview(), data.signals(), levels,
                    openAppeal, session.getStartedAt(), session.getFinishedAt()));
        }
        return rows;
    }

    // ---------- Экран проверки ----------

    public ReviewDto review(Long sessionId, AuthUser teacher) {
        InterviewSession session = ownedSession(sessionId, teacher);
        SessionView.Data data = sessionView.load(session);
        User student = users.findById(session.getStudentId()).orElseThrow();

        List<TopicReview> topics = data.topics().stream().map(topic -> new TopicReview(
                topic.topic().getId(), topic.topic().getTitle(), topic.averageLevel(),
                topic.questions().stream().map(q -> new QuestionReview(
                        q.question().getId(), q.question().getPosition(), q.card().getQuestion(),
                        q.card().getFundamentals(), q.card().getRubric(), q.question().getState(),
                        q.question().getFollowupsAsked(), q.question().getAiSoloLevel(),
                        q.question().getAiConfidence(), q.question().getAiGrade(),
                        q.question().getFinalSoloLevel(), q.question().getTeacherComment(),
                        q.messages().stream().map(SessionView::toTranscript).toList())).toList())).toList();

        return new ReviewDto(session.getId(), data.exam().getId(), data.exam().getTitle(), data.course().getId(),
                data.course().getTitle(), data.exam().getMode(), session.getStatus(), student.getFullName(),
                student.getEmail(), session.getAiScore(), session.getFinalScore(),
                ScoreCalculator.finalScore(data.questions()), session.isNeedsReview(), session.getTeacherComment(),
                session.getAiSummary(), data.signals(), topics, AppealDto.of(data.appeal()),
                session.getStartedAt(), session.getFinishedAt(), session.getReviewedAt());
    }

    /** Преподаватель ставит свой уровень по вопросу. Балл пересчитывается (previewScore в ответе). */
    @Transactional
    public ReviewDto setLevel(Long sessionId, Long questionId, AuthUser teacher, LevelRequest request) {
        InterviewSession session = ownedSession(sessionId, teacher);
        if (session.getStatus() != InterviewSession.Status.AI_GRADED) {
            throw ApiException.conflict(session.getStatus() == InterviewSession.Status.CONFIRMED
                    ? "Оценка уже подтверждена. Изменить её можно только по апелляции."
                    : "Менять уровни можно после того, как ИИ закончит оценку");
        }
        SessionQuestion question = questions.findById(questionId)
                .filter(q -> q.getSessionId().equals(sessionId))
                .orElseThrow(() -> ApiException.notFound("Вопрос не найден"));
        question.setFinalSoloLevel(request.finalSoloLevel());
        question.setTeacherComment(request.comment());
        questions.saveAndFlush(question);
        return review(sessionId, teacher);
    }

    /** Подтверждение итоговой оценки. Только после него студент видит отчёт в режиме EXAM. */
    @Transactional
    public ReviewDto confirm(Long sessionId, AuthUser teacher, ConfirmRequest request) {
        ownedSession(sessionId, teacher);
        InterviewSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        if (exam.getMode() == Exam.Mode.PRACTICE) {
            throw ApiException.conflict("Тренировочные сессии не подтверждаются: оценка в журнал не идёт");
        }
        if (session.getStatus() != InterviewSession.Status.AI_GRADED) {
            throw ApiException.conflict("Подтвердить можно только сессию со статусом AI_GRADED");
        }
        List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(sessionId);
        BigDecimal manual = request == null ? null : request.finalScore();
        if (manual != null && (manual.signum() < 0 || manual.compareTo(BigDecimal.valueOf(100)) > 0)) {
            throw ApiException.badRequest("Итоговый балл — от 0 до 100");
        }
        for (SessionQuestion question : all) {
            if (manual == null && question.effectiveLevel() == null) {
                throw ApiException.badRequest("Поставьте уровень по вопросу " + question.getPosition()
                        + ": ИИ не смог его оценить");
            }
        }
        // Итог ставит человек: либо свой балл, либо расчёт по уровням (которые он тоже мог изменить).
        session.setFinalScore(manual != null ? manual.setScale(1, RoundingMode.HALF_UP) : ScoreCalculator.finalScore(all));
        session.setTeacherComment(request == null ? null : request.comment());
        session.setReviewedBy(teacher.id());
        session.setReviewedAt(Instant.now());
        session.setStatus(InterviewSession.Status.CONFIRMED);
        sessions.saveAndFlush(session);
        return review(sessionId, teacher);
    }

    /**
     * Повторный запуск ИИ-оценки — если она сорвалась (LLM была недоступна, сервер перезапускали).
     * Оценённые вопросы не трогаем, неудачные оцениваем заново, затем заново собирается итог.
     */
    public void regrade(Long sessionId, AuthUser teacher) {
        ownedSession(sessionId, teacher);
        List<Long> toGrade = tx.execute(status -> resetFailedGrades(sessionId));
        grader.regradeAsync(sessionId, toGrade);            // в фоне, уже после коммита
    }

    private List<Long> resetFailedGrades(Long sessionId) {
        InterviewSession session = sessions.findByIdForUpdate(sessionId).orElseThrow();
        if (session.getStatus() != InterviewSession.Status.COMPLETED
                && session.getStatus() != InterviewSession.Status.AI_GRADED) {
            throw ApiException.conflict("Повторная ИИ-оценка доступна только до подтверждения");
        }
        List<Long> toGrade = new ArrayList<>();
        for (SessionQuestion question : questions.findBySessionIdOrderByPosition(sessionId)) {
            boolean failed = question.getAiGrade() == null || question.getAiGrade().failed();
            if (question.getState() == SessionQuestion.State.CLOSED && failed) {
                question.setAiGrade(null);
                questions.save(question);
                toGrade.add(question.getId());
            }
        }
        session.setStatus(InterviewSession.Status.COMPLETED);
        sessions.save(session);
        return toGrade;
    }

    private InterviewSession ownedSession(Long sessionId, AuthUser teacher) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        access.course(exam.getCourseId(), teacher);
        return session;
    }
}
