import { Flame, Target, CheckCircle, Zap, TrendingUp, Award, Lock } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';
import { Progress } from '@/components/ui/progress';
import { isToday, isYesterday, parseISO } from 'date-fns';
import { cn } from '@/lib/utils';
import type { LevelProgress } from '@/hooks/useQuestionProgress';
import type { CEFRLevel } from '@/lib/constants';

interface MobileStatsHeaderProps {
  streak: number;
  lastPracticeDate: string | null;
  totalXP: number;
  totalAttempts: number;
  totalCorrect: number;
  overallAccuracy: number;
  currentLevel: CEFRLevel;
  currentLevelAccuracy: number;
  currentLevelAnswered: number;
  currentLevelTotal: number;
  questionProgress: LevelProgress[];
  earnedCertificates?: CEFRLevel[];
  onLevelChange?: (level: CEFRLevel) => void;
}

export const MobileStatsHeader = ({
  streak,
  lastPracticeDate,
  totalXP,
  totalAttempts,
  totalCorrect,
  currentLevel,
  currentLevelAccuracy,
  currentLevelAnswered,
  currentLevelTotal,
  questionProgress,
  earnedCertificates = [],
  onLevelChange,
}: MobileStatsHeaderProps) => {
  const getStreakStatus = () => {
    if (!lastPracticeDate) return 'Start your streak!';
    const date = parseISO(lastPracticeDate);
    if (isToday(date)) return 'Keep going!';
    if (isYesterday(date)) return 'Practice today!';
    return 'Start new streak!';
  };

  const isStreakActive = lastPracticeDate && (isToday(parseISO(lastPracticeDate)) || isYesterday(parseISO(lastPracticeDate)));

  const currentLevelProgress = questionProgress.find(p => p.cefr === currentLevel);

  const isLevelLocked = (level: CEFRLevel, index: number) => {
    if (index === 0) return false;
    const levels: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
    const prevLevel = levels[index - 1];
    return !earnedCertificates.includes(prevLevel);
  };

  return (
    <Card className="overflow-hidden border-0 shadow-xl bg-gradient-to-br from-card via-card to-muted/20">
      <CardContent className="p-0">
        {/* Combined Stats Banner */}
        <div className="bg-gradient-to-br from-slate-900 via-slate-800 to-slate-900 p-3 sm:p-4 relative overflow-hidden">
          <div className="absolute inset-0 opacity-30">
            <div className="absolute top-0 right-0 w-32 h-32 bg-orange-500/20 rounded-full -translate-y-1/2 translate-x-1/2 blur-2xl" />
            <div className="absolute bottom-0 left-0 w-24 h-24 bg-green-500/20 rounded-full translate-y-1/2 -translate-x-1/2 blur-2xl" />
          </div>
          
          <div className="relative flex items-center gap-2 sm:gap-3">
            {/* Streak Section */}
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
            
            <div className="w-px h-14 sm:h-16 bg-white/20 flex-shrink-0" />
            
            {/* Current Level Accuracy Gauge */}
            <div className="flex-1 flex flex-col justify-center items-center min-w-0">
              {currentLevelAnswered > 0 ? (
                <div className="w-full flex justify-center">
                  <SpeedometerGauge 
                    percentage={currentLevelAccuracy} 
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
              <p className="text-[9px] sm:text-[10px] text-white/70 mt-1 text-center">
                {currentLevel} Level ({currentLevelAnswered}/{currentLevelTotal})
              </p>
            </div>
          </div>
        </div>

        <div className="p-3 sm:p-4 space-y-3 sm:space-y-4">
          {/* Level Progress Grid */}
          {questionProgress.length > 0 && (
            <div>
              <p className="text-[10px] sm:text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wide">
                Level Progress
              </p>
              <div className="grid grid-cols-3 sm:grid-cols-6 gap-1.5 sm:gap-2">
                {questionProgress.map((level, index) => {
                  const locked = isLevelLocked(level.cefr, index);
                  const earned = earnedCertificates.includes(level.cefr);
                  const isCurrentLevel = level.cefr === currentLevel;
                  
                  return (
                    <div 
                      key={level.cefr}
                      className={cn(
                        "text-center p-1.5 sm:p-2 rounded-lg border transition-all",
                        locked && "opacity-50 bg-muted/30",
                        isCurrentLevel && !locked && "bg-primary/10 border-primary/30",
                        earned && "bg-green-500/10 border-green-500/30",
                        !locked && !isCurrentLevel && !earned && "bg-muted/30 border-border/50"
                      )}
                    >
                      <div className="flex items-center justify-center gap-0.5">
                        <p className="text-[10px] sm:text-xs font-bold">{level.cefr}</p>
                        {locked && <Lock className="w-2.5 h-2.5 text-muted-foreground" />}
                        {earned && <Award className="w-2.5 h-2.5 text-green-500" />}
                      </div>
                      
                      {!locked ? (
                        <>
                          <p className={cn(
                            "text-sm sm:text-base font-bold",
                            level.answered_questions > 0 
                              ? level.accuracy >= 90 ? "text-green-500" 
                                : level.accuracy >= 70 ? "text-yellow-500" 
                                : "text-orange-500"
                              : "text-muted-foreground"
                          )}>
                            {level.answered_questions > 0 ? `${level.accuracy}%` : '--'}
                          </p>
                          <p className="text-[8px] sm:text-[9px] text-muted-foreground">
                            {level.answered_questions}/{level.total_questions}
                          </p>
                          {/* Mini completion bar */}
                          <div className="w-full h-0.5 rounded-full bg-muted mt-0.5 overflow-hidden">
                            <div 
                              className={cn(
                                "h-full rounded-full",
                                level.accuracy >= 90 ? "bg-green-500" :
                                level.accuracy >= 70 ? "bg-yellow-500" : "bg-primary"
                              )}
                              style={{ width: `${level.completion_percent}%` }}
                            />
                          </div>
                        </>
                      ) : (
                        <p className="text-[9px] text-muted-foreground mt-1">Locked</p>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Current Level Details */}
          {currentLevelProgress && currentLevelProgress.answered_questions > 0 && (
            <div className="p-2.5 rounded-lg bg-muted/30 border">
              <p className="text-[10px] sm:text-xs font-medium text-muted-foreground mb-1.5 uppercase tracking-wide">
                {currentLevel} Level Details
              </p>
              <div className="grid grid-cols-4 gap-2 text-center mb-2">
                <div>
                  <p className="text-sm font-bold">{currentLevelProgress.answered_questions}</p>
                  <p className="text-[8px] text-muted-foreground">Answered</p>
                </div>
                <div>
                  <p className="text-sm font-bold text-green-500">{currentLevelProgress.correct_count}</p>
                  <p className="text-[8px] text-muted-foreground">Correct</p>
                </div>
                <div>
                  <p className="text-sm font-bold text-red-500">{currentLevelProgress.wrong_count}</p>
                  <p className="text-[8px] text-muted-foreground">Wrong</p>
                </div>
                <div>
                  <p className="text-sm font-bold">{currentLevelProgress.remaining_questions}</p>
                  <p className="text-[8px] text-muted-foreground">Left</p>
                </div>
              </div>
              
              {/* Certificate progress */}
              <div className="mt-2 pt-2 border-t border-border/50">
                <div className="flex items-center justify-between text-[9px] mb-1">
                  <span className="text-muted-foreground">🎓 Certificate Progress</span>
                  <span className="text-muted-foreground">
                    {currentLevelProgress.correct_count}/{currentLevelProgress.certificate_target} (90%)
                  </span>
                </div>
                <Progress 
                  value={(currentLevelProgress.correct_count / currentLevelProgress.certificate_target) * 100} 
                  className="h-1.5"
                />
                <p className="text-[9px] text-muted-foreground mt-1">
                  {currentLevelProgress.correct_needed_for_cert > 0 
                    ? `Need ${currentLevelProgress.correct_needed_for_cert} more correct answers`
                    : currentLevelProgress.remaining_questions > 0
                      ? `Complete ${currentLevelProgress.remaining_questions} more questions`
                      : '✅ Certificate requirements met!'
                  }
                </p>
              </div>
            </div>
          )}

          {/* Overall Stats Grid */}
          <div>
            <p className="text-[10px] sm:text-xs font-medium text-muted-foreground mb-2 uppercase tracking-wide">
              Overall Stats
            </p>
            <div className="grid grid-cols-3 gap-2 sm:gap-3">
              <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-primary/10 to-primary/5 border border-primary/20">
                <Zap className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-primary" />
                <p className="text-base sm:text-lg font-bold text-foreground">{totalXP.toLocaleString()}</p>
                <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">XP</p>
              </div>
              <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-orange-500/10 to-orange-500/5 border border-orange-500/20">
                <Target className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-orange-500" />
                <p className="text-base sm:text-lg font-bold text-foreground">{totalAttempts}</p>
                <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">Attempts</p>
              </div>
              <div className="text-center p-2.5 sm:p-3 rounded-xl bg-gradient-to-br from-green-500/10 to-green-500/5 border border-green-500/20">
                <CheckCircle className="w-4 h-4 sm:w-5 sm:h-5 mx-auto mb-1 text-green-500" />
                <p className="text-base sm:text-lg font-bold text-foreground">{totalCorrect}</p>
                <p className="text-[9px] sm:text-[10px] text-muted-foreground uppercase tracking-wide">Correct</p>
              </div>
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
