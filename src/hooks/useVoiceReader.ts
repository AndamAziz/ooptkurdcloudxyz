import { useState, useEffect, useCallback } from 'react';

const VOICE_MUTED_KEY = 'voice_reader_muted';

export const useVoiceReader = () => {
  const [isMuted, setIsMuted] = useState(() => {
    const stored = localStorage.getItem(VOICE_MUTED_KEY);
    return stored === 'true';
  });
  const [isSpeaking, setIsSpeaking] = useState(false);

  useEffect(() => {
    localStorage.setItem(VOICE_MUTED_KEY, String(isMuted));
  }, [isMuted]);

  const stop = useCallback(() => {
    window.speechSynthesis.cancel();
    setIsSpeaking(false);
  }, []);

  const speak = useCallback((text: string, onEnd?: () => void) => {
    if (isMuted || !text) return;

    stop();

    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = 'en-US';
    utterance.rate = 0.9;
    utterance.pitch = 1;

    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => {
      setIsSpeaking(false);
      onEnd?.();
    };
    utterance.onerror = () => setIsSpeaking(false);

    window.speechSynthesis.speak(utterance);
  }, [isMuted, stop]);

  const readQuestion = useCallback((prompt: string, options: { key: string; value: string }[]) => {
    if (isMuted) return;

    const optionsText = options
      .map(o => `Option ${o.key}: ${o.value}`)
      .join('. ');

    const fullText = `${prompt}. ${optionsText}`;
    speak(fullText);
  }, [isMuted, speak]);

  const speakFeedback = useCallback((isCorrect: boolean) => {
    speak(isCorrect ? 'Correct!' : 'Incorrect.');
  }, [speak]);

  const toggleMute = useCallback(() => {
    stop();
    setIsMuted(prev => !prev);
  }, [stop]);

  return {
    isMuted,
    isSpeaking,
    speak,
    stop,
    readQuestion,
    speakFeedback,
    toggleMute,
  };
};
