import { Flame, Target, CheckCircle, Zap } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';
import { LevelProgressCard } from './LevelProgressCard';
import { isToday, isYesterday, parseISO } from 'date-fns';
import { cn } from '@/lib/utils';
import type { LevelStats } from '@/hooks/useProgress';

interface MobileStatsHeaderProps {
  streak: number;
  lastPracticeDate: string | null;
  totalXP: number;
  totalAttempts: number;
  totalCorrect: number;
  accuracy: number;
  levelStats: LevelStats[];
}

export const MobileStatsHeader = ({
  streak,
  lastPracticeDate,
  totalXP,
  totalAttempts,
  totalCorrect,
  accuracy,
  levelStats,
}: MobileStatsHeaderProps) => {
  const getStreakStatus = () => {
    if (!lastPracticeDate) return 'Start your streak today!';
    const date = parseISO(lastPracticeDate);
    if (isToday(date)) return 'Keep it going!';
    if (isYesterday(date)) return 'Practice today to keep streak!';
    return 'Start a new streak today!';
  };

  const isStreakActive = lastPracticeDate && (isToday(parseISO(lastPracticeDate)) || isYesterday(parseISO(lastPracticeDate)));

  return (
    <Card className="overflow-hidden border-0 shadow-xl bg-gradient-to-br from-card via-card to-muted/20">
      <CardContent className="p-0">
        {/* Combined Stats Banner */}
        <div className="bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-4 relative overflow-hidden">
          {/* Background decoration */}
          <div className="absolute inset-0 opacity-30">
            <div className="absolute top-0 right-0 w-40 h-40 bg-orange-500/20 rounded-full -translate-y-1/2 translate-x-1/2 blur-2xl" />
            <div className="absolute bottom-0 left-0 w-32 h-32 bg-green-500/20 rounded-full translate-y-1/2 -translate-x-1/2 blur-2xl" />
          </div>
          
          <div className="relative flex items-center justify-between gap-4">
            {/* Streak Section */}
            <div className="flex items-center gap-3 flex-1">
              <div className={cn(
                "p-2.5 rounded-xl bg-gradient-to-br from-orange-500 to-rose-600 shadow-lg shadow-orange-500/30",
                isStreakActive && "animate-pulse"
              )}>
                <Flame className="w-7 h-7 text-white drop-shadow-lg" />
              </div>
              <div>
                <p className="text-3xl font-bold text-white drop-shadow-md">{streak}</p>
                <p className="text-xs text-white/70 font-medium">Day Streak</p>
                <p className="text-[10px] text-white/50 mt-0.5">{getStreakStatus()}</p>
              </div>
            </div>
            
            {/* Divider */}
            <div className="w-px h-16 bg-white/10" />
            
            {/* Accuracy Gauge Section */}
            <div className="flex-1 flex justify-center">
              {totalAttempts > 0 ? (
                <SpeedometerGauge 
                  percentage={accuracy} 
                  size="xs"
                  showLabel={false}
                  showPercentage={true}
                />
              ) : (
                <div className="text-center">
                  <p className="text-2xl font-bold text-white/60">--</p>
                  <p className="text-xs text-white/40">No data yet</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Stats Section */}
        <div className="p-4 space-y-4">

          {/* Level Progress */}
          {levelStats.length > 0 && (
            <div>
              <p className="text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wide">Level Progress</p>
              <LevelProgressCard levelStats={levelStats} compact />
            </div>
          )}

          {/* Quick Stats Grid */}
          <div className="grid grid-cols-3 gap-3">
            <div className="text-center p-3 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 border border-primary/20">
              <Zap className="w-5 h-5 mx-auto mb-1 text-primary" />
              <p className="text-lg font-bold text-foreground">{totalXP.toLocaleString()}</p>
              <p className="text-[10px] text-muted-foreground uppercase tracking-wide">XP</p>
            </div>
            <div className="text-center p-3 rounded-xl bg-gradient-to-br from-blue-500/10 to-blue-500/5 border border-blue-500/20">
              <Target className="w-5 h-5 mx-auto mb-1 text-blue-500" />
              <p className="text-lg font-bold text-foreground">{totalAttempts}</p>
              <p className="text-[10px] text-muted-foreground uppercase tracking-wide">Attempts</p>
            </div>
            <div className="text-center p-3 rounded-xl bg-gradient-to-br from-green-500/10 to-green-500/5 border border-green-500/20">
              <CheckCircle className="w-5 h-5 mx-auto mb-1 text-green-500" />
              <p className="text-lg font-bold text-foreground">{totalCorrect}</p>
              <p className="text-[10px] text-muted-foreground uppercase tracking-wide">Correct</p>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
