import { Link } from 'react-router-dom';
import { Play, Target, Timer } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import { cn } from '@/lib/utils';

interface PracticeModesProps {
  displayName?: string;
  currentLevel?: CEFRLevel;
  currentXP?: number;
  levelProgress?: {
    level: CEFRLevel;
    remaining: number;
    total: number;
    accuracy: number;
  }[];
}

// XP thresholds for each level
const LEVEL_XP_THRESHOLDS: Record<CEFRLevel, number> = {
  'A1': 0,
  'A2': 800,
  'B1': 1800,
  'B2': 3200,
  'C1': 4800,
  'C2': 6500,
};

export const PracticeModes = ({ displayName, currentLevel, currentXP = 0, levelProgress }: PracticeModesProps) => {
  // Find recommended level based on practice progress (first incomplete level)
  const recommendedLevel = levelProgress?.find(p => p.remaining > 0)?.level || currentLevel;
  
  // Get remaining questions for recommended level
  const recommendedLevelProgress = levelProgress?.find(p => p.level === recommendedLevel);
  const remainingQuestions = recommendedLevelProgress?.remaining || 0;
  const totalQuestions = recommendedLevelProgress?.total || 0;
  const answeredQuestions = totalQuestions - remainingQuestions;

  // Calculate XP progress to next level
  const nextLevelIndex = CEFR_LEVELS.indexOf(currentLevel || 'A1') + 1;
  const nextLevel = CEFR_LEVELS[nextLevelIndex] as CEFRLevel | undefined;
  const nextLevelXP = nextLevel ? LEVEL_XP_THRESHOLDS[nextLevel] : LEVEL_XP_THRESHOLDS['C2'];
  const currentLevelXP = currentLevel ? LEVEL_XP_THRESHOLDS[currentLevel] : 0;
  const xpForNextLevel = nextLevelXP - currentLevelXP;
  const xpProgress = currentXP - currentLevelXP;
  const xpProgressPercent = xpForNextLevel > 0 ? Math.min((xpProgress / xpForNextLevel) * 100, 100) : 100;

  // Reorder levels: recommended/current level first, then others in order
  const primaryLevel = recommendedLevel || currentLevel;
  const orderedLevels = primaryLevel 
    ? [primaryLevel, ...CEFR_LEVELS.filter(l => l !== primaryLevel)]
    : CEFR_LEVELS;

  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-3 sm:p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-base sm:text-lg md:text-xl">
          <Play className="w-4 h-4 sm:w-5 sm:h-5" />
          Start Practice
        </CardTitle>
        <CardDescription className="text-xs sm:text-sm">
          Choose your practice mode and level
        </CardDescription>
      </CardHeader>
      <CardContent className="p-3 sm:p-4 md:p-6 pt-0">
        {/* Current Level & XP Progress */}
        <div className="mb-3 sm:mb-4 p-3 sm:p-4 rounded-xl bg-gradient-to-r from-primary/10 via-primary/5 to-transparent border border-primary/20">
          <div className="flex items-center justify-between gap-2 mb-2">
            <div className="flex items-center gap-2">
              <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-lg bg-primary/20 flex items-center justify-center">
                <Target className="w-4 h-4 sm:w-5 sm:h-5 text-primary" />
              </div>
              <div>
                <p className="text-[10px] sm:text-xs text-muted-foreground">Your Level (XP Based)</p>
                <p className="font-bold text-sm sm:text-base text-primary">{currentLevel}</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs sm:text-sm font-bold text-foreground">{currentXP} XP</p>
              {nextLevel && (
                <p className="text-[9px] sm:text-[10px] text-muted-foreground">
                  {nextLevelXP - currentXP} XP to {nextLevel}
                </p>
              )}
            </div>
          </div>
          {nextLevel && (
            <div className="h-1.5 bg-muted rounded-full overflow-hidden">
              <div 
                className="h-full bg-primary rounded-full transition-all duration-500"
                style={{ width: `${xpProgressPercent}%` }}
              />
            </div>
          )}
        </div>

        {/* Recommended Practice Level */}
        {recommendedLevel && recommendedLevel !== currentLevel && totalQuestions > 0 && (
          <div className="mb-3 sm:mb-4 p-2.5 sm:p-3 rounded-lg bg-green-500/10 border border-green-500/20">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2">
                <CEFRBadge level={recommendedLevel} size="sm" />
                <span className="text-xs sm:text-sm font-medium">Practice: {recommendedLevel}</span>
              </div>
              <span className="text-[10px] sm:text-xs text-green-600 font-medium">
                {remainingQuestions > 0 ? `${answeredQuestions}/${totalQuestions}` : '✓ Complete'}
              </span>
            </div>
          </div>
        )}

        <div className="grid gap-2 sm:gap-3 md:gap-4 grid-cols-2">
          <Link to="/practice?mode=10_questions" className="block">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary h-full transition-all active:scale-[0.98]">
              <CardContent className="p-3 sm:p-4 md:p-6 text-center">
                <div className="w-9 h-9 sm:w-10 sm:h-10 md:w-12 md:h-12 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-2 sm:mb-3 md:mb-4">
                  <Target className="w-4 h-4 sm:w-5 sm:h-5 md:w-6 md:h-6 text-primary" />
                </div>
                <h3 className="font-semibold mb-0.5 sm:mb-1 text-xs sm:text-sm md:text-base">10 Questions</h3>
                <p className="text-[10px] sm:text-xs md:text-sm text-muted-foreground">
                  Quick practice
                </p>
              </CardContent>
            </Card>
          </Link>
          
          <Link to="/practice?mode=timed" className="block">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary h-full transition-all active:scale-[0.98]">
              <CardContent className="p-3 sm:p-4 md:p-6 text-center">
                <div className="w-9 h-9 sm:w-10 sm:h-10 md:w-12 md:h-12 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-2 sm:mb-3 md:mb-4">
                  <Timer className="w-4 h-4 sm:w-5 sm:h-5 md:w-6 md:h-6 text-warning" />
                </div>
                <h3 className="font-semibold mb-0.5 sm:mb-1 text-xs sm:text-sm md:text-base">Timed Mode</h3>
                <p className="text-[10px] sm:text-xs md:text-sm text-muted-foreground">
                  10 min challenge
                </p>
              </CardContent>
            </Card>
          </Link>
        </div>

        <div className="mt-3 sm:mt-4 md:mt-6">
          <div className="flex flex-wrap items-center gap-1.5 sm:gap-2 mb-2 sm:mb-3">
            <p className="text-[10px] sm:text-xs md:text-sm font-medium">Select Level:</p>
            {displayName && (
              <span className="text-[10px] sm:text-xs text-muted-foreground ml-auto">
                <span className="hidden sm:inline">Welcome, </span>
                <span className="font-medium text-foreground">{displayName}</span>
              </span>
            )}
          </div>
          <div className="flex flex-wrap gap-1.5 sm:gap-2">
            {orderedLevels.map((level) => {
              const progress = levelProgress?.find(p => p.level === level);
              const isCurrentLevel = level === currentLevel;
              const isCompleted = progress && progress.remaining === 0;
              
              return (
                <Link key={level} to={`/practice?level=${level}`}>
                  <Button 
                    variant={isCurrentLevel ? "default" : "outline"} 
                    size="sm" 
                    className={cn(
                      "min-h-[40px] sm:min-h-[44px] touch-target px-2 sm:px-3 transition-all active:scale-[0.96]",
                      isCurrentLevel && "ring-2 ring-primary ring-offset-2 ring-offset-background shadow-lg",
                      isCompleted && !isCurrentLevel && "bg-green-500/10 border-green-500/30"
                    )}
                  >
                    <CEFRBadge level={level} size="sm" />
                    {isCompleted && (
                      <span className="ml-1 text-[10px] text-green-500">✓</span>
                    )}
                  </Button>
                </Link>
              );
            })}
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
