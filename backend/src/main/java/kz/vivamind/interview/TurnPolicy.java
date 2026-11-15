package kz.vivamind.interview;

import kz.vivamind.ai.dto.FollowUp;
import kz.vivamind.ai.dto.FollowUpType;
import kz.vivamind.ai.dto.TurnDecision;
import kz.vivamind.ai.dto.TurnDecision.Understanding;
import kz.vivamind.card.QuestionCard;
import kz.vivamind.common.Languages;
import kz.vivamind.exam.Exam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Правила перехода между репликами (раздел 3.1 ТЗ). Обычный детерминированный Java-код:
 * LLM только ПРЕДЛАГАЕТ действие, а углублять или закрывать вопрос — решается здесь.
 */
@Component
public class TurnPolicy {

    public enum Action {FOLLOW_UP, CLOSE_QUESTION}

    public record Result(Action action, int absentStreak) {
    }

    public Result decide(SessionQuestion question, TurnDecision decision, Exam exam) {
        int absentStreak = decision.understanding() == Understanding.ABSENT ? question.getAbsentStreak() + 1 : 0;

        if (question.getFollowupsAsked() >= exam.getMaxFollowups()) {
            return new Result(Action.CLOSE_QUESTION, absentStreak);          // лимит углублений исчерпан
        }
        if (absentStreak >= 2) {
            return new Result(Action.CLOSE_QUESTION, absentStreak);          // «нет понимания» два раза подряд
        }
        if (decision.understanding() == Understanding.CONFIRMED
                && question.getFollowupsAsked() >= exam.getMinFollowups()) {
            return new Result(Action.CLOSE_QUESTION, absentStreak);          // понимание подтверждено после углубления
        }
        return new Result(Action.FOLLOW_UP, absentStreak);
    }

    /**
     * Текст углубления: вопрос от LLM → иначе неиспользованная идея из карточки → иначе шаблон WHY.
     * В режиме EXAM подсказки (HINT) запрещены и заменяются шаблоном WHY.
     */
    public FollowUp chooseFollowUp(TurnDecision decision, QuestionCard card, List<String> alreadyAsked,
                                   Exam.Mode mode, String language) {
        FollowUp proposed = decision.followUp();
        if (proposed != null && proposed.text() != null && !proposed.text().isBlank()) {
            FollowUpType type = proposed.type() != null ? proposed.type() : FollowUpType.WHY;
            if (type == FollowUpType.HINT && mode == Exam.Mode.EXAM) {
                return new FollowUp(FollowUpType.WHY, Languages.whyTemplate(language));
            }
            return new FollowUp(type, proposed.text().trim());
        }
        for (FollowUp idea : card.getFollowUpIdeas()) {
            if (idea.type() != FollowUpType.HINT && !alreadyAsked.contains(idea.text())) {
                return idea;
            }
        }
        return new FollowUp(FollowUpType.WHY, Languages.whyTemplate(language));
    }

    /** Значение плейсхолдера {allowedTypes} в промпте turn.st. */
    public static String allowedTypes(Exam.Mode mode) {
        String base = "WHY, EXAMPLE, WHAT_IF, FIND_ERROR";
        return mode == Exam.Mode.PRACTICE ? base + ", HINT" : base;
    }
}
