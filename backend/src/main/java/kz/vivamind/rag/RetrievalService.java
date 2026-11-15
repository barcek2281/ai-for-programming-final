package kz.vivamind.rag;

import kz.vivamind.ai.AiException;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Поиск по материалам ОДНОЙ темы курса. Весь векторный поиск делает Spring AI VectorStore. */
@Service
public class RetrievalService {

    private final VectorStore vectorStore;

    public RetrievalService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /** top-k фрагментов темы, ближайших к запросу. */
    public List<Excerpt> search(Long topicId, String query, int k) {
        var b = new FilterExpressionBuilder();
        return run(query, k, b.eq("topicId", topicId).build());
    }

    /** Конкретные фрагменты темы по их id (источники карточки). */
    public List<Excerpt> byChunkIds(Long topicId, List<String> chunkIds, String query) {
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        var b = new FilterExpressionBuilder();
        Filter.Expression filter = b.and(b.eq("topicId", topicId), b.in("chunkId", new ArrayList<Object>(chunkIds))).build();
        return run(query, chunkIds.size(), filter);
    }

    private List<Excerpt> run(String query, int k, Filter.Expression filter) {
        List<Document> docs;
        try {
            docs = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(query)
                    .topK(k)
                    .filterExpression(filter)
                    .build());
        } catch (RuntimeException e) {
            // Запрос сначала превращается в эмбеддинг через OpenRouter — он может быть недоступен.
            throw new AiException("Vector search failed", e);
        }
        return docs.stream().map(RetrievalService::toExcerpt).toList();
    }

    private static Excerpt toExcerpt(Document doc) {
        Object page = doc.getMetadata().get("page");
        return new Excerpt(doc.getId(),
                String.valueOf(doc.getMetadata().get("fileName")),
                page instanceof Number n ? n.intValue() : null,
                doc.getText());
    }
}
