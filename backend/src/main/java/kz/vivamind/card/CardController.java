package kz.vivamind.card;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.vivamind.ai.dto.FollowUp;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseAccess;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.course.LearningOutcome;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Карточки вопросов: ИИ готовит черновик, преподаватель правит и утверждает. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TEACHER')")
public class CardController {

    public record CardUpdateRequest(
            @NotBlank String question,
            @NotNull @Size(min = 1, max = 7) List<@NotBlank String> fundamentals,
            @NotNull List<String> misconceptions,
            @NotNull List<FollowUp> followUpIdeas,
            @NotNull Map<String, String> rubric) {
    }

    private final QuestionCardRepository cards;
    private final CardGenerationService generation;
    private final RetrievalService retrieval;
    private final CourseAccess access;

    public CardController(QuestionCardRepository cards, CardGenerationService generation,
                          RetrievalService retrieval, CourseAccess access) {
        this.cards = cards;
        this.generation = generation;
        this.retrieval = retrieval;
        this.access = access;
    }

    /** Синхронно, 5–15 секунд: одна новая карточка в статусе DRAFT. */
    @PostMapping("/outcomes/{id}/cards/generate")
    public QuestionCard generate(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        LearningOutcome outcome = access.outcome(id, teacher);
        CourseTopic topic = access.topic(outcome.getTopicId(), teacher);
        Course course = access.course(topic.getCourseId(), teacher);
        return generation.generate(course, topic, outcome);
    }

    @GetMapping("/topics/{id}/cards")
    public List<QuestionCard> list(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                   @RequestParam(required = false) CardStatus status) {
        access.topic(id, teacher);
        return status == null
                ? cards.findByTopicIdOrderByIdDesc(id)
                : cards.findByTopicIdAndStatusOrderByIdDesc(id, status);
    }

    @PutMapping("/cards/{id}")
    public QuestionCard update(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                               @Valid @RequestBody CardUpdateRequest request) {
        QuestionCard card = owned(id, teacher);
        apply(card, request);
        return touch(card, teacher);
    }

    /** Преподаватель пишет вопрос сам, без ИИ. Свой вопрос сразу считается утверждённым. */
    @PostMapping("/outcomes/{id}/cards")
    public QuestionCard create(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                               @Valid @RequestBody CardUpdateRequest request) {
        LearningOutcome outcome = access.outcome(id, teacher);
        CourseTopic topic = access.topic(outcome.getTopicId(), teacher);
        QuestionCard card = new QuestionCard();
        card.setCourseId(topic.getCourseId());
        card.setTopicId(topic.getId());
        card.setOutcomeId(outcome.getId());
        card.setSourceChunkIds(List.of());
        card.setAiGenerated(false);
        card.setStatus(CardStatus.APPROVED);
        apply(card, request);
        return touch(card, teacher);
    }

    private static void apply(QuestionCard card, CardUpdateRequest request) {
        for (String level : List.of("0", "1", "2", "3", "4")) {
            String text = request.rubric().get(level);
            if (text == null || text.isBlank()) {
                throw ApiException.badRequest("В рубрике должны быть заполнены все уровни 0–4");
            }
        }
        card.setQuestion(request.question().trim());
        card.setFundamentals(request.fundamentals());
        card.setMisconceptions(request.misconceptions().stream().filter(s -> s != null && !s.isBlank()).toList());
        card.setFollowUpIdeas(request.followUpIdeas().stream()
                .filter(i -> i != null && i.type() != null && i.text() != null && !i.text().isBlank()).toList());
        card.setRubric(request.rubric());
    }

    @PostMapping("/cards/{id}/approve")
    public QuestionCard approve(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        QuestionCard card = owned(id, teacher);
        card.setStatus(CardStatus.APPROVED);
        return touch(card, teacher);
    }

    @PostMapping("/cards/{id}/reject")
    public QuestionCard reject(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        QuestionCard card = owned(id, teacher);
        card.setStatus(CardStatus.REJECTED);
        return touch(card, teacher);
    }

    /** Фрагменты материалов, по которым карточка сгенерирована (файл, страница, текст). */
    @GetMapping("/cards/{id}/sources")
    public List<Excerpt> sources(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        QuestionCard card = owned(id, teacher);
        return retrieval.byChunkIds(card.getTopicId(), card.getSourceChunkIds(), card.getQuestion());
    }

    private QuestionCard owned(Long cardId, AuthUser teacher) {
        QuestionCard card = cards.findById(cardId).orElseThrow(() -> ApiException.notFound("Карточка не найдена"));
        access.course(card.getCourseId(), teacher);
        return card;
    }

    private QuestionCard touch(QuestionCard card, AuthUser teacher) {
        card.setUpdatedBy(teacher.id());
        card.setUpdatedAt(Instant.now());
        return cards.save(card);
    }
}
