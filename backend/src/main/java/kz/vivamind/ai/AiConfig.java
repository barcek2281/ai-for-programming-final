package kz.vivamind.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** Один ChatClient на всё приложение. Модель и температура задаются на каждый вызов в AiService. */
    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    /** Приложение стартует и без ключа, но сразу предупреждает, что функции ИИ работать не будут. */
    @Bean
    ApplicationRunner aiSettingsCheck(AiProperties props, @Value("${spring.ai.openai.api-key:}") String apiKey) {
        return args -> {
            if (apiKey.isBlank()) {
                log.warn("OPENROUTER_API_KEY is empty: PDF processing, card generation and interviews will fail "
                        + "until it is set in .env");
            }
            if (isBlank(props.fastModel()) || isBlank(props.strongModel())) {
                log.warn("LLM_FAST_MODEL / LLM_STRONG_MODEL are not set in .env: LLM calls will fail");
            }
        };
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
