import { Flame, Target, CheckCircle, Zap } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';
import { isToday, isYesterday, parseISO } from 'date-fns';
import { cn } from '@/lib/utils';

interface MobileStatsHeaderProps {
  streak: number;
  lastPracticeDate: string | null;
  totalXP: number;
  totalAttempts: number;
  totalCorrect: number;
  accuracy: number;
}

export const MobileStatsHeader = ({
  streak,
  lastPracticeDate,
  totalXP,
  totalAttempts,
  totalCorrect,
  accuracy,
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
    <Card className="overflow-hidden border-0 shadow-lg bg-gradient-to-br from-card via-card to-muted/30">
      <CardContent className="p-0">
        {/* Streak Banner */}
        <div className="bg-gradient-to-r from-orange-500 via-red-500 to-rose-600 p-4 relative overflow-hidden">
          {/* Background decoration */}
          <div className="absolute inset-0 opacity-20">
            <div className="absolute top-0 right-0 w-32 h-32 bg-white/10 rounded-full -translate-y-1/2 translate-x-1/2" />
            <div className="absolute bottom-0 left-0 w-24 h-24 bg-white/10 rounded-full translate-y-1/2 -translate-x-1/2" />
          </div>
          
          <div className="relative flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className={cn(
                "p-2 rounded-full bg-white/20 backdrop-blur-sm",
                isStreakActive && "animate-pulse"
              )}>
                <Flame className="w-8 h-8 text-white drop-shadow-lg" />
              </div>
              <div>
                <p className="text-4xl font-bold text-white drop-shadow-md">{streak}</p>
                <p className="text-sm text-white/90 font-medium">Day Streak</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs text-white/80">{getStreakStatus()}</p>
            </div>
          </div>
        </div>

        {/* Stats Section */}
        <div className="p-4 space-y-4">
          {/* Accuracy Gauge - Centered */}
          {totalAttempts > 0 && (
            <div className="flex justify-center py-2">
              <SpeedometerGauge 
                percentage={accuracy} 
                size="sm"
                showLabel={true}
                showPercentage={true}
              />
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
