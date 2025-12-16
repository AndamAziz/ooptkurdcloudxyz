import { TrendingUp, CheckCircle, Lock } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { Progress } from '@/components/ui/progress';
import type { LevelStats } from '@/hooks/useProgress';
import type { LevelProgress } from '@/hooks/useQuestionProgress';
import { cn } from '@/lib/utils';

interface LevelProgressCardProps {
  levelStats: LevelStats[];
  questionProgress?: LevelProgress[];
  compact?: boolean;
  currentLevel?: string;
}

export const LevelProgressCard = ({ levelStats, questionProgress, compact = false, currentLevel }: LevelProgressCardProps) => {
  const getProgressColor = (accuracy: number) => {
    if (accuracy >= 80) return 'bg-green-500';
    if (accuracy >= 60) return 'bg-emerald-400';
    if (accuracy >= 40) return 'bg-yellow-500';
    if (accuracy >= 20) return 'bg-orange-500';
    return 'bg-red-500';
  };

  const isLevelUnlocked = (index: number) => {
    if (index === 0) return true;
    const prevLevel = levelStats[index - 1];
    return prevLevel.attempts >= 10 && prevLevel.accuracy >= 60;
  };

  // Find current level index (first unlocked level without 60%+ accuracy)
  const getCurrentLevelIndex = () => {
    for (let i = 0; i < levelStats.length; i++) {
      if (!isLevelUnlocked(i)) return i - 1;
      if (levelStats[i].accuracy < 60 || levelStats[i].attempts < 10) return i;
    }
    return levelStats.length - 1;
  };

  const currentLevelIndex = getCurrentLevelIndex();

  // Helper to get question progress for a level
  const getQuestionProgressForLevel = (level: string) => {
    return questionProgress?.find(p => p.cefr === level);
  };

  if (compact) {
    return (
      <div className="grid grid-cols-6 gap-1 sm:gap-1.5">
        {levelStats.map((level, index) => {
          const unlocked = isLevelUnlocked(index);
          const qProgress = getQuestionProgressForLevel(level.level);
          const hasProgress = qProgress ? qProgress.answered_questions > 0 : level.attempts > 0;
          const accuracy = qProgress?.accuracy ?? level.accuracy;
          const isCurrentLevel = index === currentLevelIndex;
          const isCompleted = unlocked && hasProgress && accuracy >= 60 && (qProgress ? qProgress.answered_questions >= 10 : level.attempts >= 10);
          
          return (
            <div 
              key={level.level}
              className={cn(
                "relative flex flex-col items-center p-1.5 sm:p-2 rounded-lg border transition-all min-w-[48px] sm:min-w-[60px] min-h-[58px] sm:min-h-[70px]",
                // Locked state
                !unlocked && "opacity-50 bg-muted/50 border-muted",
                // Current level - glowing blue border
                isCurrentLevel && unlocked && "ring-2 ring-primary ring-offset-1 ring-offset-background bg-gradient-to-b from-primary/15 to-primary/5 border-primary/40 shadow-lg shadow-primary/20",
                // Completed level
                isCompleted && !isCurrentLevel && "bg-gradient-to-b from-green-500/10 to-transparent border-green-500/30",
                // Unlocked but not started
                unlocked && !hasProgress && !isCurrentLevel && "border-dashed border-muted-foreground/30 hover:border-primary/50"
              )}
            >
              {/* Lock icon for locked levels */}
              {!unlocked && (
                <Lock className="w-3 h-3 sm:w-3.5 sm:h-3.5 absolute top-0.5 right-0.5 text-muted-foreground" />
              )}
              
              {/* Checkmark for completed levels */}
              {isCompleted && (
                <CheckCircle className="w-3 h-3 sm:w-3.5 sm:h-3.5 absolute top-0.5 right-0.5 text-green-500" />
              )}
              
              {/* Level name */}
              <span className={cn(
                "text-xs sm:text-sm font-bold",
                isCurrentLevel && "text-primary",
                isCompleted && "text-green-600 dark:text-green-400"
              )}>
                {level.level}
              </span>
              
              {/* Progress indicator */}
              {hasProgress && unlocked ? (
                <>
                  {/* Mini progress bar */}
                  <div className="w-full h-1 sm:h-1.5 rounded-full bg-muted mt-1 overflow-hidden">
                    <div 
                      className={cn("h-full rounded-full transition-all", getProgressColor(accuracy))}
                      style={{ width: `${Math.min(accuracy, 100)}%` }}
                    />
                  </div>
                  <span className={cn(
                    "text-[10px] sm:text-xs mt-0.5 font-medium",
                    accuracy >= 60 ? "text-green-600 dark:text-green-400" : "text-muted-foreground"
                  )}>
                    {accuracy}%
                  </span>
                  {qProgress && (
                    <span className="text-[8px] text-muted-foreground">
                      {qProgress.answered_questions}/{qProgress.total_questions}
                    </span>
                  )}
                </>
              ) : !unlocked ? (
                <div className="flex flex-col items-center mt-1">
                  <Lock className="w-3.5 h-3.5 sm:w-4 sm:h-4 text-muted-foreground/60" />
                  <span className="text-[9px] sm:text-[10px] text-muted-foreground mt-0.5">Locked</span>
                </div>
              ) : (
                <span className="text-[10px] sm:text-xs text-muted-foreground mt-1">-</span>
              )}
            </div>
          );
        })}
      </div>
    );
  }

  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-lg md:text-xl">
          <TrendingUp className="w-5 h-5" />
          Level Progress
        </CardTitle>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-0 space-y-4">
        <p className="text-sm text-muted-foreground">
          Answer all questions in each level with 90%+ accuracy to earn a certificate.
        </p>
        
        <div className="space-y-3">
          {levelStats.map((level, index) => {
            const unlocked = isLevelUnlocked(index);
            const qProgress = getQuestionProgressForLevel(level.level);
            const hasProgress = qProgress ? qProgress.answered_questions > 0 : level.attempts > 0;
            const accuracy = qProgress?.accuracy ?? level.accuracy;
            const answered = qProgress?.answered_questions ?? level.attempts;
            const total = qProgress?.total_questions ?? 0;
            const correct = qProgress?.correct_count ?? level.correct;
            const remaining = qProgress?.remaining_questions ?? 0;
            const isCurrentLevelItem = index === currentLevelIndex;
            const isCompleted = unlocked && hasProgress && accuracy >= 60 && answered >= 10;
            
            return (
              <div 
                key={level.level}
                className={cn(
                  "p-3 rounded-lg border transition-all",
                  !unlocked && "opacity-60 bg-muted/30",
                  isCurrentLevelItem && unlocked && "ring-2 ring-primary border-primary/40 bg-primary/5",
                  isCompleted && !isCurrentLevelItem && "border-green-500/30 bg-green-500/5",
                  unlocked && !hasProgress && !isCurrentLevelItem && "border-dashed"
                )}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={level.level} size="sm" />
                    {!unlocked && <Lock className="w-3.5 h-3.5 text-muted-foreground" />}
                    {isCompleted && (
                      <CheckCircle className="w-4 h-4 text-green-500" />
                    )}
                    {isCurrentLevelItem && unlocked && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-primary/20 text-primary font-medium">
                        Current
                      </span>
                    )}
                  </div>
                  <div className="text-right text-sm">
                    {hasProgress ? (
                      <div className="flex flex-col items-end">
                        <span className={cn(
                          "font-bold",
                          accuracy >= 90 ? "text-green-500" : accuracy >= 70 ? "text-yellow-500" : "text-orange-500"
                        )}>
                          {accuracy}% accuracy
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {answered}/{total} answered • {remaining} left
                        </span>
                      </div>
                    ) : (
                      <span className="text-muted-foreground">Not started</span>
                    )}
                  </div>
                </div>
                
                {unlocked && (
                  <div className="space-y-1">
                    <Progress 
                      value={total > 0 ? (answered / total) * 100 : 0} 
                      className="h-2"
                    />
                    <p className="text-xs text-muted-foreground text-right">
                      {correct} correct of {answered} answered
                    </p>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
};
