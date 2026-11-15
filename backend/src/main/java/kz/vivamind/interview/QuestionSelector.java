package kz.vivamind.interview;

import kz.vivamind.card.CardStatus;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.course.CourseTopicRepository;
import kz.vivamind.exam.Exam;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/**
 * Выбор вопросов для сессии (раздел 3.3 ТЗ): только утверждённые карточки выбранных тем,
 * по одной случайной карточке на разные цели обучения, поровну по темам.
 * Случайность с seed = id сессии, поэтому у каждого студента свой путь вопросов.
 */
@Component
public class QuestionSelector {

    private final QuestionCardRepository cards;
    private final CourseTopicRepository topics;

    public QuestionSelector(QuestionCardRepository cards, CourseTopicRepository topics) {
        this.cards = cards;
        this.topics = topics;
    }

    public List<QuestionCard> select(Exam exam, long seed) {
        Random random = new Random(seed);

        // тема → цель обучения → её утверждённые карточки (TreeMap — чтобы результат зависел только от seed)
        Map<Long, Map<Long, List<QuestionCard>>> byTopic = new TreeMap<>();
        for (QuestionCard card : cards.findByTopicIdInAndStatusOrderByIdAsc(exam.getTopicIds(), CardStatus.APPROVED)) {
            byTopic.computeIfAbsent(card.getTopicId(), t -> new TreeMap<>())
                    .computeIfAbsent(card.getOutcomeId(), o -> new ArrayList<>())
                    .add(card);
        }

        // Для каждой темы — очередь её целей обучения в случайном порядке.
        List<Deque<List<QuestionCard>>> topicQueues = new ArrayList<>();
        for (Map<Long, List<QuestionCard>> outcomesOfTopic : byTopic.values()) {
            List<List<QuestionCard>> outcomes = new ArrayList<>(outcomesOfTopic.values());
            Collections.shuffle(outcomes, random);
            topicQueues.add(new ArrayDeque<>(outcomes));
        }
        Collections.shuffle(topicQueues, random);   // «лишний» вопрос достаётся разным темам

        // Идём по темам по кругу и берём по одной цели, пока не наберём нужное число вопросов.
        List<QuestionCard> picked = new ArrayList<>();
        boolean tookSomething = true;
        while (picked.size() < exam.getQuestionsCount() && tookSomething) {
            tookSomething = false;
            for (Deque<List<QuestionCard>> queue : topicQueues) {
                List<QuestionCard> cardsOfOutcome = queue.poll();
                if (cardsOfOutcome == null || picked.size() >= exam.getQuestionsCount()) {
                    continue;
                }
                picked.add(cardsOfOutcome.get(random.nextInt(cardsOfOutcome.size())));
                tookSomething = true;
            }
        }

        // Интервью идёт по темам в порядке курса.
        Map<Long, Integer> topicOrder = new HashMap<>();
        for (CourseTopic topic : topics.findAllById(exam.getTopicIds())) {
            topicOrder.put(topic.getId(), topic.getPosition());
        }
        picked.sort(Comparator.comparingInt((QuestionCard c) -> topicOrder.getOrDefault(c.getTopicId(), 0))
                .thenComparing(QuestionCard::getTopicId));
        return picked;
    }
}
