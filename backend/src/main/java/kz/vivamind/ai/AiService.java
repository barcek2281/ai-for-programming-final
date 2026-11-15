package kz.vivamind.ai;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import kz.vivamind.ai.dto.CardDraft;
import kz.vivamind.ai.dto.QuestionGrade;
import kz.vivamind.ai.dto.SessionSummary;
import kz.vivamind.ai.dto.TurnDecision;
import kz.vivamind.common.LlmCallLog;
import kz.vivamind.common.LlmCallLog.Purpose;
import kz.vivamind.common.LlmCallLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Все вызовы LLM в одном месте (раздел 8 ТЗ). У каждого вызова — своя модель и температура.
 * Системный промпт — шаблон из resources/prompts, сообщение пользователя — готовая строка из AiMessages.
 * Ответ модели сразу превращается в Java-record и проходит Bean Validation.
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final ChatClient chatClient;
    private final AiProperties props;
    private final Validator validator;
    private final LlmCallLogRepository llmCalls;

    @Value("classpath:prompts/card_gen.st")
    private Resource cardGenPrompt;
    @Value("classpath:prompts/turn.st")
    private Resource turnPrompt;
    @Value("classpath:prompts/question_grade.st")
    private Resource questionGradePrompt;
    @Value("classpath:prompts/summary.st")
    private Resource summaryPrompt;

    public AiService(ChatClient chatClient, AiProperties props, Validator validator, LlmCallLogRepository llmCalls) {
        this.chatClient = chatClient;
        this.props = props;
        this.validator = validator;
        this.llmCalls = llmCalls;
    }

    public CardDraft generateCard(String language, String userText) {
        return call(Purpose.CARD_GEN, props.strongModel(), 0.4, cardGenPrompt,
                Map.of("language", language), userText, CardDraft.class, null);
    }

    public TurnDecision decideTurn(String language, String allowedTypes, String userText, Long sessionId) {
        return call(Purpose.TURN, props.fastModel(), 0.3, turnPrompt,
                Map.of("language", language, "allowedTypes", allowedTypes), userText, TurnDecision.class, sessionId);
    }

    public QuestionGrade gradeQuestion(String language, String userText, Long sessionId) {
        return call(Purpose.QUESTION_GRADE, props.strongModel(), 0.0, questionGradePrompt,
                Map.of("language", language), userText, QuestionGrade.class, sessionId);
    }

    public SessionSummary summarize(String language, String userText, Long sessionId) {
        return call(Purpose.SUMMARY, props.strongModel(), 0.2, summaryPrompt,
                Map.of("language", language), userText, SessionSummary.class, sessionId);
    }

    /** Один вызов LLM. Ошибка разбора или валидации → один повтор → иначе AiException. */
    private <T> T call(Purpose purpose, String model, double temperature, Resource systemPrompt,
                       Map<String, Object> params, String userText, Class<T> type, Long sessionId) {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            long started = System.currentTimeMillis();
            try {
                ResponseEntity<ChatResponse, T> result = chatClient.prompt()
                        .options(OpenAiChatOptions.builder().model(model).temperature(temperature))
                        .system(s -> s.text(systemPrompt).params(params))
                        .messages(new UserMessage(userText))    // готовая строка, НЕ шаблон
                        .call()
                        .responseEntity(type);

                T entity = result.entity();
                validate(entity);
                record(purpose, model, sessionId, usageOf(result.response()), started, null);
                return entity;
            } catch (RuntimeException e) {
                lastError = e;
                record(purpose, model, sessionId, null, started, e);
                // Текст ошибки может содержать ответ модели, поэтому на уровне WARN — только тип.
                log.warn("LLM call {} failed (attempt {}): {}", purpose, attempt, e.getClass().getSimpleName());
                log.debug("LLM call failure details", e);
            }
        }
        throw new AiException("LLM call failed: " + purpose, lastError);
    }

    private <T> void validate(T entity) {
        if (entity == null) {
            throw new IllegalStateException("empty LLM response");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(entity);
        if (!violations.isEmpty()) {
            throw new IllegalStateException("invalid LLM response: " + violations.stream()
                    .map(v -> v.getPropertyPath().toString()).sorted().collect(Collectors.joining(", ")));
        }
    }

    private static Usage usageOf(ChatResponse response) {
        return response != null && response.getMetadata() != null ? response.getMetadata().getUsage() : null;
    }

    /** Запись в llm_calls: модель, токены, время — без текста запроса и ответа. */
    private void record(Purpose purpose, String model, Long sessionId, Usage usage, long started, RuntimeException error) {
        try {
            LlmCallLog call = new LlmCallLog();
            call.setPurpose(purpose);
            call.setModel(model);
            call.setSessionId(sessionId);
            if (usage != null) {
                call.setPromptTokens(usage.getPromptTokens());
                call.setCompletionTokens(usage.getCompletionTokens());
            }
            call.setLatencyMs((int) (System.currentTimeMillis() - started));
            call.setOk(error == null);
            call.setError(error == null ? null : error.getClass().getSimpleName());
            llmCalls.save(call);
        } catch (RuntimeException e) {
            log.warn("Could not save llm_calls row: {}", e.toString());
        }
    }
}
