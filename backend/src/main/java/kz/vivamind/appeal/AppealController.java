package kz.vivamind.appeal;

import jakarta.validation.Valid;
import kz.vivamind.appeal.AppealService.AppealRequest;
import kz.vivamind.appeal.AppealService.AppealRow;
import kz.vivamind.appeal.AppealService.ResolveRequest;
import kz.vivamind.auth.AuthUser;
import kz.vivamind.grading.SessionView.AppealDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AppealController {

    private final AppealService appealService;

    public AppealController(AppealService appealService) {
        this.appealService = appealService;
    }

    @PostMapping("/sessions/{id}/appeal")
    @PreAuthorize("hasRole('STUDENT')")
    public AppealDto submit(@AuthenticationPrincipal AuthUser student, @PathVariable Long id,
                            @Valid @RequestBody AppealRequest request) {
        return appealService.submit(id, student, request);
    }

    @GetMapping("/appeals")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AppealRow> list(@AuthenticationPrincipal AuthUser teacher,
                                @RequestParam(required = false) Appeal.Status status) {
        return appealService.listForTeacher(teacher, status);
    }

    @PostMapping("/appeals/{id}/resolve")
    @PreAuthorize("hasRole('TEACHER')")
    public AppealRow resolve(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                             @Valid @RequestBody ResolveRequest request) {
        return appealService.resolve(id, teacher, request);
    }
}
