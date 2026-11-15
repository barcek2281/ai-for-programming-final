package kz.vivamind.course;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseTopicRepository extends JpaRepository<CourseTopic, Long> {

    List<CourseTopic> findByCourseIdOrderByPositionAscIdAsc(Long courseId);

    int countByCourseId(Long courseId);
}
