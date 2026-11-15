package kz.vivamind.grading;

import jakarta.validation.Valid;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.grading.ReviewService.ConfirmRequest;
import kz.vivamind.grading.ReviewService.LevelRequest;
import kz.vivamind.grading.ReviewService.ReviewDto;
import kz.vivamind.grading.ReviewService.SessionRow;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TEACHER')")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/exams/{id}/sessions")
    public List<SessionRow> sessions(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return reviewService.sessionsOfExam(id, teacher);
    }

    @GetMapping("/sessions/{id}/review")
    public ReviewDto review(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        return reviewService.review(id, teacher);
    }

    @PutMapping("/sessions/{id}/questions/{questionId}")
    public ReviewDto setLevel(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                              @PathVariable Long questionId, @Valid @RequestBody LevelRequest request) {
        return reviewService.setLevel(id, questionId, teacher, request);
    }

    @PostMapping("/sessions/{id}/confirm")
    public ReviewDto confirm(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                             @RequestBody(required = false) ConfirmRequest request) {
        return reviewService.confirm(id, teacher, request);
    }

    @PostMapping("/sessions/{id}/regrade")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void regrade(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        reviewService.regrade(id, teacher);
    }
}
