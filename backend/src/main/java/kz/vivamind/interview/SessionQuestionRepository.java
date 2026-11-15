package kz.vivamind.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SessionQuestionRepository extends JpaRepository<SessionQuestion, Long> {

    List<SessionQuestion> findBySessionIdOrderByPosition(Long sessionId);

    List<SessionQuestion> findBySessionIdInOrderBySessionIdAscPositionAsc(Collection<Long> sessionIds);

    Optional<SessionQuestion> findBySessionIdAndPosition(Long sessionId, int position);
}
