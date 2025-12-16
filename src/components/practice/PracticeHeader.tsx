import { Clock, GraduationCap } from 'lucide-react';
import { Progress } from '@/components/ui/progress';
import { CEFRBadge } from '@/components/CEFRBadge';
import { cn } from '@/lib/utils';
import type { CEFRLevel } from '@/lib/constants';

interface PracticeHeaderProps {
  level: CEFRLevel;
  currentIndex: number;
  totalQuestions: number;
  progress: number;
  showTimer?: boolean;
  timeLeft?: number;
  isPlacementMode?: boolean;
  currentQuestionLevel?: CEFRLevel;
}

const formatTime = (seconds: number) => {
  const mins = Math.floor(seconds / 60);
  const secs = seconds % 60;
  return `${mins}:${secs.toString().padStart(2, '0')}`;
};

export const PracticeHeader = ({
  level,
  currentIndex,
  totalQuestions,
  progress,
  showTimer = false,
  timeLeft = 0,
  isPlacementMode = false,
  currentQuestionLevel,
}: PracticeHeaderProps) => {
  return (
    <div className="mb-4 md:mb-6 space-y-2 md:space-y-3">
      <div className="flex items-center justify-between gap-2">
        <div className="flex items-center gap-2 md:gap-3">
          {isPlacementMode ? (
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-lg bg-primary/20 flex items-center justify-center">
                <GraduationCap className="w-4 h-4 text-primary" />
              </div>
              <span className="text-xs md:text-sm font-medium text-primary">Placement Test</span>
              {currentQuestionLevel && (
                <CEFRBadge level={currentQuestionLevel} size="sm" />
              )}
            </div>
          ) : (
            <CEFRBadge level={level} />
          )}
          <span className="text-xs md:text-sm text-muted-foreground">
            Q {currentIndex + 1}/{totalQuestions}
          </span>
        </div>
        {showTimer && (
          <div className={cn(
            "flex items-center gap-1.5 md:gap-2 px-2 md:px-3 py-1 rounded-full text-sm",
            timeLeft < 60 ? "bg-destructive/10 text-destructive" : "bg-muted"
          )}>
            <Clock className="w-3.5 h-3.5 md:w-4 md:h-4" />
            <span className="font-mono font-medium text-xs md:text-sm">{formatTime(timeLeft)}</span>
          </div>
        )}
      </div>
      <Progress value={progress} className="h-1.5 md:h-2" />
    </div>
  );
};
