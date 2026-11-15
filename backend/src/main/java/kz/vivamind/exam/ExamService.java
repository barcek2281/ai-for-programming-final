package kz.vivamind.exam;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.card.CardStatus;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseAccess;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.course.CourseTopicRepository;
import kz.vivamind.interview.InterviewSession;
import kz.vivamind.interview.InterviewSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ExamService {

    public record ExamRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull Exam.Mode mode,
            @NotEmpty Set<Long> topicIds,
            @Min(1) @Max(12) Integer questionsCount,
            @Min(0) @Max(5) Integer minFollowups,
            @Min(1) @Max(6) Integer maxFollowups,
            @Min(30) @Max(900) Integer answerTimeLimitSec,
            Boolean sameQuestions) {
    }

    public record TopicRef(Long id, String title) {
    }

    public record ExamDto(Long id, Long courseId, String courseTitle, String title, Exam.Mode mode,
                          int questionsCount, int minFollowups, int maxFollowups, int answerTimeLimitSec,
                          String accessCode, Exam.Status status, Instant createdAt, List<TopicRef> topics,
                          int sessionsCount, boolean sameQuestions) {
    }

    public record SoloLevel(int level, String name, String description) {
    }

    /** То, что студент видит перед началом: экзамен, правила и рубрика простыми словами. */
    public record ExamIntro(Long id, String title, String courseTitle, String language, Exam.Mode mode,
                            List<String> topics, int questionsCount, int maxFollowups, int answerTimeLimitSec,
                            List<String> rules, List<SoloLevel> soloRubric, Long activeSessionId,
                            boolean attemptUsed) {
    }

    /** Код без похожих символов (0/O, 1/I/L), чтобы его было легко продиктовать в аудитории. */
    private static final String CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamRepository exams;
    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final QuestionCardRepository cards;
    private final InterviewSessionRepository sessions;
    private final CourseAccess access;

    public ExamService(ExamRepository exams, CourseRepository courses, CourseTopicRepository topics,
                       QuestionCardRepository cards, InterviewSessionRepository sessions, CourseAccess access) {
        this.exams = exams;
        this.courses = courses;
        this.topics = topics;
        this.cards = cards;
        this.sessions = sessions;
        this.access = access;
    }

    // ---------- Преподаватель ----------

    @Transactional
    public ExamDto create(Long courseId, AuthUser teacher, ExamRequest request) {
        Course course = access.course(courseId, teacher);
        Exam exam = new Exam();
        exam.setCourseId(course.getId());
        exam.setStatus(Exam.Status.DRAFT);
        exam.setAccessCode(newAccessCode());
        apply(exam, request);
        return toDto(exams.save(exam));
    }

    public List<ExamDto> list(Long courseId, AuthUser teacher) {
        access.course(courseId, teacher);
        return exams.findByCourseIdOrderByCreatedAtDesc(courseId).stream().map(this::toDto).toList();
    }

    public ExamDto get(Long examId, AuthUser teacher) {
        return toDto(owned(examId, teacher));
    }

    @Transactional
    public ExamDto update(Long examId, AuthUser teacher, ExamRequest request) {
        Exam exam = owned(examId, teacher);
        if (exam.getStatus() != Exam.Status.DRAFT) {
            throw ApiException.conflict("Настройки можно менять только до публикации");
        }
        apply(exam, request);
        return toDto(exams.save(exam));
    }

    @Transactional
    public ExamDto publish(Long examId, AuthUser teacher) {
        Exam exam = owned(examId, teacher);
        if (exam.getStatus() == Exam.Status.CLOSED) {
            throw ApiException.conflict("Экзамен уже закрыт");
        }
        // В каждой выбранной теме должна быть хотя бы одна утверждённая карточка.
        for (CourseTopic topic : topics.findAllById(exam.getTopicIds())) {
            if (cards.countByTopicIdAndStatus(topic.getId(), CardStatus.APPROVED) == 0) {
                throw ApiException.conflict("В теме «" + topic.getTitle() + "» нет ни одной утверждённой карточки");
            }
        }
        exam.setStatus(Exam.Status.PUBLISHED);
        return toDto(exams.save(exam));
    }

    @Transactional
    public ExamDto close(Long examId, AuthUser teacher) {
        Exam exam = owned(examId, teacher);
        exam.setStatus(Exam.Status.CLOSED);
        // Незаконченные интервью помечаем как брошенные.
        for (InterviewSession session : sessions.findByExamIdAndStatus(examId, InterviewSession.Status.IN_PROGRESS)) {
            InterviewSession locked = sessions.findByIdForUpdate(session.getId()).orElseThrow();
            if (locked.getStatus() == InterviewSession.Status.IN_PROGRESS) {
                locked.setStatus(InterviewSession.Status.ABANDONED);
                locked.setFinishedAt(Instant.now());
            }
        }
        return toDto(exams.save(exam));
    }

    /** Удаляет экзамен вместе со всеми его сессиями (ответы, оценки и апелляции студентов уходят каскадом). */
    @Transactional
    public void delete(Long examId, AuthUser teacher) {
        Exam exam = owned(examId, teacher);
        sessions.deleteAll(sessions.findByExamIdOrderByStartedAtDesc(examId));
        sessions.flush();
        exams.delete(exam);
    }

    public Exam owned(Long examId, AuthUser teacher) {
        Exam exam = exams.findById(examId).orElseThrow(() -> ApiException.notFound("Экзамен не найден"));
        access.course(exam.getCourseId(), teacher);
        return exam;
    }

    private void apply(Exam exam, ExamRequest request) {
        List<CourseTopic> chosen = topics.findAllById(request.topicIds());
        boolean allFromCourse = chosen.size() == request.topicIds().size()
                && chosen.stream().allMatch(t -> t.getCourseId().equals(exam.getCourseId()));
        if (!allFromCourse) {
            throw ApiException.badRequest("Все темы экзамена должны быть из этого курса");
        }
        exam.setTitle(request.title().trim());
        exam.setMode(request.mode());
        exam.setTopicIds(new LinkedHashSet<>(request.topicIds()));
        if (request.questionsCount() != null) {
            exam.setQuestionsCount(request.questionsCount());
        }
        if (request.minFollowups() != null) {
            exam.setMinFollowups(request.minFollowups());
        }
        if (request.maxFollowups() != null) {
            exam.setMaxFollowups(request.maxFollowups());
        }
        if (request.answerTimeLimitSec() != null) {
            exam.setAnswerTimeLimitSec(request.answerTimeLimitSec());
        }
        if (request.sameQuestions() != null) {
            exam.setSameQuestions(request.sameQuestions());
        }
        if (exam.getMinFollowups() > exam.getMaxFollowups()) {
            throw ApiException.badRequest("Минимум углублений не может быть больше максимума");
        }
    }

    private String newAccessCode() {
        while (true) {
            StringBuilder code = new StringBuilder();
            for (int i = 0; i < 6; i++) {
                code.append(CODE_ALPHABET.charAt(RANDOM.nextInt(CODE_ALPHABET.length())));
            }
            if (!exams.existsByAccessCode(code.toString())) {
                return code.toString();
            }
        }
    }

    public ExamDto toDto(Exam exam) {
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        return new ExamDto(exam.getId(), exam.getCourseId(), course.getTitle(), exam.getTitle(), exam.getMode(),
                exam.getQuestionsCount(), exam.getMinFollowups(), exam.getMaxFollowups(),
                exam.getAnswerTimeLimitSec(), exam.getAccessCode(), exam.getStatus(), exam.getCreatedAt(),
                orderedTopics(exam).stream().map(t -> new TopicRef(t.getId(), t.getTitle())).toList(),
                sessions.countByExamId(exam.getId()), exam.isSameQuestions());
    }

    /** Темы экзамена в порядке курса. */
    public List<CourseTopic> orderedTopics(Exam exam) {
        List<CourseTopic> list = new ArrayList<>(topics.findAllById(exam.getTopicIds()));
        list.sort(Comparator.comparingInt(CourseTopic::getPosition).thenComparing(CourseTopic::getId));
        return list;
    }

    // ---------- Студент ----------

    /** Вход по коду: данные экзамена + правила + рубрика. Сессия здесь ещё не создаётся. */
    public ExamIntro join(String accessCode, AuthUser student) {
        Exam exam = exams.findByAccessCode(accessCode == null ? "" : accessCode.trim().toUpperCase())
                .filter(e -> e.getStatus() != Exam.Status.DRAFT)
                .orElseThrow(() -> ApiException.notFound("Экзамен с таким кодом не найден"));
        if (exam.getStatus() == Exam.Status.CLOSED) {
            throw ApiException.conflict("Этот экзамен уже закрыт");
        }
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        List<String> topicTitles = orderedTopics(exam).stream().map(CourseTopic::getTitle).toList();

        // Вопросов будет не больше, чем целей обучения с утверждёнными карточками.
        long outcomesWithCards = cards.findByTopicIdInAndStatusOrderByIdAsc(exam.getTopicIds(), CardStatus.APPROVED)
                .stream().map(QuestionCard::getOutcomeId).distinct().count();
        int questions = (int) Math.min(exam.getQuestionsCount(), outcomesWithCards);

        List<InterviewSession> mine = sessions.findByExamIdAndStudentIdOrderByStartedAtDesc(exam.getId(), student.id());
        Long activeSessionId = mine.stream()
                .filter(s -> s.getStatus() == InterviewSession.Status.IN_PROGRESS)
                .map(InterviewSession::getId).findFirst().orElse(null);
        boolean attemptUsed = exam.getMode() == Exam.Mode.EXAM && activeSessionId == null && !mine.isEmpty();

        return new ExamIntro(exam.getId(), exam.getTitle(), course.getTitle(), course.getLanguage(), exam.getMode(),
                topicTitles, questions, exam.getMaxFollowups(), exam.getAnswerTimeLimitSec(),
                rules(exam, questions), SOLO_RUBRIC, activeSessionId, attemptUsed);
    }

    private static final List<SoloLevel> SOLO_RUBRIC = List.of(
            new SoloLevel(0, "Преструктурный", "Ответ не по существу или просто повторяет вопрос."),
            new SoloLevel(1, "Одноструктурный", "Назван один верный аспект, без связей с остальным."),
            new SoloLevel(2, "Многоструктурный", "Названо несколько верных аспектов, но они не связаны между собой."),
            new SoloLevel(3, "Реляционный", "Идеи связаны в целое: вы объясняете, почему и как это работает."),
            new SoloLevel(4, "Расширенный абстрактный",
                    "Вы переносите знание на новую ситуацию: обобщаете, приводите свой пример, критикуете."));

    private static List<String> rules(Exam exam, int questions) {
        List<String> rules = new ArrayList<>();
        rules.add("Вопросов в интервью: " + questions + ", все — по материалам выбранных тем. "
                + "Проверяется понимание, а не заучивание.");
        rules.add("После вашего ответа ИИ задаёт углубляющие вопросы по вашим же словам "
                + "(«почему?», «приведите пример», «а что если…»). Максимум углублений на один вопрос: "
                + exam.getMaxFollowups() + ".");
        rules.add("Время на один ответ: " + exam.getAnswerTimeLimitSec() + " с. "
                + "Когда оно выйдет, набранный текст отправится автоматически.");
        rules.add("Если не знаете ответа — нажмите «Не знаю». Два «Не знаю» подряд закрывают вопрос.");
        if (exam.getMode() == Exam.Mode.EXAM) {
            rules.add("Режим экзамена: одна попытка, подсказок нет. Вставка текста заблокирована. "
                    + "Попытки вставки и переключения вкладок фиксируются как сигналы для преподавателя — "
                    + "это не доказательства нарушения, и на балл они автоматически не влияют.");
            rules.add("ИИ готовит только черновик оценки по таксономии SOLO. Итоговую оценку подтверждает "
                    + "преподаватель; после этого вы увидите отчёт и в течение 24 часов сможете подать апелляцию.");
        } else {
            rules.add("Режим тренировки: попыток сколько угодно, ИИ может давать подсказки, отчёт — сразу после "
                    + "ИИ-оценки. В журнал оценка не идёт.");
        }
        rules.add("Ваши ответы обрабатывает ИИ-модель. В неё передаются только текст диалога и материалы курса — "
                + "без имени, email и других персональных данных.");
        return rules;
    }
}
