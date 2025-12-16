import { CheckCircle2, XCircle, ArrowRight, Volume2, VolumeX, Loader2 } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { Question, AnswerResult } from '@/hooks/useQuestions';
import { useVoiceReader } from '@/hooks/useVoiceReader';
import { useEffect } from 'react';

interface QuestionCardProps {
  question: Question;
  selectedAnswer: string | null;
  answerResult: AnswerResult | null;
  isCorrect: boolean | null;
  onAnswer: (answer: string) => void;
  onNext: () => void;
  isLastQuestion: boolean;
}

export const QuestionCard = ({
  question,
  selectedAnswer,
  answerResult,
  isCorrect,
  onAnswer,
  onNext,
  isLastQuestion,
}: QuestionCardProps) => {
  const { isMuted, isSpeaking, readQuestion, speakFeedback, toggleMute, stop } = useVoiceReader();
  const isFeedback = selectedAnswer !== null && answerResult !== null;

  const options = [
    { key: 'A', value: question.options_a },
    { key: 'B', value: question.options_b },
    { key: 'C', value: question.options_c },
    { key: 'D', value: question.options_d },
  ].filter(o => o.value) as { key: string; value: string }[];

  // Speak feedback when answer is submitted
  useEffect(() => {
    if (isFeedback && isCorrect !== null) {
      speakFeedback(isCorrect);
    }
  }, [isFeedback, isCorrect, speakFeedback]);

  // Stop speaking when moving to next question
  useEffect(() => {
    return () => stop();
  }, [question.id, stop]);

  const handleReadQuestion = () => {
    if (isMuted) {
      toggleMute();
      return;
    }
    // Clean prompt: remove identifiers like (v443), (q123), etc.
    const cleanPrompt = question.prompt.replace(/\s*\([a-zA-Z]*\d+\)\s*$/, '').trim();
    readQuestion(cleanPrompt, options);
  };

  return (
    <Card className="animate-fade-in w-full max-w-full">
      <CardContent className="p-4 md:p-6 lg:p-8">
        <div className="flex items-start justify-between gap-2 mb-6 md:mb-8">
          <p className="text-base md:text-lg leading-relaxed flex-1">{question.prompt}</p>
          <Button
            variant="ghost"
            size="icon"
            onClick={handleReadQuestion}
            className="h-8 w-8 flex-shrink-0"
            title={isMuted ? "Unmute and read question" : "Read question aloud"}
          >
            {isSpeaking ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : isMuted ? (
              <VolumeX className="h-4 w-4 text-muted-foreground" />
            ) : (
              <Volume2 className="h-4 w-4" />
            )}
          </Button>
        </div>

        <div className="space-y-2 md:space-y-3">
          {options.map((option) => {
            const isSelected = selectedAnswer === option.key;
            const isCorrectAnswer = isFeedback && option.key.toUpperCase() === answerResult?.correct_answer?.toUpperCase();
            const showCorrect = isFeedback && isCorrectAnswer;
            const showWrong = isFeedback && isSelected && !isCorrectAnswer;

            return (
              <button
                key={option.key}
                onClick={() => onAnswer(option.key)}
                disabled={isFeedback}
                className={cn(
                  "w-full p-3 md:p-4 rounded-lg border-2 text-left transition-all min-h-[52px]",
                  "flex items-center gap-3 md:gap-4",
                  !isFeedback && "hover:border-primary hover:bg-primary/5 active:bg-primary/10",
                  showCorrect && "border-success bg-success/10",
                  showWrong && "border-destructive bg-destructive/10",
                  !showCorrect && !showWrong && isSelected && "border-primary bg-primary/10"
                )}
              >
                <span className={cn(
                  "w-7 h-7 md:w-8 md:h-8 rounded-full flex items-center justify-center text-xs md:text-sm font-semibold flex-shrink-0",
                  showCorrect ? "bg-success text-success-foreground" : 
                  showWrong ? "bg-destructive text-destructive-foreground" :
                  "bg-muted"
                )}>
                  {option.key}
                </span>
                <span className="flex-1 text-sm md:text-base">{option.value}</span>
                {showCorrect && <CheckCircle2 className="w-5 h-5 text-success flex-shrink-0" />}
                {showWrong && <XCircle className="w-5 h-5 text-destructive flex-shrink-0" />}
              </button>
            );
          })}
        </div>

        {/* Feedback */}
        {isFeedback && (
          <div className="mt-4 md:mt-6 animate-slide-up">
            <div className={cn(
              "p-3 md:p-4 rounded-lg mb-3 md:mb-4",
              isCorrect ? "bg-success/10" : "bg-destructive/10"
            )}>
              <p className={cn(
                "font-semibold mb-1 text-sm md:text-base",
                isCorrect ? "text-success" : "text-destructive"
              )}>
                {isCorrect ? "Correct! +10 XP" : "Incorrect +2 XP"}
              </p>
              {answerResult?.explanation && (
                <p className="text-xs md:text-sm text-muted-foreground">
                  {answerResult.explanation}
                </p>
              )}
            </div>
            
            <Button onClick={onNext} className="w-full min-h-[48px]">
              {isLastQuestion ? 'See Results' : 'Next Question'}
              <ArrowRight className="w-4 h-4 ml-2" />
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
