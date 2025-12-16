import { CheckCircle2, Target, RotateCcw, Trophy } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { CEFRBadge } from '@/components/CEFRBadge';
import type { LevelProgress } from '@/hooks/useQuestionProgress';
import type { CEFRLevel } from '@/lib/constants';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";

interface QuestionProgressCardProps {
  levelProgress?: LevelProgress;
  selectedLevel: CEFRLevel;
  onResetProgress: () => void;
  isResetting?: boolean;
}

export const QuestionProgressCard = ({
  levelProgress,
  selectedLevel,
  onResetProgress,
  isResetting = false,
}: QuestionProgressCardProps) => {
  if (!levelProgress) {
    return null;
  }

  const { total_questions, answered_questions, remaining_questions, accuracy } = levelProgress;
  const progressPercentage = total_questions > 0 
    ? Math.round((answered_questions / total_questions) * 100) 
    : 0;
  const isCompleted = remaining_questions === 0 && total_questions > 0;

  return (
    <Card className="border-0 shadow-lg bg-gradient-to-br from-card via-card to-muted/20 overflow-hidden">
      <CardContent className="p-3 sm:p-4 space-y-3 sm:space-y-4">
        {/* Header */}
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-1.5 sm:gap-2">
            <CEFRBadge level={selectedLevel} size="sm" />
            <span className="font-semibold text-xs sm:text-sm">Question Progress</span>
          </div>
          {isCompleted && (
            <div className="flex items-center gap-1 text-green-500">
              <Trophy className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
              <span className="text-[10px] sm:text-xs font-medium">Completed!</span>
            </div>
          )}
        </div>

        {/* Progress Bar */}
        <div className="space-y-1.5 sm:space-y-2">
          <div className="flex justify-between text-[10px] sm:text-xs text-muted-foreground">
            <span>{answered_questions} answered</span>
            <span>{remaining_questions} remaining</span>
          </div>
          <Progress value={progressPercentage} className="h-1.5 sm:h-2" />
          <div className="text-center text-[10px] sm:text-xs text-muted-foreground">
            {progressPercentage}% of {total_questions} questions
          </div>
        </div>

        {/* Stats */}
        <div className="grid grid-cols-3 gap-1.5 sm:gap-2 text-center">
          <div className="bg-muted/50 rounded-lg p-1.5 sm:p-2">
            <CheckCircle2 className="w-3.5 h-3.5 sm:w-4 sm:h-4 mx-auto mb-0.5 sm:mb-1 text-green-500" />
            <p className="text-sm sm:text-lg font-bold">{answered_questions}</p>
            <p className="text-[9px] sm:text-[10px] text-muted-foreground leading-tight">Completed</p>
          </div>
          <div className="bg-muted/50 rounded-lg p-1.5 sm:p-2">
            <Target className="w-3.5 h-3.5 sm:w-4 sm:h-4 mx-auto mb-0.5 sm:mb-1 text-blue-500" />
            <p className="text-sm sm:text-lg font-bold">{remaining_questions}</p>
            <p className="text-[9px] sm:text-[10px] text-muted-foreground leading-tight">Remaining</p>
          </div>
          <div className="bg-muted/50 rounded-lg p-1.5 sm:p-2">
            <Trophy className="w-3.5 h-3.5 sm:w-4 sm:h-4 mx-auto mb-0.5 sm:mb-1 text-yellow-500" />
            <p className="text-sm sm:text-lg font-bold">{accuracy}%</p>
            <p className="text-[9px] sm:text-[10px] text-muted-foreground leading-tight">Accuracy</p>
          </div>
        </div>

        {/* Completed Message */}
        {isCompleted && (
          <div className="bg-gradient-to-r from-green-500/10 to-emerald-500/10 border border-green-500/20 rounded-lg p-2 sm:p-3 text-center">
            <p className="text-xs sm:text-sm font-medium text-green-600 dark:text-green-400">
              🎉 You've answered all {total_questions} questions!
            </p>
            <p className="text-[10px] sm:text-xs text-muted-foreground mt-0.5 sm:mt-1">
              Final accuracy: {accuracy}%
            </p>
          </div>
        )}

        {/* Reset Button */}
        {answered_questions > 0 && (
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button 
                variant="outline" 
                size="sm" 
                className="w-full text-[10px] sm:text-xs min-h-[40px] sm:min-h-[36px]"
                disabled={isResetting}
              >
                <RotateCcw className="w-3 h-3 mr-1" />
                {isResetting ? 'Resetting...' : 'Reset Progress'}
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent className="max-w-[90vw] sm:max-w-lg mx-auto">
              <AlertDialogHeader>
                <AlertDialogTitle className="text-base sm:text-lg">Reset {selectedLevel} Progress?</AlertDialogTitle>
                <AlertDialogDescription className="text-xs sm:text-sm">
                  This will delete all your attempt history for {selectedLevel} level ({answered_questions} questions).
                  You'll be able to practice all questions again from the beginning.
                  This action cannot be undone.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter className="flex-col sm:flex-row gap-2 sm:gap-0">
                <AlertDialogCancel className="w-full sm:w-auto">Cancel</AlertDialogCancel>
                <AlertDialogAction onClick={onResetProgress} className="w-full sm:w-auto">
                  Reset Progress
                </AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        )}
      </CardContent>
    </Card>
  );
};
