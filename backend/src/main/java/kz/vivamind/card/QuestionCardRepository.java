package kz.vivamind.card;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface QuestionCardRepository extends JpaRepository<QuestionCard, Long> {

    List<QuestionCard> findByTopicIdOrderByIdDesc(Long topicId);

    List<QuestionCard> findByTopicIdAndStatusOrderByIdDesc(Long topicId, CardStatus status);

    List<QuestionCard> findByTopicIdInAndStatusOrderByIdAsc(Collection<Long> topicIds, CardStatus status);

    List<QuestionCard> findByOutcomeIdOrderByIdAsc(Long outcomeId);

    int countByTopicId(Long topicId);

    int countByTopicIdAndStatus(Long topicId, CardStatus status);
}
