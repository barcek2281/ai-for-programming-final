package kz.vivamind.exam;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.exam.ExamService.ExamDto;
import kz.vivamind.exam.ExamService.ExamIntro;
import kz.vivamind.exam.ExamService.ExamRequest;
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

@RestController
@RequestMapping("/api/v1")
public class ExamController {

    public record JoinRequest(@NotBlank String accessCode) {
    }

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    // ---------- Преподаватель ----------

    @PostMapping("/courses/{id}/exams")
    @PreAuthorize("hasRole('TEACHER')")
    public ExamDto create(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                          @Valid @RequestBody ExamRequest request) {
        return examService.create(id, teacher, request);
    }

    @GetMapping("/courses/{id}/exams")
    @PreAuthorize("hasRole('TEACHER')")
    public List<ExamDto> list(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return examService.list(id, teacher);
    }

    @GetMapping("/exams/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ExamDto get(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return examService.get(id, teacher);
    }

    @PutMapping("/exams/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public ExamDto update(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                          @Valid @RequestBody ExamRequest request) {
        return examService.update(id, teacher, request);
    }

    @DeleteMapping("/exams/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public void delete(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        examService.delete(id, teacher);
    }

    @PostMapping("/exams/{id}/publish")
    @PreAuthorize("hasRole('TEACHER')")
    public ExamDto publish(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return examService.publish(id, teacher);
    }

    @PostMapping("/exams/{id}/close")
    @PreAuthorize("hasRole('TEACHER')")
    public ExamDto close(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return examService.close(id, teacher);
    }

    // ---------- Студент ----------

    @PostMapping("/exams/join")
    @PreAuthorize("hasRole('STUDENT')")
    public ExamIntro join(@AuthenticationPrincipal AuthUser student, @Valid @RequestBody JoinRequest request) {
        return examService.join(request.accessCode(), student);
    }
}
