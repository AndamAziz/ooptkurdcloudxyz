import { Clock } from 'lucide-react';
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
}: PracticeHeaderProps) => {
  return (
    <div className="mb-6 space-y-3">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <CEFRBadge level={level} />
          <span className="text-sm text-muted-foreground">
            Question {currentIndex + 1} of {totalQuestions}
          </span>
        </div>
        {showTimer && (
          <div className={cn(
            "flex items-center gap-2 px-3 py-1 rounded-full",
            timeLeft < 60 ? "bg-destructive/10 text-destructive" : "bg-muted"
          )}>
            <Clock className="w-4 h-4" />
            <span className="font-mono font-medium">{formatTime(timeLeft)}</span>
          </div>
        )}
      </div>
      <Progress value={progress} className="h-2" />
    </div>
  );
};
