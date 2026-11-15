package kz.vivamind.rag;

/** Фрагмент материала курса, найденный векторным поиском. */
public record Excerpt(String id, String fileName, Integer page, String text) {

    /** Формат, в котором фрагмент уходит в LLM (раздел 7.2 ТЗ). */
    public String toPromptTag() {
        String source = page == null ? fileName : fileName + " p." + page;   // у DOCX и PPTX страниц нет
        return "<excerpt id=\"%s\" source=\"%s\">%s</excerpt>".formatted(id, source, text);
    }
}
