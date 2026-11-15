package kz.vivamind.grading;

import kz.vivamind.auth.AuthUser;
import kz.vivamind.grading.ReportService.HistoryRow;
import kz.vivamind.grading.ReportService.ReportDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('STUDENT')")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/sessions/{id}/report")
    public ReportDto report(@AuthenticationPrincipal AuthUser student, @PathVariable Long id) {
        return reportService.report(id, student);
    }

    @GetMapping("/me/sessions")
    public List<HistoryRow> history(@AuthenticationPrincipal AuthUser student) {
        return reportService.history(student);
    }
}
