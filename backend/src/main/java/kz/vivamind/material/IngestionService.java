package kz.vivamind.material;

import kz.vivamind.ai.AiException;
import kz.vivamind.common.LlmCallLog;
import kz.vivamind.common.LlmCallLogRepository;
import kz.vivamind.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Фоновая обработка загруженного PDF (раздел 7.1 ТЗ):
 * S3 → PagePdfDocumentReader (страницы) → TokenTextSplitter (фрагменты) → VectorStore (эмбеддинги).
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final MaterialRepository materials;
    private final StorageService storage;
    private final VectorStore vectorStore;
    private final LlmCallLogRepository llmCalls;
    private final String embeddingModel;

    public IngestionService(MaterialRepository materials, StorageService storage, VectorStore vectorStore,
                            LlmCallLogRepository llmCalls,
                            @Value("${spring.ai.openai.embedding.model}") String embeddingModel) {
        this.materials = materials;
        this.storage = storage;
        this.vectorStore = vectorStore;
        this.llmCalls = llmCalls;
        this.embeddingModel = embeddingModel;
    }

    @Async
    public void ingest(Long materialId) {
        Material material = materials.findById(materialId).orElse(null);
        if (material == null) {
            return;
        }
        try {
            material.setStatus(Material.Status.PROCESSING);
            materials.save(material);

            List<Document> pages = readPages(material);
            if (pages.isEmpty()) {
                throw new IllegalStateException("В файле не найден текст. Возможно, это скан или слайды из одних картинок.");
            }
            for (Document page : pages) {
                page.getMetadata().put("courseId", material.getCourseId());
                page.getMetadata().put("topicId", material.getTopicId());
                page.getMetadata().put("materialId", material.getId());
                page.getMetadata().put("fileName", material.getFileName());
                // Номер страницы есть только у PDF; у DOCX и PPTX страниц нет.
                Object pageNumber = page.getMetadata().get(PagePdfDocumentReader.METADATA_START_PAGE_NUMBER);
                if (pageNumber != null) {
                    page.getMetadata().put("page", pageNumber);
                }
            }

            // Метаданные страницы переходят во все её фрагменты.
            List<Document> chunks = TokenTextSplitter.builder().build().apply(pages);
            // id фрагмента дублируем в метаданные, чтобы потом находить источники карточки через фильтр.
            chunks.forEach(chunk -> chunk.getMetadata().put("chunkId", chunk.getId()));

            embedAndStore(chunks);

            material.setPages(isPdf(material) ? pages.size() : null);
            material.setStatus(Material.Status.READY);
            material.setError(null);
            log.info("Material {} ingested: {} pages, {} chunks", materialId, pages.size(), chunks.size());
        } catch (Exception e) {
            log.warn("Material {} ingestion failed: {}", materialId, e.toString());
            material.setStatus(Material.Status.FAILED);
            String known = AiException.explain(e);
            if (known == null && String.valueOf(e.getMessage()).contains("TikaException")) {
                known = "Не удалось прочитать файл: возможно, он повреждён или защищён паролем. "
                        + "Попробуйте пересохранить его или загрузить как PDF.";
            }
            material.setError(known != null ? known
                    : shorten(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        // Материал могли удалить, пока он обрабатывался.
        if (materials.existsById(materialId)) {
            materials.save(material);
        }
    }

    /** Один Document на страницу PDF. */
    private List<Document> readPages(Material material) {
        byte[] pdf = storage.get(material.getS3Key());
        Resource resource = new ByteArrayResource(pdf) {
            @Override
            public String getFilename() {
                return material.getFileName();
            }
        };
        List<Document> raw;
        if (isPdf(material)) {
            PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder().withPagesPerDocument(1).build();
            raw = new PagePdfDocumentReader(resource, config).get();
        } else {
            raw = new TikaDocumentReader(resource).get();     // DOCX, PPTX: весь текст одним документом
        }
        return raw.stream()
                .map(page -> page.mutate().text(tidy(page.getText())).build())
                .filter(page -> !page.getText().isBlank())
                .toList();
    }

    private static boolean isPdf(Material material) {
        return material.getFileName().toLowerCase().endsWith(".pdf");
    }

    /** PDF-ридер сохраняет вёрстку страницы сериями пробелов. Для поиска и LLM они только мешают. */
    private static String tidy(String text) {
        return text.replace("\r", "")
                .replaceAll("[ \\t\\f\\u00A0]+", " ")       // серия пробелов → один пробел
                .replaceAll(" ?\\n ?", "\n")                 // пробелы вокруг перевода строки
                .replaceAll("\\n{3,}", "\n\n")               // не больше одной пустой строки подряд
                .strip();
    }

    /** Эмбеддинги считает сам Spring AI внутри vectorStore.add(). */
    private void embedAndStore(List<Document> chunks) {
        long started = System.currentTimeMillis();
        LlmCallLog call = new LlmCallLog();
        call.setPurpose(LlmCallLog.Purpose.EMBED);
        call.setModel(embeddingModel);
        try {
            vectorStore.add(chunks);
            call.setOk(true);
        } catch (RuntimeException e) {
            call.setOk(false);
            call.setError(e.getClass().getSimpleName());
            throw e;
        } finally {
            call.setLatencyMs((int) (System.currentTimeMillis() - started));
            llmCalls.save(call);
        }
    }

    private static String shorten(String text) {
        return text.length() > 500 ? text.substring(0, 500) + "…" : text;
    }
}
