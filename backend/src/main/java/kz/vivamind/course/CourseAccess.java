package kz.vivamind.course;

import kz.vivamind.auth.AuthUser;
import kz.vivamind.common.ApiException;
import org.springframework.stereotype.Service;

/** Единая проверка «преподаватель видит только свои курсы». Возвращает объект или бросает 404/403. */
@Service
public class CourseAccess {

    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final LearningOutcomeRepository outcomes;

    public CourseAccess(CourseRepository courses, CourseTopicRepository topics, LearningOutcomeRepository outcomes) {
        this.courses = courses;
        this.topics = topics;
        this.outcomes = outcomes;
    }

    public Course course(Long courseId, AuthUser teacher) {
        Course course = courses.findById(courseId).orElseThrow(() -> ApiException.notFound("Курс не найден"));
        if (!course.getTeacherId().equals(teacher.id())) {
            throw ApiException.forbidden("Это не ваш курс");
        }
        return course;
    }

    public CourseTopic topic(Long topicId, AuthUser teacher) {
        CourseTopic topic = topics.findById(topicId).orElseThrow(() -> ApiException.notFound("Тема не найдена"));
        course(topic.getCourseId(), teacher);
        return topic;
    }

    public LearningOutcome outcome(Long outcomeId, AuthUser teacher) {
        LearningOutcome outcome = outcomes.findById(outcomeId)
                .orElseThrow(() -> ApiException.notFound("Цель обучения не найдена"));
        topic(outcome.getTopicId(), teacher);
        return outcome;
    }
}
