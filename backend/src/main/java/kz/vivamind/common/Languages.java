package kz.vivamind.common;

/** Тексты, которые зависят от языка курса (kk | ru | en). Сам интерфейс — на русском. */
public final class Languages {

    private Languages() {
    }

    public static boolean isSupported(String code) {
        return "kk".equals(code) || "ru".equals(code) || "en".equals(code);
    }

    /** Название языка для плейсхолдера {language} в системных промптах. */
    public static String promptName(String code) {
        return switch (code) {
            case "kk" -> "Kazakh";
            case "en" -> "English";
            default -> "Russian";
        };
    }

    /** Запасной углубляющий вопрос типа WHY, если LLM не предложила свой. */
    public static String whyTemplate(String code) {
        return switch (code) {
            case "kk" -> "Неліктен солай? Өз сөзіңізбен түсіндіріп беріңіз.";
            case "en" -> "Why is that? Please explain in your own words.";
            default -> "Почему это так? Объясните своими словами.";
        };
    }

    public static String dontKnow(String code) {
        return switch (code) {
            case "kk" -> "Білмеймін";
            case "en" -> "I don't know";
            default -> "Не знаю";
        };
    }

    public static String thanks(String code) {
        return switch (code) {
            case "kk" -> "Рақмет! Сұхбат аяқталды. Жауаптарыңыз бағалауға жіберілді.";
            case "en" -> "Thank you! The interview is over. Your answers have been sent for grading.";
            default -> "Спасибо! Интервью завершено. Ваши ответы отправлены на оценку.";
        };
    }
}
