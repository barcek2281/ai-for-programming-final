package kz.vivamind.ai;

import kz.vivamind.ai.dto.FollowUp;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.exam.Exam;
import kz.vivamind.interview.Message;
import kz.vivamind.rag.Excerpt;

import java.util.List;

/**
 * Собирает user-сообщения для LLM. Это ЕДИНСТВЕННОЕ место, где формируется то, что уходит в модель:
 * материалы курса, карточка вопроса и текст диалога. ФИО, email и ID студента сюда не попадают.
 * Ответы студента оборачиваются в теги student_answer (защита от prompt injection).
 */
public final class AiMessages {

    private AiMessages() {
    }

    /** Вопрос, оценённый ИИ, — вход для итогового отчёта. */
    public record GradedQuestion(String topicTitle, String question, Integer soloLevel, String rationale,
                                 List<String> coveredFundamentals, List<String> missingFundamentals) {
    }

    // ---------- CARD_GEN ----------

    public static String cardGeneration(String outcomeCode, String outcomeText, List<String> existingQuestions,
                                        List<Excerpt> excerpts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Learning outcome ").append(outcomeCode).append(": ").append(outcomeText).append("\n\n");
        if (!existingQuestions.isEmpty()) {
            sb.append("Existing questions for this outcome (write a different one):\n");
            existingQuestions.forEach(q -> sb.append("- ").append(q).append('\n'));
            sb.append('\n');
        }
        sb.append(excerptsBlock(excerpts));
        return sb.toString();
    }

    // ---------- TURN ----------

    public static String turn(Exam.Mode mode, QuestionCard card, List<Excerpt> excerpts, List<Message> dialogue,
                              int followupsAsked, int maxFollowups) {
        StringBuilder sb = new StringBuilder();
        sb.append("Mode: ").append(mode).append('\n');
        sb.append("Follow-ups asked so far: ").append(followupsAsked).append(" of max ").append(maxFollowups)
                .append("\n\n");
        sb.append("<card>\n");
        sb.append("Question: ").append(card.getQuestion()).append('\n');
        sb.append(bullets("Fundamentals", card.getFundamentals()));
        sb.append(bullets("Misconceptions", card.getMisconceptions()));
        sb.append("Follow-up ideas:\n");
        for (FollowUp idea : card.getFollowUpIdeas()) {
            sb.append("- [").append(idea.type()).append("] ").append(idea.text()).append('\n');
        }
        sb.append("</card>\n\n");
        sb.append(excerptsBlock(excerpts)).append('\n');
        sb.append(dialogueBlock(dialogue));
        return sb.toString();
    }

    // ---------- QUESTION_GRADE ----------

    public static String questionGrade(QuestionCard card, List<Excerpt> excerpts, List<Message> dialogue) {
        StringBuilder sb = new StringBuilder();
        sb.append("<card>\n");
        sb.append("Question: ").append(card.getQuestion()).append('\n');
        sb.append(bullets("Fundamentals", card.getFundamentals()));
        sb.append(bullets("Misconceptions", card.getMisconceptions()));
        sb.append("Rubric (SOLO levels):\n");
        card.getRubric().forEach((level, text) -> sb.append(level).append(": ").append(text).append('\n'));
        sb.append("</card>\n\n");
        sb.append(excerptsBlock(excerpts)).append('\n');
        sb.append(dialogueBlock(dialogue));
        return sb.toString();
    }

    // ---------- SUMMARY ----------

    public static String summary(List<GradedQuestion> questions, List<Excerpt> references) {
        StringBuilder sb = new StringBuilder("<graded_questions>\n");
        for (GradedQuestion q : questions) {
            sb.append("<question topic=\"").append(q.topicTitle()).append("\" solo_level=\"")
                    .append(q.soloLevel() == null ? "not graded" : q.soloLevel()).append("\">\n");
            sb.append("Question: ").append(q.question()).append('\n');
            if (q.rationale() != null) {
                sb.append("Grader rationale: ").append(q.rationale()).append('\n');
            }
            sb.append(bullets("Shown fundamentals", q.coveredFundamentals()));
            sb.append(bullets("Missing fundamentals", q.missingFundamentals()));
            sb.append("</question>\n");
        }
        sb.append("</graded_questions>\n\n<references>\n");
        for (Excerpt ref : references) {
            sb.append("<reference source=\"").append(ref.fileName()).append("\" page=\"")
                    .append(ref.page() == null ? "" : ref.page()).append("\">").append(shorten(ref.text(), 400)).append("</reference>\n");
        }
        sb.append("</references>\n");
        return sb.toString();
    }

    // ---------- общие блоки ----------

    private static String excerptsBlock(List<Excerpt> excerpts) {
        StringBuilder sb = new StringBuilder("<excerpts>\n");
        excerpts.forEach(e -> sb.append(e.toPromptTag()).append('\n'));
        return sb.append("</excerpts>\n").toString();
    }

    /** Диалог только текущего вопроса. Слова студента — всегда внутри student_answer. */
    private static String dialogueBlock(List<Message> dialogue) {
        StringBuilder sb = new StringBuilder("<dialogue>\n");
        for (Message m : dialogue) {
            if (m.fromStudent()) {
                sb.append("<student_answer kind=\"").append(m.getKind()).append("\">")
                        .append(escape(m.getText())).append("</student_answer>\n");
            } else {
                sb.append("<examiner kind=\"").append(m.getKind()).append("\">")
                        .append(m.getText()).append("</examiner>\n");
            }
        }
        return sb.append("</dialogue>\n").toString();
    }

    private static String bullets(String title, List<String> items) {
        StringBuilder sb = new StringBuilder(title).append(":\n");
        if (items == null || items.isEmpty()) {
            return sb.append("- (none)\n").toString();
        }
        items.forEach(item -> sb.append("- ").append(item).append('\n'));
        return sb.toString();
    }

    /** Студент не должен «закрыть» тег и выдать свой текст за инструкцию или реплику экзаменатора. */
    public static String escape(String studentText) {
        return studentText.replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String shorten(String text, int max) {
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > max ? oneLine.substring(0, max) + "…" : oneLine;
    }
}
