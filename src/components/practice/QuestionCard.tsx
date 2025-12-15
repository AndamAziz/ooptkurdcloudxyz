import { CheckCircle2, XCircle } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import type { Question, AnswerResult } from '@/hooks/useQuestions';

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
  const isFeedback = selectedAnswer !== null && answerResult !== null;

  const options = [
    { key: 'A', value: question.options_a },
    { key: 'B', value: question.options_b },
    { key: 'C', value: question.options_c },
    { key: 'D', value: question.options_d },
  ].filter(o => o.value);

  return (
    <Card className="animate-fade-in">
      <CardContent className="p-8">
        <p className="text-lg leading-relaxed mb-8">{question.prompt}</p>

        <div className="space-y-3">
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
                  "w-full p-4 rounded-lg border-2 text-left transition-all",
                  "flex items-center gap-4",
                  !isFeedback && "hover:border-primary hover:bg-primary/5",
                  showCorrect && "border-success bg-success/10",
                  showWrong && "border-destructive bg-destructive/10",
                  !showCorrect && !showWrong && isSelected && "border-primary bg-primary/10"
                )}
              >
                <span className={cn(
                  "w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold",
                  showCorrect ? "bg-success text-success-foreground" : 
                  showWrong ? "bg-destructive text-destructive-foreground" :
                  "bg-muted"
                )}>
                  {option.key}
                </span>
                <span className="flex-1">{option.value}</span>
                {showCorrect && <CheckCircle2 className="w-5 h-5 text-success" />}
                {showWrong && <XCircle className="w-5 h-5 text-destructive" />}
              </button>
            );
          })}
        </div>

        {/* Feedback */}
        {isFeedback && (
          <div className="mt-6 animate-slide-up">
            <div className={cn(
              "p-4 rounded-lg mb-4",
              isCorrect ? "bg-success/10" : "bg-destructive/10"
            )}>
              <p className={cn(
                "font-semibold mb-1",
                isCorrect ? "text-success" : "text-destructive"
              )}>
                {isCorrect ? "Correct! +10 XP" : "Incorrect +2 XP"}
              </p>
              {answerResult?.explanation && (
                <p className="text-sm text-muted-foreground">
                  {answerResult.explanation}
                </p>
              )}
            </div>
            
            <Button onClick={onNext} className="w-full">
              {isLastQuestion ? 'See Results' : 'Next Question'}
            </Button>
          </div>
        )}
      </CardContent>
    </Card>
  );
};
