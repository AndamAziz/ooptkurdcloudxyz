import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Clock } from 'lucide-react';
import type { PlacementQuestion as PlacementQuestionType } from '@/hooks/usePlacementTest';

interface PlacementQuestionProps {
  question: PlacementQuestionType;
  currentIndex: number;
  totalQuestions: number;
  selectedAnswer: string | null;
  onSelectAnswer: (answer: string) => void;
  onSubmit: () => void;
  isSubmitting?: boolean;
}

export const PlacementQuestion = ({
  question,
  currentIndex,
  totalQuestions,
  selectedAnswer,
  onSelectAnswer,
  onSubmit,
  isSubmitting,
}: PlacementQuestionProps) => {
  const progress = ((currentIndex) / totalQuestions) * 100;
  const options = [
    { key: 'A', value: question.options_a },
    { key: 'B', value: question.options_b },
    { key: 'C', value: question.options_c },
    { key: 'D', value: question.options_d },
  ].filter(opt => opt.value);

  // Clean prompt by removing version identifiers
  const cleanPrompt = question.prompt?.replace(/\s*\(v\d+\)\s*$/, '') || '';

  return (
    <div className="min-h-screen bg-background p-4 flex flex-col">
      <div className="max-w-2xl mx-auto w-full flex-1 flex flex-col">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center justify-between mb-2">
            <h2 className="text-lg font-semibold">Placement Test</h2>
            <span className="text-sm text-muted-foreground">
              Question {currentIndex + 1}/{totalQuestions}
            </span>
          </div>
          <Progress value={progress} className="h-2" />
          <p className="text-xs text-muted-foreground mt-2 flex items-center gap-1">
            <Clock className="w-3 h-3" />
            No time limit - take your time
          </p>
        </div>

        {/* Question Card */}
        <Card className="flex-1 flex flex-col">
          <CardHeader>
            <CardTitle className="text-lg font-medium leading-relaxed">
              {cleanPrompt}
            </CardTitle>
          </CardHeader>
          <CardContent className="flex-1 flex flex-col">
            <div className="space-y-3 flex-1">
              {options.map((option) => (
                <Button
                  key={option.key}
                  variant={selectedAnswer === option.key ? 'default' : 'outline'}
                  className="w-full justify-start text-left h-auto py-4 px-4"
                  onClick={() => onSelectAnswer(option.key)}
                  disabled={isSubmitting}
                >
                  <span className="font-semibold mr-3 text-muted-foreground">
                    {option.key})
                  </span>
                  <span className="flex-1">{option.value}</span>
                </Button>
              ))}
            </div>

            <Button
              onClick={onSubmit}
              disabled={!selectedAnswer || isSubmitting}
              className="w-full mt-6"
              size="lg"
            >
              {isSubmitting ? 'Checking...' : currentIndex === totalQuestions - 1 ? 'Finish Test' : 'Next Question'}
            </Button>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
