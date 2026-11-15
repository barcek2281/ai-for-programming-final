package kz.vivamind.interview;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {

    /** Строка сессии с блокировкой — чтобы два запроса не меняли её одновременно. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InterviewSession s where s.id = :id")
    Optional<InterviewSession> findByIdForUpdate(@Param("id") Long id);

    List<InterviewSession> findByExamIdOrderByStartedAtDesc(Long examId);

    List<InterviewSession> findByStudentIdOrderByStartedAtDesc(Long studentId);

    List<InterviewSession> findByExamIdAndStudentIdOrderByStartedAtDesc(Long examId, Long studentId);

    List<InterviewSession> findByExamIdAndStatus(Long examId, InterviewSession.Status status);

    int countByExamId(Long examId);

    /** Точечное обновление одного флага из фоновой оценки (не трогает остальные колонки). */
    @Transactional
    @Modifying
    @Query("update InterviewSession s set s.needsReview = true where s.id = :id")
    void markNeedsReview(@Param("id") Long id);
}
