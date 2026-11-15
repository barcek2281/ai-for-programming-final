package kz.vivamind.interview;

import kz.vivamind.ai.AiMessages;
import kz.vivamind.ai.AiService;
import kz.vivamind.ai.dto.FollowUp;
import kz.vivamind.ai.dto.FollowUpType;
import kz.vivamind.ai.dto.TurnDecision;
import kz.vivamind.ai.dto.TurnDecision.Understanding;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.common.ApiException;
import kz.vivamind.common.Languages;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseRepository;
import kz.vivamind.course.CourseTopicRepository;
import kz.vivamind.exam.Exam;
import kz.vivamind.exam.ExamRepository;
import kz.vivamind.grading.QuestionGrader;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Цикл интервью (раздел 3 ТЗ): базовый вопрос → ответ → углубления → закрытие вопроса → следующий вопрос.
 *
 * Обработка ответа идёт в три шага, чтобы не держать транзакцию БД, пока отвечает LLM:
 * 1) короткая транзакция: проверки и сохранение ответа студента;
 * 2) вызов LLM (без транзакции);
 * 3) короткая транзакция: TurnPolicy принимает решение, сохраняется следующая реплика ИИ.
 */
@Service
public class InterviewService {

    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);

    /** Допуск на сеть: ответ считается опоздавшим только через 10 с после дедлайна. */
    private static final Duration DEADLINE_GRACE = Duration.ofSeconds(10);
    /** Если ответ студента висит без реплики ИИ дольше этого времени — обработка сорвалась, ответ можно отправить заново. */
    private static final Duration STALE_ANSWER = Duration.ofSeconds(150);
    private static final int MAX_ANSWER_CHARS = 6000;
    private static final int EXCERPTS_FOR_TURN = 3;

    public record StartRequest(boolean consent, String accessCode) {
    }

    public record AnswerRequest(String text, Message.Kind kind, Integer responseMs, Integer pasteAttempts,
                                Integer tabSwitches) {
    }

    public record QuestionInfo(int position, int total, String courseTopic) {
    }

    public record MessageDto(Long id, Message.Author author, Message.Kind kind, FollowUpType followUpType,
                             String text, Integer questionPosition, Instant createdAt) {
    }

    public record AnswerResponse(InterviewSession.Status sessionStatus, QuestionInfo question, MessageDto aiMessage,
                                 Instant answerDeadline, Instant serverTime) {
    }

    /** Полное состояние сессии — чтобы страница интервью пережила перезагрузку. */
    public record SessionState(Long id, Long examId, String examTitle, Exam.Mode mode,
                               InterviewSession.Status sessionStatus, QuestionInfo question,
                               List<MessageDto> messages, Instant answerDeadline, int answerTimeLimitSec,
                               boolean awaitingAi, Instant serverTime) {
    }

    /** Всё, что нужно для хода, собранное в первой транзакции. */
    private record Turn(Long sessionId, Exam exam, String language, SessionQuestion question, QuestionCard card,
                        Message answer, List<Message> dialogue, boolean noAnswer) {
    }

    private record Applied(Long closedQuestionId, AnswerResponse response) {
    }

    private final InterviewSessionRepository sessions;
    private final SessionQuestionRepository questions;
    private final MessageRepository messages;
    private final ExamRepository exams;
    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final QuestionCardRepository cards;
    private final QuestionSelector selector;
    private final RetrievalService retrieval;
    private final AiService ai;
    private final TurnPolicy turnPolicy;
    private final QuestionGrader grader;
    private final TransactionTemplate tx;

    public InterviewService(InterviewSessionRepository sessions, SessionQuestionRepository questions,
                            MessageRepository messages, ExamRepository exams, CourseRepository courses,
                            CourseTopicRepository topics, QuestionCardRepository cards, QuestionSelector selector,
                            RetrievalService retrieval, AiService ai, TurnPolicy turnPolicy, QuestionGrader grader,
                            TransactionTemplate tx) {
        this.sessions = sessions;
        this.questions = questions;
        this.messages = messages;
        this.exams = exams;
        this.courses = courses;
        this.topics = topics;
        this.cards = cards;
        this.selector = selector;
        this.retrieval = retrieval;
        this.ai = ai;
        this.turnPolicy = turnPolicy;
        this.grader = grader;
        this.tx = tx;
    }

    // ---------- Старт ----------

    public SessionState start(Long examId, AuthUser student, StartRequest request) {
        if (request == null || !request.consent()) {
            throw ApiException.badRequest("Без согласия на обработку ответов интервью не начинается");
        }
        Long sessionId = tx.execute(status -> createSession(examId, student, request.accessCode()));
        return state(sessionId, student);
    }

    private Long createSession(Long examId, AuthUser student, String accessCode) {
        Exam exam = exams.findById(examId).orElseThrow(() -> ApiException.notFound("Экзамен не найден"));
        if (exam.getStatus() != Exam.Status.PUBLISHED) {
            throw ApiException.conflict("Экзамен сейчас недоступен");
        }
        if (accessCode == null || !exam.getAccessCode().equalsIgnoreCase(accessCode.trim())) {
            throw ApiException.forbidden("Неверный код экзамена");
        }
        List<InterviewSession> previous = sessions.findByExamIdAndStudentIdOrderByStartedAtDesc(examId, student.id());
        Optional<InterviewSession> active = previous.stream()
                .filter(s -> s.getStatus() == InterviewSession.Status.IN_PROGRESS).findFirst();
        if (active.isPresent()) {
            return active.get().getId();                       // продолжаем начатую попытку
        }
        if (exam.getMode() == Exam.Mode.EXAM && !previous.isEmpty()) {
            throw ApiException.conflict("В режиме экзамена только одна попытка");
        }

        InterviewSession session = new InterviewSession();
        session.setExamId(examId);
        session.setStudentId(student.id());
        session.setStatus(InterviewSession.Status.IN_PROGRESS);
        session.setConsentAt(Instant.now());
        session.setCurrentQuestionPos(1);
        session = sessions.save(session);

        // Одинаковые вопросы для всех: случайность зависит от экзамена, а не от сессии.
        List<QuestionCard> picked = selector.select(exam, exam.isSameQuestions() ? exam.getId() : session.getId());
        if (picked.isEmpty()) {
            throw ApiException.conflict("В экзамене нет утверждённых вопросов");
        }
        Long firstQuestionId = null;
        for (int i = 0; i < picked.size(); i++) {
            SessionQuestion question = new SessionQuestion();
            question.setSessionId(session.getId());
            question.setCardId(picked.get(i).getId());
            question.setPosition(i + 1);
            question.setState(i == 0 ? SessionQuestion.State.ACTIVE : SessionQuestion.State.PENDING);
            question = questions.save(question);
            if (i == 0) {
                firstQuestionId = question.getId();
            }
        }
        saveAiMessage(session.getId(), firstQuestionId, Message.Kind.BASE_QUESTION, null, picked.get(0).getQuestion());
        return session.getId();
    }

    // ---------- Состояние (для перезагрузки страницы) ----------

    public SessionState state(Long sessionId, AuthUser student) {
        InterviewSession session = sessions.findById(sessionId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (!session.getStudentId().equals(student.id())) {
            throw ApiException.forbidden("Это не ваша сессия");
        }
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(sessionId);
        Map<Long, Integer> positionById = new HashMap<>();
        all.forEach(q -> positionById.put(q.getId(), q.getPosition()));

        List<Message> history = messages.findBySessionIdOrderByIdAsc(sessionId);
        Message last = history.isEmpty() ? null : history.get(history.size() - 1);
        boolean inProgress = session.getStatus() == InterviewSession.Status.IN_PROGRESS;
        boolean awaitingAi = inProgress && last != null && last.fromStudent();
        Instant deadline = inProgress && last != null && !last.fromStudent()
                ? last.getCreatedAt().plusSeconds(exam.getAnswerTimeLimitSec()) : null;

        QuestionInfo info = null;
        if (inProgress) {
            SessionQuestion current = all.stream()
                    .filter(q -> q.getPosition() == session.getCurrentQuestionPos()).findFirst().orElseThrow();
            info = new QuestionInfo(current.getPosition(), all.size(), topicTitle(current.getCardId()));
        }
        List<MessageDto> dtos = history.stream().map(m -> toDto(m, positionById.get(m.getQuestionId()))).toList();
        return new SessionState(session.getId(), exam.getId(), exam.getTitle(), exam.getMode(), session.getStatus(),
                info, dtos, deadline, exam.getAnswerTimeLimitSec(), awaitingAi, Instant.now());
    }

    // ---------- Ответ студента ----------

    public AnswerResponse handleAnswer(Long sessionId, AuthUser student, AnswerRequest request) {
        // 1) проверки + сохранение ответа
        Turn turn = tx.execute(status -> saveAnswer(sessionId, student, request));

        // 2) LLM оценивает последний ответ и предлагает углубление
        TurnDecision proposed;
        try {
            List<Excerpt> excerpts = retrieval.search(turn.card().getTopicId(),
                    turn.card().getQuestion() + "\n" + turn.answer().getText(), EXCERPTS_FOR_TURN);
            String userText = AiMessages.turn(turn.exam().getMode(), turn.card(), excerpts, turn.dialogue(),
                    turn.question().getFollowupsAsked(), turn.exam().getMaxFollowups());
            proposed = ai.decideTurn(Languages.promptName(turn.language()),
                    TurnPolicy.allowedTypes(turn.exam().getMode()), userText, sessionId);
        } catch (RuntimeException e) {
            log.warn("Turn failed for session {}: {}", sessionId, e.getClass().getSimpleName());
            messages.deleteById(turn.answer().getId());      // ответ не засчитан — его можно отправить ещё раз
            throw ApiException.unavailable("ИИ-экзаменатор не ответил. Ваш ответ не потерян — отправьте его ещё раз.");
        }
        // «Не знаю» и пустой ответ — всегда «нет понимания», что бы ни сказала модель.
        TurnDecision decision = turn.noAnswer() ? proposed.withUnderstanding(Understanding.ABSENT) : proposed;

        // 3) решение принимает TurnPolicy
        Applied applied = tx.execute(status -> applyDecision(turn, decision));

        // Оценка закрытого вопроса — в фоне, после коммита.
        if (applied.closedQuestionId() != null) {
            grader.gradeAsync(applied.closedQuestionId());
        }
        return applied.response();
    }

    private Turn saveAnswer(Long sessionId, AuthUser student, AnswerRequest request) {
        InterviewSession session = sessions.findByIdForUpdate(sessionId)
                .orElseThrow(() -> ApiException.notFound("Сессия не найдена"));
        if (!session.getStudentId().equals(student.id())) {
            throw ApiException.forbidden("Это не ваша сессия");
        }
        if (session.getStatus() != InterviewSession.Status.IN_PROGRESS) {
            throw ApiException.conflict("Интервью уже завершено");
        }
        Exam exam = exams.findById(session.getExamId()).orElseThrow();
        Course course = courses.findById(exam.getCourseId()).orElseThrow();
        Instant now = Instant.now();

        // Последней должна быть реплика ИИ. Если это ответ студента — либо двойная отправка (409),
        // либо прошлый ход сорвался и «зависший» ответ нужно убрать.
        Message last = messages.findFirstBySessionIdOrderByIdDesc(sessionId).orElseThrow();
        if (last.fromStudent()) {
            if (last.getCreatedAt().plus(STALE_ANSWER).isAfter(now)) {
                throw ApiException.conflict("Предыдущий ответ ещё обрабатывается");
            }
            messages.delete(last);
            messages.flush();
            last = messages.findFirstBySessionIdOrderByIdDesc(sessionId).orElseThrow();
        }

        SessionQuestion question = questions.findBySessionIdAndPosition(sessionId, session.getCurrentQuestionPos())
                .orElseThrow();
        QuestionCard card = cards.findById(question.getCardId()).orElseThrow();

        Message.Kind kind = request.kind() == null ? Message.Kind.ANSWER : request.kind();
        if (kind != Message.Kind.ANSWER && kind != Message.Kind.DONT_KNOW && kind != Message.Kind.TIMEOUT) {
            throw ApiException.badRequest("kind: ANSWER, DONT_KNOW или TIMEOUT");
        }
        String text = request.text() == null ? "" : request.text().strip();
        if (text.length() > MAX_ANSWER_CHARS) {
            text = text.substring(0, MAX_ANSWER_CHARS);
        }
        if (kind == Message.Kind.DONT_KNOW) {
            text = Languages.dontKnow(course.getLanguage());
        }
        if (kind == Message.Kind.ANSWER && text.isEmpty()) {
            throw ApiException.badRequest("Ответ пустой");
        }
        // Сервер — источник истины по времени: дедлайн = время последней реплики ИИ + лимит.
        Instant deadline = last.getCreatedAt().plusSeconds(exam.getAnswerTimeLimitSec());
        if (kind == Message.Kind.ANSWER && now.isAfter(deadline.plus(DEADLINE_GRACE))) {
            kind = Message.Kind.TIMEOUT;
        }

        Message answer = new Message();
        answer.setSessionId(sessionId);
        answer.setQuestionId(question.getId());
        answer.setAuthor(Message.Author.STUDENT);
        answer.setKind(kind);
        answer.setText(text);
        answer.setResponseMs(request.responseMs());
        answer.setPasteAttempts(nonNegative(request.pasteAttempts()));
        answer.setTabSwitches(nonNegative(request.tabSwitches()));
        answer.setCreatedAt(now);
        answer = messages.save(answer);

        List<Message> dialogue = messages.findByQuestionIdOrderByIdAsc(question.getId());
        boolean noAnswer = kind == Message.Kind.DONT_KNOW || text.isEmpty();
        return new Turn(sessionId, exam, course.getLanguage(), question, card, answer, dialogue, noAnswer);
    }

    private Applied applyDecision(Turn turn, TurnDecision decision) {
        InterviewSession session = sessions.findByIdForUpdate(turn.sessionId()).orElseThrow();
        if (session.getStatus() != InterviewSession.Status.IN_PROGRESS) {
            throw ApiException.conflict("Экзамен закрыт преподавателем");
        }
        SessionQuestion question = questions.findById(turn.question().getId()).orElseThrow();
        Message answer = messages.findById(turn.answer().getId()).orElseThrow();
        answer.setTurnDecision(decision);                       // сырой ответ LLM — для аудита
        messages.save(answer);

        if (decision.offTopicOrInjection()) {
            session.setNeedsReview(true);                       // попытка манипуляции → обязательно смотрит человек
        }
        TurnPolicy.Result result = turnPolicy.decide(question, decision, turn.exam());
        question.setAbsentStreak(result.absentStreak());

        List<SessionQuestion> all = questions.findBySessionIdOrderByPosition(session.getId());
        int limit = turn.exam().getAnswerTimeLimitSec();

        if (result.action() == TurnPolicy.Action.FOLLOW_UP) {
            List<String> alreadyAsked = turn.dialogue().stream()
                    .filter(m -> m.getKind() == Message.Kind.FOLLOW_UP).map(Message::getText).toList();
            FollowUp followUp = turnPolicy.chooseFollowUp(decision, turn.card(), alreadyAsked,
                    turn.exam().getMode(), turn.language());
            question.setFollowupsAsked(question.getFollowupsAsked() + 1);
            questions.save(question);
            sessions.save(session);
            Message aiMessage = saveAiMessage(session.getId(), question.getId(), Message.Kind.FOLLOW_UP,
                    followUp.type(), followUp.text());
            QuestionInfo info = new QuestionInfo(question.getPosition(), all.size(), topicTitle(question.getCardId()));
            return new Applied(null, response(session, info, aiMessage, question.getPosition(), limit));
        }

        // Вопрос закрыт. Следующий базовый вопрос берётся прямо из карточки, без LLM.
        question.setState(SessionQuestion.State.CLOSED);
        questions.save(question);
        Optional<SessionQuestion> next = all.stream()
                .filter(q -> q.getPosition() == question.getPosition() + 1).findFirst();
        if (next.isPresent()) {
            SessionQuestion nextQuestion = next.get();
            nextQuestion.setState(SessionQuestion.State.ACTIVE);
            questions.save(nextQuestion);
            session.setCurrentQuestionPos(nextQuestion.getPosition());
            sessions.save(session);
            QuestionCard nextCard = cards.findById(nextQuestion.getCardId()).orElseThrow();
            Message aiMessage = saveAiMessage(session.getId(), nextQuestion.getId(), Message.Kind.BASE_QUESTION,
                    null, nextCard.getQuestion());
            QuestionInfo info = new QuestionInfo(nextQuestion.getPosition(), all.size(),
                    topicTitle(nextCard.getId()));
            return new Applied(question.getId(), response(session, info, aiMessage, nextQuestion.getPosition(), limit));
        }

        // Это был последний вопрос.
        session.setStatus(InterviewSession.Status.COMPLETED);
        session.setFinishedAt(Instant.now());
        sessions.save(session);
        Message thanks = saveAiMessage(session.getId(), null, Message.Kind.SYSTEM, null,
                Languages.thanks(turn.language()));
        return new Applied(question.getId(), response(session, null, thanks, null, limit));
    }

    // ---------- вспомогательное ----------

    private AnswerResponse response(InterviewSession session, QuestionInfo info, Message aiMessage,
                                    Integer questionPosition, int limitSec) {
        Instant deadline = session.getStatus() == InterviewSession.Status.IN_PROGRESS
                ? aiMessage.getCreatedAt().plusSeconds(limitSec) : null;
        return new AnswerResponse(session.getStatus(), info, toDto(aiMessage, questionPosition), deadline,
                Instant.now());
    }

    private Message saveAiMessage(Long sessionId, Long questionId, Message.Kind kind, FollowUpType type, String text) {
        Message message = new Message();
        message.setSessionId(sessionId);
        message.setQuestionId(questionId);
        message.setAuthor(Message.Author.AI);
        message.setKind(kind);
        message.setFollowUpType(type);
        message.setText(text);
        return messages.save(message);
    }

    private String topicTitle(Long cardId) {
        return cards.findById(cardId)
                .flatMap(card -> topics.findById(card.getTopicId()))
                .map(topic -> topic.getTitle()).orElse("");
    }

    private static MessageDto toDto(Message m, Integer questionPosition) {
        return new MessageDto(m.getId(), m.getAuthor(), m.getKind(), m.getFollowUpType(), m.getText(),
                questionPosition, m.getCreatedAt());
    }

    private static int nonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }
}
