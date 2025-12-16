import { Trophy, Check, X, ChevronRight, TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { CEFRBadge } from '@/components/CEFRBadge';
import type { PlacementResult } from '@/hooks/usePlacementTest';
import type { CEFRLevel } from '@/lib/constants';
import { LEVEL_DESCRIPTIONS } from '@/lib/constants';

interface PlacementResultsProps {
  result: PlacementResult;
  onContinue: () => void;
  onChooseHigherLevel?: () => void;
  isLoading?: boolean;
}

const LEVEL_ORDER: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];

export const PlacementResults = ({ 
  result, 
  onContinue, 
  onChooseHigherLevel,
  isLoading 
}: PlacementResultsProps) => {
  const levelDescription = LEVEL_DESCRIPTIONS[result.recommendedLevel];
  const recommendedIndex = LEVEL_ORDER.indexOf(result.recommendedLevel);
  const hasHigherLevel = recommendedIndex < LEVEL_ORDER.length - 1;

  return (
    <div className="min-h-screen bg-background p-4 flex items-center justify-center">
      <Card className="w-full max-w-lg">
        <CardHeader className="text-center space-y-4">
          <div className="mx-auto w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center">
            <Trophy className="w-8 h-8 text-primary" />
          </div>
          <CardTitle className="text-2xl">Test Complete!</CardTitle>
          <div className="space-y-2">
            <p className="text-muted-foreground">Your English Level is:</p>
            <div className="flex items-center justify-center gap-3">
              <CEFRBadge level={result.recommendedLevel} size="lg" />
            </div>
            <p className="text-sm text-muted-foreground">
              {levelDescription}
            </p>
          </div>
        </CardHeader>

        <CardContent className="space-y-6">
          {/* Level breakdown */}
          <div className="bg-muted/50 rounded-lg p-4 space-y-3">
            <h3 className="font-semibold text-sm">Your Performance by Level:</h3>
            <div className="space-y-2">
              {LEVEL_ORDER.map((level) => {
                const score = result.levelScores[level];
                const passed = score.percentage >= 70;
                return (
                  <div key={level} className="flex items-center gap-3">
                    <span className="w-8 text-sm font-medium">{level}</span>
                    <div className="flex-1">
                      <Progress 
                        value={score.percentage} 
                        className="h-2"
                      />
                    </div>
                    <span className="text-xs text-muted-foreground w-20 text-right">
                      {score.percentage}% ({score.correct}/{score.total})
                    </span>
                    {passed ? (
                      <Check className="w-4 h-4 text-green-500" />
                    ) : (
                      <X className="w-4 h-4 text-red-500" />
                    )}
                  </div>
                );
              })}
            </div>
            <div className="pt-2 border-t">
              <div className="flex items-center justify-between text-sm">
                <span className="font-medium">Overall:</span>
                <span className="text-muted-foreground">
                  {result.totalCorrect}/{result.totalQuestions} ({result.overallPercentage}%)
                </span>
              </div>
            </div>
          </div>

          {/* Action buttons */}
          <div className="space-y-3">
            <Button 
              onClick={onContinue} 
              className="w-full" 
              size="lg"
              disabled={isLoading}
            >
              {isLoading ? 'Saving...' : 'Start Learning'}
              <ChevronRight className="w-4 h-4 ml-2" />
            </Button>

            {hasHigherLevel && onChooseHigherLevel && (
              <Button 
                variant="ghost" 
                onClick={onChooseHigherLevel}
                className="w-full text-muted-foreground"
                disabled={isLoading}
              >
                <TrendingUp className="w-4 h-4 mr-2" />
                Want to challenge yourself? Start from a higher level
              </Button>
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
