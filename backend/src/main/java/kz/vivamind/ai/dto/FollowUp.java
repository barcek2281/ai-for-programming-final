package kz.vivamind.ai.dto;

/** Углубляющий вопрос: в карточке — идея на будущее, в TurnDecision — вопрос по словам студента. */
public record FollowUp(FollowUpType type, String text) {
}
