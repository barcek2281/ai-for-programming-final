package kz.vivamind.appeal;

import jakarta.validation.constraints.NotBlank;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.auth.UserRepository;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseAccess;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.grading.ScoreCalculator;
import kz.vivamind.grading.SessionView.AppealDto;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSessionRepository;
import kz.vivamind.interview.SessionQuestion;
import kz.vivamind.interview.SessionQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Апелляции (раздел 9 ТЗ): студент подаёт в течение 24 часов после подтверждения оценки,
 * преподаватель разбирает. По итогам апелляции балл нельзя понизить.
 */
@Service
public class AppealService {

    private static final Duration APPEAL_WINDOW = Duration.ofHours(24);

    public record AppealRequest(@NotBlank String reason) {
    }

    /** questionLevels: id вопроса сессии → новый уровень SOLO (необязательно). */
    public record ResolveRequest(@NotBlank String resolution, Map<Long, Integer> questionLevels) {
    }

    public record AppealRow(Long id, Long sessionId, String examTitle, String courseTitle, String studentName,
                            String reason, Appeal.Status status, String resolution, BigDecimal finalScore,
                            Instant createdAt, Instant resolvedAt) {
    }

    private final AppealRepository appeals;
    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository questions;
    private final ExamRepository exams;
    private final CourseRepository courses;
    private final UserRepository users;
    private final CourseAccess access;

    public AppealService(AppealRepository appeals, InterviewSessionRepository sessions,
                         SessionQuestionRepository questions, ExamRepository exams, CourseRepository courses,
                         UserRepository users, CourseAccess access) {
        this.appeals = appeals;
        this.sessions = sessions;
        this.questions = questions;
        this.exams = exams;
        this.courses = courses;
        this.users = users;
        this.access = access;
    }

    public static Instant deadline(InterviewSession session) {
        return session.getReviewedAt().plus(APPEAL_WINDOW);
    }

    // ---------- Студент ----------

    @Transactional
    public AppealDto submit(Long sessionId, AuthUser student, AppealRequest request) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (!session.getStudentId().equals(student.id())) {
            throw ApiException.forbidden("Это не ваша сессия");
        }
        if (session.getStatus() != InterviewSession.Status.CONFIRMED) {
            throw ApiException.conflict("Апелляцию можно подать только на подтверждённую оценку");
        }
        if (Instant.now().isAfter(deadline(session))) {
            throw ApiException.conflict("Срок подачи апелляции (24 часа после подтверждения) истёк");
        }
        if (appeals.findFirstBySessionIdOrderByIdDesc(sessionId).isPresent()) {
            throw ApiException.conflict("Апелляция по этой сессии уже подана");
        }
        Appeal appeal = new Appeal();
        appeal.setSessionId(sessionId);
        appeal.setReason(request.reason().trim());
        appeal.setStatus(Appeal.Status.OPEN);
        return AppealDto.of(appeals.save(appeal));
    }

    // ---------- Преподаватель ----------

    public List<AppealRow> listForTeacher(AuthUser teacher, Appeal.Status status) {
        return appeals.findForTeacher(teacher.id()).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .map(this::toRow).toList();
    }

    @Transactional
    public AppealRow resolve(Long appealId, AuthUser teacher, ResolveRequest request) {
        Appeal appeal = appeals.findById(appealId).orElseThrow(() -> ApiException.notFound("Апелляция не найдена"));
        InterviewSession session = sessions.findByIdForUpdate(appeal.getSessionId()).orElseThrow();
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        access.course(exam.getCourseId(), teacher);
        if (appeal.getStatus() != Appeal.Status.OPEN) {
            throw ApiException.conflict("Апелляция уже разобрана");
        }

        if (request.questionLevels() != null && !request.questionLevels().isEmpty()) {
            List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(session.getId());
            for (SessionQuestion question : all) {
                Integer level = request.questionLevels().get(question.getId());
                if (level == null) {
                    continue;
                }
                if (level < 0 || level > 4) {
                    throw ApiException.badRequest("Уровень SOLO — от 0 до 4");
                }
                question.setFinalSoloLevel(level);
            }
            BigDecimal newScore = ScoreCalculator.finalScore(all);
            // Как в политике экзаменов AITU: по апелляции оценку можно повысить или оставить, но не понизить.
            if (session.getFinalScore() != null && newScore.compareTo(session.getFinalScore()) < 0) {
                throw ApiException.badRequest("По итогам апелляции балл нельзя понизить (сейчас "
                        + session.getFinalScore().setScale(1, RoundingMode.HALF_UP) + ", получилось бы " + newScore + ")");
            }
            questions.saveAll(all);
            session.setFinalScore(newScore);
            sessions.save(session);
        }

        appeal.setResolution(request.resolution().trim());
        appeal.setStatus(Appeal.Status.RESOLVED);
        appeal.setResolvedAt(Instant.now());
        return toRow(appeals.save(appeal));
    }

    private AppealRow toRow(Appeal appeal) {
        InterviewSession session = sessions.findById(appeal.getSessionId()).orElseThrow();
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        String student = users.findById(session.getStudentId()).map(u -> u.getFullName()).orElse("");
        return new AppealRow(appeal.getId(), session.getId(), exam.getTitle(), course.getTitle(), student,
                appeal.getReason(), appeal.getStatus(), appeal.getResolution(), session.getFinalScore(),
                appeal.getCreatedAt(), appeal.getResolvedAt());
    }
}
