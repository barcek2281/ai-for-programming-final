package kz.vivamind.appeal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppealRepository extends JpaRepository<Appeal, Long> {

    Optional<Appeal> findFirstBySessionIdOrderByIdDesc(Long sessionId);

    List<Appeal> findBySessionIdInAndStatus(Collection<Long> sessionIds, Appeal.Status status);

    /** Апелляции по сессиям курсов одного преподавателя. */
    @Query("""
            select a from Appeal a, InterviewSession s, Exam e, Course c
            where a.sessionId = s.id and s.examId = e.id and e.courseId = c.id
              and c.teacherId = :teacherId
            order by a.createdAt desc
            """)
    List<Appeal> findForTeacher(@Param("teacherId") Long teacherId);
}
