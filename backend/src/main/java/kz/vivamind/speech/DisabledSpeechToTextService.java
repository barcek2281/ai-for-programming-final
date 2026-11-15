package kz.vivamind.speech;

import kz.vivamind.common.FeatureDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Заглушка голосового ввода: честно отвечает HTTP 501 «в разработке», ничего не симулирует. */
@Service
public class DisabledSpeechToTextService implements SpeechToTextService {

    /** Флаг vivamind.voice.enabled зарезервирован под фазу 2; рабочей реализации пока нет. */
    private final boolean voiceEnabled;

    public DisabledSpeechToTextService(@Value("${vivamind.voice.enabled}") boolean voiceEnabled) {
        this.voiceEnabled = voiceEnabled;
    }

    @Override
    public String transcribe(byte[] audio, String format, String language) {
        throw new FeatureDisabledException(voiceEnabled
                ? "Голосовые ответы (флаг включён, но реализация появится в фазе 2)"
                : "Голосовые ответы");
    }
}
