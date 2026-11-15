package kz.vivamind.course;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.card.CardStatus;
import kz.vivamind.card.QuestionCardRepository;
import kz.vivamind.common.ApiException;
import kz.vivamind.common.Languages;
import kz.vivamind.material.Material;
import kz.vivamind.material.MaterialRepository;
import kz.vivamind.material.MaterialService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Курсы, темы курса и цели обучения — рабочее место преподавателя. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TEACHER')")
public class CourseController {

    public record CourseRequest(@NotBlank @Size(max = 255) String title, String description,
                                @NotBlank String language) {
    }

    public record TopicRequest(@NotBlank @Size(max = 255) String title, String description, Integer position) {
    }

    public record OutcomeRequest(@Size(max = 20) String code, @NotBlank String text, Integer position) {
    }

    /** Тема курса вместе со счётчиками — чтобы в списке было видно, готова ли она к экзамену. */
    public record TopicDto(Long id, Long courseId, String title, String description, int position,
                           int outcomesCount, int materialsCount, int cardsCount, int approvedCardsCount,
                           String courseTitle, String courseLanguage) {
    }

    private final CourseRepository courses;
    private final CourseTopicRepository topics;
    private final LearningOutcomeRepository outcomes;
    private final MaterialRepository materials;
    private final QuestionCardRepository cards;
    private final CourseAccess access;
    private final MaterialService materialService;

    public CourseController(CourseRepository courses, CourseTopicRepository topics,
                            LearningOutcomeRepository outcomes, MaterialRepository materials,
                            QuestionCardRepository cards, CourseAccess access, MaterialService materialService) {
        this.courses = courses;
        this.topics = topics;
        this.outcomes = outcomes;
        this.materials = materials;
        this.cards = cards;
        this.access = access;
        this.materialService = materialService;
    }

    // ---------- Курсы ----------

    @PostMapping("/courses")
    public Course createCourse(@AuthenticationPrincipal AuthUser teacher, @Valid @RequestBody CourseRequest request) {
        Course course = new Course();
        course.setTeacherId(teacher.id());
        apply(course, request);
        return courses.save(course);
    }

    @GetMapping("/courses")
    public List<Course> myCourses(@AuthenticationPrincipal AuthUser teacher) {
        return courses.findByTeacherIdOrderByCreatedAtDesc(teacher.id());
    }

    @GetMapping("/courses/{id}")
    public Course course(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return access.course(id, teacher);
    }

    @PutMapping("/courses/{id}")
    public Course updateCourse(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                               @Valid @RequestBody CourseRequest request) {
        Course course = access.course(id, teacher);
        apply(course, request);
        return courses.save(course);
    }

    private void apply(Course course, CourseRequest request) {
        if (!Languages.isSupported(request.language())) {
            throw ApiException.badRequest("Язык курса: kk, ru или en");
        }
        course.setTitle(request.title().trim());
        course.setDescription(request.description());
        course.setLanguage(request.language());
    }

    // ---------- Темы курса ----------

    @PostMapping("/courses/{id}/topics")
    public TopicDto createTopic(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                @Valid @RequestBody TopicRequest request) {
        Course course = access.course(id, teacher);
        CourseTopic topic = new CourseTopic();
        topic.setCourseId(course.getId());
        topic.setTitle(request.title().trim());
        topic.setDescription(request.description());
        topic.setPosition(request.position() != null ? request.position() : topics.countByCourseId(id) + 1);
        return toDto(topics.save(topic), course);
    }

    @GetMapping("/courses/{id}/topics")
    public List<TopicDto> topics(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        Course course = access.course(id, teacher);
        return topics.findByCourseIdOrderByPositionAscIdAsc(id).stream().map(t -> toDto(t, course)).toList();
    }

    @GetMapping("/topics/{id}")
    public TopicDto topic(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        CourseTopic topic = access.topic(id, teacher);
        return toDto(topic, access.course(topic.getCourseId(), teacher));
    }

    @PutMapping("/topics/{id}")
    public TopicDto updateTopic(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                @Valid @RequestBody TopicRequest request) {
        CourseTopic topic = access.topic(id, teacher);
        topic.setTitle(request.title().trim());
        topic.setDescription(request.description());
        if (request.position() != null) {
            topic.setPosition(request.position());
        }
        return toDto(topics.save(topic), access.course(topic.getCourseId(), teacher));
    }

    @DeleteMapping("/topics/{id}")
    public void deleteTopic(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        CourseTopic topic = access.topic(id, teacher);
        // Сначала запись в БД (каскадом уйдут цели, карточки, материалы). Если тема уже использована
        // в интервью, БД откажет — и файлы с фрагментами останутся на месте.
        List<Material> topicMaterials = materials.findByTopicIdOrderByUploadedAtDesc(id);
        topics.delete(topic);
        topics.flush();
        topicMaterials.forEach(materialService::deleteStoredData);
    }

    private TopicDto toDto(CourseTopic topic, Course course) {
        Long id = topic.getId();
        return new TopicDto(id, topic.getCourseId(), topic.getTitle(), topic.getDescription(), topic.getPosition(),
                outcomes.countByTopicId(id), materials.countByTopicId(id), cards.countByTopicId(id),
                cards.countByTopicIdAndStatus(id, CardStatus.APPROVED), course.getTitle(), course.getLanguage());
    }

    // ---------- Цели обучения ----------

    @PostMapping("/topics/{id}/outcomes")
    public LearningOutcome createOutcome(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                         @Valid @RequestBody OutcomeRequest request) {
        access.topic(id, teacher);
        int next = outcomes.countByTopicId(id) + 1;
        LearningOutcome outcome = new LearningOutcome();
        outcome.setTopicId(id);
        outcome.setCode(hasText(request.code()) ? request.code().trim() : "LO" + next);
        outcome.setText(request.text().trim());
        outcome.setPosition(request.position() != null ? request.position() : next);
        return outcomes.save(outcome);
    }

    @GetMapping("/topics/{id}/outcomes")
    public List<LearningOutcome> outcomes(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        access.topic(id, teacher);
        return outcomes.findByTopicIdOrderByPositionAscIdAsc(id);
    }

    @PutMapping("/outcomes/{id}")
    public LearningOutcome updateOutcome(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                         @Valid @RequestBody OutcomeRequest request) {
        LearningOutcome outcome = access.outcome(id, teacher);
        if (hasText(request.code())) {
            outcome.setCode(request.code().trim());
        }
        outcome.setText(request.text().trim());
        if (request.position() != null) {
            outcome.setPosition(request.position());
        }
        return outcomes.save(outcome);
    }

    @DeleteMapping("/outcomes/{id}")
    public void deleteOutcome(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        outcomes.delete(access.outcome(id, teacher));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
