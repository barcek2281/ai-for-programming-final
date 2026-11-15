package kz.vivamind.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LearningOutcomeRepository extends JpaRepository<LearningOutcome, Long> {

    List<LearningOutcome> findByTopicIdOrderByPositionAscIdAsc(Long topicId);

    int countByTopicId(Long topicId);
}
