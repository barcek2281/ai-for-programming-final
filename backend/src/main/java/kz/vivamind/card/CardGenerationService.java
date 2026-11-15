package kz.vivamind.card;

import kz.vivamind.ai.AiMessages;
import kz.vivamind.ai.AiService;
import kz.vivamind.ai.dto.CardDraft;
import kz.vivamind.ai.dto.FollowUp;
import kz.vivamind.ai.dto.FollowUpType;
import kz.vivamind.common.ApiException;
import kz.vivamind.common.Languages;
import kz.vivamind.course.Course;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.course.LearningOutcome;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Генерация черновика карточки по цели обучения: RAG (top-6 фрагментов темы) → LLM → DRAFT. */
@Service
public class CardGenerationService {

    private static final int EXCERPTS_FOR_CARD = 6;

    private final RetrievalService retrieval;
    private final AiService ai;
    private final QuestionCardRepository cards;

    public CardGenerationService(RetrievalService retrieval, AiService ai, QuestionCardRepository cards) {
        this.retrieval = retrieval;
        this.ai = ai;
        this.cards = cards;
    }

    public QuestionCard generate(Course course, CourseTopic topic, LearningOutcome outcome) {
        List<Excerpt> excerpts = retrieval.search(topic.getId(), outcome.getText(), EXCERPTS_FOR_CARD);
        if (excerpts.isEmpty()) {
            throw ApiException.conflict("В теме нет обработанных материалов. Загрузите PDF и дождитесь статуса READY.");
        }
        List<String> existing = cards.findByOutcomeIdOrderByIdAsc(outcome.getId()).stream()
                .map(QuestionCard::getQuestion).toList();

        String userText = AiMessages.cardGeneration(outcome.getCode(), outcome.getText(), existing, excerpts);
        CardDraft draft = ai.generateCard(Languages.promptName(course.getLanguage()), userText);

        QuestionCard card = new QuestionCard();
        card.setCourseId(course.getId());
        card.setTopicId(topic.getId());
        card.setOutcomeId(outcome.getId());
        card.setQuestion(draft.question().trim());
        card.setFundamentals(draft.fundamentals());
        card.setMisconceptions(draft.misconceptions());
        card.setFollowUpIdeas(cleanIdeas(draft.followUpIdeas()));
        card.setRubric(draft.rubric().toMap());
        card.setSourceChunkIds(checkedSources(draft.sourceChunkIds(), excerpts));
        card.setStatus(CardStatus.DRAFT);
        card.setAiGenerated(true);
        return cards.save(card);
    }

    /** Подсказки (HINT) в идеях карточки не храним: в экзамене они запрещены. */
    private static List<FollowUp> cleanIdeas(List<FollowUp> ideas) {
        return ideas.stream()
                .filter(i -> i != null && i.type() != null && i.text() != null && !i.text().isBlank())
                .filter(i -> i.type() != FollowUpType.HINT)
                .toList();
    }

    /**
     * Проверка источников: оставляем только те id, которые модель действительно получила.
     * Если она не назвала ни одного настоящего — источниками считаем все переданные фрагменты.
     */
    private static List<String> checkedSources(List<String> claimed, List<Excerpt> given) {
        Set<String> givenIds = given.stream().map(Excerpt::id).collect(Collectors.toSet());
        List<String> valid = claimed.stream().filter(givenIds::contains).distinct().toList();
        return valid.isEmpty() ? given.stream().map(Excerpt::id).toList() : valid;
    }
}
