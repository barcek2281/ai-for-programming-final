package kz.vivamind.interview;

import kz.vivamind.auth.AuthUser;
import kz.vivamind.interview.InterviewService.AnswerRequest;
import kz.vivamind.interview.InterviewService.AnswerResponse;
import kz.vivamind.interview.InterviewService.SessionState;
import kz.vivamind.interview.InterviewService.StartRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Интервью со стороны студента. Студент видит только свои сессии. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('STUDENT')")
public class InterviewController {

    private final InterviewService interviewService;

    public InterviewController(InterviewService interviewService) {
        this.interviewService = interviewService;
    }

    /** {consent: true, accessCode} → сессия + первый вопрос. */
    @PostMapping("/exams/{id}/sessions")
    public SessionState start(@AuthenticationPrincipal AuthUser student, @PathVariable Long id,
                              @RequestBody StartRequest request) {
        return interviewService.start(id, student, request);
    }

    @GetMapping("/sessions/{id}")
    public SessionState state(@AuthenticationPrincipal AuthUser student, @PathVariable Long id) {
        return interviewService.state(id, student);
    }

    @PostMapping("/sessions/{id}/answers")
    public AnswerResponse answer(@AuthenticationPrincipal AuthUser student, @PathVariable Long id,
                                 @RequestBody AnswerRequest request) {
        return interviewService.handleAnswer(id, student, request);
    }
}
