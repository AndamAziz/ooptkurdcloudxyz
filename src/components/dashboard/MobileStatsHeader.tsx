import { Flame, Target, CheckCircle, Zap, TrendingUp, TrendingDown } from 'lucide-react';
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
  todayXP?: number;
  todayAttempts?: number;
  todayCorrect?: number;
}

export const MobileStatsHeader = ({
  streak,
  lastPracticeDate,
  totalXP,
  totalAttempts,
  totalCorrect,
  accuracy,
  levelStats,
  todayXP = 0,
  todayAttempts = 0,
  todayCorrect = 0,
}: MobileStatsHeaderProps) => {
  const getStreakStatus = () => {
    if (!lastPracticeDate) return 'Start your streak!';
    const date = parseISO(lastPracticeDate);
    if (isToday(date)) return 'Keep going!';
    if (isYesterday(date)) return 'Practice today!';
    return 'Start new streak!';
  };

  const isStreakActive = lastPracticeDate && (isToday(parseISO(lastPracticeDate)) || isYesterday(parseISO(lastPracticeDate)));

  return (
    <Card className="overflow-hidden border-0 shadow-xl bg-gradient-to-br from-card via-card to-muted/20">
      <CardContent className="p-0">
        {/* Combined Stats Banner - More Compact */}
        <div className="bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-3 sm:p-4 relative overflow-hidden">
          {/* Background decoration */}
          <div className="absolute inset-0 opacity-30">
            <div className="absolute top-0 right-0 w-32 h-32 bg-orange-500/20 rounded-full -translate-y-1/2 translate-x-1/2 blur-2xl" />
            <div className="absolute bottom-0 left-0 w-24 h-24 bg-green-500/20 rounded-full translate-y-1/2 -translate-x-1/2 blur-2xl" />
          </div>
          
          <div className="relative flex items-center gap-2 sm:gap-3">
            {/* Streak Section - 30% width */}
            <div className="flex items-center gap-2 w-[30%] flex-shrink-0">
              <div className={cn(
                "p-2 rounded-lg bg-gradient-to-br from-orange-500 to-rose-600 shadow-lg shadow-orange-500/30",
                isStreakActive && "animate-pulse"
              )}>
                <Flame className="w-5 h-5 sm:w-6 sm:h-6 text-white drop-shadow-lg" />
              </div>
              <div className="min-w-0">
                <p className="text-xl sm:text-2xl font-bold text-white drop-shadow-md">{streak}</p>
                <p className="text-[10px] sm:text-xs text-white/70 font-medium leading-tight">Day Streak</p>
                <p className="text-[9px] sm:text-[10px] text-white/50 truncate">{getStreakStatus()}</p>
              </div>
            </div>
            
            {/* Divider */}
            <div className="w-px h-14 sm:h-16 bg-white/20 flex-shrink-0" />
            
            {/* Accuracy Gauge Section - 50% width */}
            <div className="flex-1 flex justify-center items-center">
              {totalAttempts > 0 ? (
                <div className="max-w-[140px] sm:max-w-[160px] md:max-w-[200px]">
                  <SpeedometerGauge 
                    percentage={accuracy} 
                    size="xs"
                    showLabel={false}
                    showPercentage={true}
                  />
                </div>
              ) : (
                <div className="text-center">
                  <p className="text-xl sm:text-2xl font-bold text-white/60">--</p>
                  <p className="text-[10px] sm:text-xs text-white/40">No data yet</p>
                </div>
              )}
            </div>
          </div>
        </div>

        {/* Stats Section */}
        <div className="p-3 sm:p-4 space-y-3 sm:space-y-4">

          {/* Level Progress */}
          {levelStats.length > 0 && (
            <div>
              <p className="text-[10px] sm:text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wide">Level Progress</p>
              <LevelProgressCard levelStats={levelStats} compact />
            </div>
          )}

          {/* Quick Stats Grid - Enhanced with today stats */}
          <div className="grid grid-cols-3 gap-2 sm:gap-3">
            <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 border border-primary/20 shadow-sm hover:scale-[1.02] transition-transform active:scale-[0.98]">
              <Zap className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-primary" />
              <p className="text-base sm:text-lg font-bold text-foreground">{totalXP.toLocaleString()}</p>
              <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">XP</p>
              {todayXP > 0 && (
                <p className="text-[9px] sm:text-[10px] text-primary font-medium flex items-center justify-center gap-0.5 mt-0.5">
                  <TrendingUp className="w-2.5 h-2.5" />
                  +{todayXP} today
                </p>
              )}
            </div>
            <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-orange-500/10 to-orange-500/5 border border-orange-500/20 shadow-sm hover:scale-[1.02] transition-transform active:scale-[0.98]">
              <Target className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-orange-500" />
              <p className="text-base sm:text-lg font-bold text-foreground">{totalAttempts}</p>
              <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">Attempts</p>
              {todayAttempts > 0 && (
                <p className="text-[9px] sm:text-[10px] text-orange-500 font-medium flex items-center justify-center gap-0.5 mt-0.5">
                  <TrendingUp className="w-2.5 h-2.5" />
                  +{todayAttempts} today
                </p>
              )}
            </div>
            <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-green-500/10 to-green-500/5 border border-green-500/20 shadow-sm hover:scale-[1.02] transition-transform active:scale-[0.98]">
              <CheckCircle className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-green-500" />
              <p className="text-base sm:text-lg font-bold text-foreground">{totalCorrect}</p>
              <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">Correct</p>
              {todayCorrect > 0 && (
                <p className="text-[9px] sm:text-[10px] text-green-500 font-medium flex items-center justify-center gap-0.5 mt-0.5">
                  <TrendingUp className="w-2.5 h-2.5" />
                  +{todayCorrect} today
                </p>
              )}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
