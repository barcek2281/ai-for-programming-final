package kz.vivamind.material;

import kz.vivamind.common.ApiException;
import kz.vivamind.course.CourseTopic;
import kz.vivamind.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

@Service
public class MaterialService {

    private static final Logger log = LoggerFactory.getLogger(MaterialService.class);

    private final MaterialRepository materials;
    private final StorageService storage;
    private final VectorStore vectorStore;
    private final IngestionService ingestion;

    public MaterialService(MaterialRepository materials, StorageService storage, VectorStore vectorStore,
                           IngestionService ingestion) {
        this.materials = materials;
        this.storage = storage;
        this.vectorStore = vectorStore;
        this.ingestion = ingestion;
    }

    /** Кладёт PDF в S3, сохраняет запись UPLOADED и запускает фоновую обработку. */
    public Material upload(CourseTopic topic, MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Не удалось прочитать файл");
        }
        String fileName = cleanFileName(file.getOriginalFilename());
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        String contentType = CONTENT_TYPES.get(extension);
        // PDF начинается с %PDF, а DOCX и PPTX — это zip-архивы (PK).
        boolean signatureOk = "pdf".equals(extension) ? startsWith(content, "%PDF") : startsWith(content, "PK");
        if (contentType == null || !signatureOk) {
            throw ApiException.badRequest("Поддерживаются файлы PDF, DOCX и PPTX");
        }
        String key = "materials/%d/%d/%s.%s".formatted(topic.getCourseId(), topic.getId(), UUID.randomUUID(), extension);
        try {
            storage.put(key, content, contentType);
        } catch (RuntimeException e) {
            log.warn("S3 upload failed: {}", e.toString());
            throw ApiException.unavailable("Хранилище файлов недоступно. Проверьте, что SeaweedFS запущен.");
        }

        Material material = new Material();
        material.setCourseId(topic.getCourseId());
        material.setTopicId(topic.getId());
        material.setFileName(fileName);
        material.setS3Key(key);
        material.setStatus(Material.Status.UPLOADED);
        material = materials.save(material);

        ingestion.ingest(material.getId());   // @Async — запрос не ждёт
        return material;
    }

    public void delete(Material material) {
        materials.delete(material);
        deleteStoredData(material);
    }

    /** Удаляет фрагменты из vector_store и файл из S3. Ошибки не мешают удалению записи. */
    public void deleteStoredData(Material material) {
        try {
            vectorStore.delete(new FilterExpressionBuilder().eq("materialId", material.getId()).build());
        } catch (RuntimeException e) {
            log.warn("Could not delete chunks of material {}: {}", material.getId(), e.toString());
        }
        try {
            storage.delete(material.getS3Key());
        } catch (RuntimeException e) {
            log.warn("Could not delete S3 object of material {}: {}", material.getId(), e.toString());
        }
    }

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation");

    private static boolean startsWith(byte[] content, String signature) {
        return content.length > signature.length()
                && new String(content, 0, signature.length(), StandardCharsets.US_ASCII).equals(signature);
    }

    private static String cleanFileName(String original) {
        String name = (original == null || original.isBlank()) ? "material.pdf" : original;
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }
}
