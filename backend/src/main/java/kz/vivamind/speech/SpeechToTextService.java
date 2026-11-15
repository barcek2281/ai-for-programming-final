package kz.vivamind.speech;

/**
 * Речь в текст — ФАЗА 2. В MVP есть только этот интерфейс и заглушка DisabledSpeechToTextService.
 * План: реализация на OpenAiAudioTranscriptionModel из Spring AI (модель STT_MODEL); транскрипт пойдёт
 * в тот же InterviewService.handleAnswer(), логика интервью не меняется.
 */
public interface SpeechToTextService {

    String transcribe(byte[] audio, String format, String language);
}
