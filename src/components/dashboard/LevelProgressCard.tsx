import { TrendingUp, CheckCircle, Lock } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { Progress } from '@/components/ui/progress';
import type { LevelStats } from '@/hooks/useProgress';
import { cn } from '@/lib/utils';

interface LevelProgressCardProps {
  levelStats: LevelStats[];
  compact?: boolean;
}

export const LevelProgressCard = ({ levelStats, compact = false }: LevelProgressCardProps) => {
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

  if (compact) {
    return (
      <div className="grid grid-cols-6 gap-1.5">
        {levelStats.map((level, index) => {
          const unlocked = isLevelUnlocked(index);
          const hasProgress = level.attempts > 0;
          
          return (
            <div 
              key={level.level}
              className={cn(
                "relative flex flex-col items-center p-2 rounded-lg border transition-all",
                !unlocked && "opacity-50 bg-muted/50",
                unlocked && hasProgress && "bg-gradient-to-b from-primary/10 to-transparent border-primary/30",
                unlocked && !hasProgress && "border-dashed border-muted-foreground/30"
              )}
            >
              {!unlocked && (
                <Lock className="w-3 h-3 absolute top-1 right-1 text-muted-foreground" />
              )}
              <span className="text-xs font-bold">{level.level}</span>
              {hasProgress ? (
                <>
                  <div className={cn(
                    "w-6 h-1 rounded-full mt-1",
                    getProgressColor(level.accuracy)
                  )} />
                  <span className="text-[10px] text-muted-foreground mt-0.5">
                    {level.accuracy}%
                  </span>
                </>
              ) : (
                <span className="text-[10px] text-muted-foreground mt-1">-</span>
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
          Your Stats
        </CardTitle>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-0 space-y-4">
        <p className="text-sm text-muted-foreground">
          Progress through each CEFR level by practicing and achieving 60%+ accuracy.
        </p>
        
        <div className="space-y-3">
          {levelStats.map((level, index) => {
            const unlocked = isLevelUnlocked(index);
            const hasProgress = level.attempts > 0;
            
            return (
              <div 
                key={level.level}
                className={cn(
                  "p-3 rounded-lg border transition-all",
                  !unlocked && "opacity-60 bg-muted/30",
                  unlocked && hasProgress && "bg-card",
                  unlocked && !hasProgress && "border-dashed"
                )}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={level.level} size="sm" />
                    {!unlocked && <Lock className="w-3.5 h-3.5 text-muted-foreground" />}
                    {hasProgress && level.accuracy >= 60 && (
                      <CheckCircle className="w-4 h-4 text-green-500" />
                    )}
                  </div>
                  <div className="text-right text-sm">
                    {hasProgress ? (
                      <span className="font-medium">
                        {level.correct}/{level.attempts} ({level.accuracy}%)
                      </span>
                    ) : (
                      <span className="text-muted-foreground">Not started</span>
                    )}
                  </div>
                </div>
                
                {unlocked && (
                  <Progress 
                    value={level.accuracy} 
                    className="h-2"
                  />
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
};
