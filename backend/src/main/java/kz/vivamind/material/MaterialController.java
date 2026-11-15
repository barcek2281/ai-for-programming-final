package kz.vivamind.material;

import kz.vivamind.auth.AuthUser;
import kz.vivamind.common.ApiException;
import kz.vivamind.course.CourseAccess;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.rag.Excerpt;
import kz.vivamind.rag.RetrievalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('TEACHER')")
public class MaterialController {

    private final MaterialRepository materials;
    private final MaterialService materialService;
    private final RetrievalService retrieval;
    private final CourseAccess access;

    public MaterialController(MaterialRepository materials, MaterialService materialService,
                              RetrievalService retrieval, CourseAccess access) {
        this.materials = materials;
        this.materialService = materialService;
        this.retrieval = retrieval;
        this.access = access;
    }

    @PostMapping("/topics/{id}/materials")
    public Material upload(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                           @RequestParam("file") MultipartFile file) {
        CourseTopic topic = access.topic(id, teacher);
        return materialService.upload(topic, file);
    }

    @GetMapping("/topics/{id}/materials")
    public List<Material> list(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        access.topic(id, teacher);
        return materials.findByTopicIdOrderByUploadedAtDesc(id);
    }

    @DeleteMapping("/materials/{id}")
    public void delete(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id) {
        Material material = materials.findById(id).orElseThrow(() -> ApiException.notFound("Материал не найден"));
        access.topic(material.getTopicId(), teacher);
        materialService.delete(material);
    }

    /** Отладка RAG: top-5 фрагментов темы по запросу. */
    @GetMapping("/topics/{id}/search")
    public List<Excerpt> search(@AuthenticationPrincipal AuthUser teacher, @PathVariable Long id,
                                @RequestParam("q") String query) {
        access.topic(id, teacher);
        if (query.isBlank()) {
            throw ApiException.badRequest("Пустой запрос");
        }
        return retrieval.search(id, query, 5);
    }
}
