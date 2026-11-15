package kz.vivamind.ai;

/** LLM не ответила или дважды вернула ответ, который не прошёл разбор/валидацию. */
public class AiException extends RuntimeException {

    public AiException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Понятное объяснение типовых ошибок OpenRouter — по имени исключения SDK, без текста ответа модели.
     * Возвращает null, если причина нетиповая.
     */
    public static String explain(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            String name = t.getClass().getSimpleName();
            if (name.contains("Unauthorized") || name.contains("PermissionDenied")) {
                return "OpenRouter отклонил ключ API. Проверьте OPENROUTER_API_KEY в файле .env и перезапустите backend.";
            }
            if (name.contains("RateLimit")) {
                return "OpenRouter: превышен лимит запросов или закончился баланс. Попробуйте позже.";
            }
            if (name.contains("NotFound")) {
                return "OpenRouter не нашёл модель. Проверьте слаги LLM_FAST_MODEL, LLM_STRONG_MODEL и EMBEDDING_MODEL в .env.";
            }
        }
        return null;
    }
}
