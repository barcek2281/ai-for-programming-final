package kz.vivamind.grading;

import kz.vivamind.ai.dto.QuestionGrade;
import kz.vivamind.ai.dto.SessionSummary;
import kz.vivamind.appeal.AppealService;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.grading.SessionView.AppealDto;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSession.Status;
import kz.vivamind.interview.InterviewSessionRepository;
import kz.vivamind.interview.Message;
import kz.vivamind.interview.SessionQuestion;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Отчёт для студента (прозрачность): уровень по каждому вопросу, цитаты, пробелы, что перечитать и свой транскрипт.
 * PRACTICE — сразу после ИИ-оценки; EXAM — только после подтверждения преподавателем.
 */
@Service
public class ReportService {

    public record TranscriptLine(Message.Author author, Message.Kind kind, String text) {
    }

    public record ReportQuestion(int position, String question, Integer level, boolean changedByTeacher,
                                 String feedback, List<QuestionGrade.Evidence> evidence,
                                 List<String> missingFundamentals, String teacherComment,
                                 List<TranscriptLine> transcript) {
    }

    public record ReportTopic(String title, BigDecimal avgLevel, List<ReportQuestion> questions) {
    }

    /** Если available = false, остальные поля пустые, а message объясняет, чего ждать. */
    public record ReportDto(boolean available, String message, Long sessionId, String examTitle, String courseTitle,
                            Exam.Mode mode, Status status, BigDecimal score, boolean confirmed,
                            SessionSummary summary, List<ReportTopic> topics, String teacherComment,
                            AppealDto appeal, boolean canAppeal, Instant appealDeadline) {
    }

    public record HistoryRow(Long id, String examTitle, String courseTitle, Exam.Mode mode, Status status,
                             BigDecimal score, boolean reportAvailable, Instant startedAt, Instant finishedAt) {
    }

    private final InterviewSessionRepository sessions;
    private final ExamRepository exams;
    private final CourseRepository courses;
    private final SessionView sessionView;

    public ReportService(InterviewSessionRepository sessions, ExamRepository exams, CourseRepository courses,
                         SessionView sessionView) {
        this.sessions = sessions;
        this.exams = exams;
        this.courses = courses;
        this.sessionView = sessionView;
    }

    public ReportDto report(Long sessionId, AuthUser student) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (!session.getStudentId().equals(student.id())) {
            throw ApiException.forbidden("Это не ваша сессия");
        }
        SessionView.Data data = sessionView.load(session);
        Exam exam = data.exam();
        if (!isAvailable(exam.getMode(), session.getStatus())) {
            return new ReportDto(false, waitingMessage(exam.getMode(), session.getStatus()), session.getId(),
                    exam.getTitle(), data.course().getTitle(), exam.getMode(), session.getStatus(), null, false,
                    null, List.of(), null, null, false, null);
        }

        boolean confirmed = session.getStatus() == Status.CONFIRMED;
        List<ReportTopic> topics = data.topics().stream().map(topic -> new ReportTopic(
                topic.topic().getTitle(), topic.averageLevel(),
                topic.questions().stream().map(ReportService::toReportQuestion).toList())).toList();

        Instant appealDeadline = confirmed ? AppealService.deadline(session) : null;
        boolean canAppeal = confirmed && data.appeal() == null && Instant.now().isBefore(appealDeadline);

        return new ReportDto(true, null, session.getId(), exam.getTitle(), data.course().getTitle(), exam.getMode(),
                session.getStatus(), confirmed ? session.getFinalScore() : session.getAiScore(), confirmed,
                session.getAiSummary(), topics, session.getTeacherComment(), AppealDto.of(data.appeal()),
                canAppeal, appealDeadline);
    }

    public List<HistoryRow> history(AuthUser student) {
        return sessions.findByStudentIdOrderByStartedAtDesc(student.id()).stream().map(session -> {
            Exam exam = exams.findById(session.getExamId()).orElseThrow();
            String courseTitle = courses.findById(exam.getCourseId()).map(c -> c.getTitle()).orElse("");
            boolean available = isAvailable(exam.getMode(), session.getStatus());
            BigDecimal score = !available ? null
                    : session.getStatus() == Status.CONFIRMED ? session.getFinalScore() : session.getAiScore();
            return new HistoryRow(session.getId(), exam.getTitle(), courseTitle, exam.getMode(), session.getStatus(),
                    score, available, session.getStartedAt(), session.getFinishedAt());
        }).toList();
    }

    private static ReportQuestion toReportQuestion(SessionView.QuestionData data) {
        SessionQuestion question = data.question();
        StoredGrade grade = question.getAiGrade();
        return new ReportQuestion(question.getPosition(), data.card().getQuestion(), question.effectiveLevel(),
                question.getFinalSoloLevel() != null && !question.getFinalSoloLevel().equals(question.getAiSoloLevel()),
                grade == null ? null : grade.studentFeedback(),
                grade == null ? List.of() : grade.evidence(),
                grade == null ? List.of() : grade.missingFundamentals(),
                question.getTeacherComment(),
                data.messages().stream().map(m -> new TranscriptLine(m.getAuthor(), m.getKind(), m.getText())).toList());
    }

    private static boolean isAvailable(Exam.Mode mode, Status status) {
        return status == Status.CONFIRMED || (mode == Exam.Mode.PRACTICE && status == Status.AI_GRADED);
    }

    private static String waitingMessage(Exam.Mode mode, Status status) {
        return switch (status) {
            case IN_PROGRESS -> "Интервью ещё не завершено.";
            case COMPLETED -> "ИИ оценивает ваши ответы. Обычно это занимает около минуты.";
            case AI_GRADED -> "Черновик оценки готов. Отчёт появится после проверки преподавателем.";
            case ABANDONED -> "Интервью не было завершено: экзамен закрыли раньше.";
            default -> "Отчёт пока недоступен.";
        };
    }
}
