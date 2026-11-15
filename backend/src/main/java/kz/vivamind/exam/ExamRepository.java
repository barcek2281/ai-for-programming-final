package kz.vivamind.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExamRepository extends JpaRepository<Exam, Long> {

    List<Exam> findByCourseIdOrderByCreatedAtDesc(Long courseId);

    Optional<Exam> findByAccessCode(String accessCode);

    boolean existsByAccessCode(String accessCode);
}
