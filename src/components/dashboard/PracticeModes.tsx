import { Link } from 'react-router-dom';
import { Play, Target, Timer, GraduationCap, Sparkles } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import { cn } from '@/lib/utils';

interface PracticeModesProps {
  displayName?: string;
  currentLevel?: CEFRLevel;
  levelProgress?: {
    level: CEFRLevel;
    remaining: number;
    total: number;
    accuracy: number;
  }[];
}

export const PracticeModes = ({ displayName, currentLevel, levelProgress }: PracticeModesProps) => {
  // Get remaining questions for current level
  const currentLevelProgress = levelProgress?.find(p => p.level === currentLevel);
  const remainingQuestions = currentLevelProgress?.remaining || 0;
  const totalQuestions = currentLevelProgress?.total || 0;
  const answeredQuestions = totalQuestions - remainingQuestions;

  // Reorder levels: current level first, then others in order
  const orderedLevels = currentLevel 
    ? [currentLevel, ...CEFR_LEVELS.filter(l => l !== currentLevel)]
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
        {/* Current level highlight banner */}
        {currentLevel && (
          <div className="mb-3 sm:mb-4 p-3 sm:p-4 rounded-xl bg-gradient-to-r from-primary/10 via-primary/5 to-transparent border border-primary/20">
            <div className="flex items-center justify-between gap-2 mb-2">
              <div className="flex items-center gap-2">
                <div className="w-8 h-8 sm:w-10 sm:h-10 rounded-lg bg-primary/20 flex items-center justify-center">
                  <Target className="w-4 h-4 sm:w-5 sm:h-5 text-primary" />
                </div>
                <div>
                  <p className="text-[10px] sm:text-xs text-muted-foreground">Your Current Level</p>
                  <p className="font-bold text-sm sm:text-base text-primary">{currentLevel}</p>
                </div>
              </div>
              <CEFRBadge level={currentLevel} size="lg" />
            </div>
            {totalQuestions > 0 && (
              <div className="mt-2">
                <div className="flex justify-between text-[10px] sm:text-xs mb-1">
                  <span className="text-muted-foreground">Progress</span>
                  <span className="font-medium">{answeredQuestions}/{totalQuestions}</span>
                </div>
                <div className="h-1.5 bg-muted rounded-full overflow-hidden">
                  <div 
                    className="h-full bg-primary rounded-full transition-all duration-500"
                    style={{ width: `${(answeredQuestions / totalQuestions) * 100}%` }}
                  />
                </div>
                <p className="text-[9px] sm:text-[10px] text-muted-foreground mt-1">
                  {remainingQuestions > 0 
                    ? `${remainingQuestions} questions remaining`
                    : '✓ All questions completed! Choose another level or reset.'
                  }
                </p>
              </div>
            )}
          </div>
        )}

        {/* Placement Test - Featured Button */}
        <Link to="/practice?mode=placement" className="block mb-3 sm:mb-4">
          <Card className="relative overflow-hidden cursor-pointer border-2 border-primary/50 hover:border-primary bg-gradient-to-r from-primary/10 via-primary/5 to-accent/10 transition-all hover:shadow-lg hover:shadow-primary/20 active:scale-[0.98]">
            <div className="absolute top-0 right-0 w-32 h-32 bg-primary/10 rounded-full -translate-y-1/2 translate-x-1/2 blur-2xl" />
            <div className="absolute bottom-0 left-0 w-24 h-24 bg-accent/10 rounded-full translate-y-1/2 -translate-x-1/2 blur-xl" />
            <CardContent className="relative p-4 sm:p-5 md:p-6">
              <div className="flex items-center gap-3 sm:gap-4">
                <div className="w-12 h-12 sm:w-14 sm:h-14 md:w-16 md:h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center shadow-lg shadow-primary/30">
                  <GraduationCap className="w-6 h-6 sm:w-7 sm:h-7 md:w-8 md:h-8 text-primary-foreground" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="font-bold text-sm sm:text-base md:text-lg text-foreground">
                      Placement Test
                    </h3>
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-primary/20 text-primary text-[10px] sm:text-xs font-medium">
                      <Sparkles className="w-3 h-3" />
                      All Levels
                    </span>
                  </div>
                  <p className="text-[11px] sm:text-xs md:text-sm text-muted-foreground line-clamp-2">
                    Mixed questions from A1 to C2 to determine your exact level
                  </p>
                </div>
                <div className="hidden sm:flex items-center gap-1 text-primary">
                  <span className="text-xs font-medium">Start</span>
                  <Play className="w-4 h-4 fill-current" />
                </div>
              </div>
              {/* Level badges row */}
              <div className="flex items-center gap-1.5 mt-3 pt-3 border-t border-border/50">
                {CEFR_LEVELS.map((level) => (
                  <span 
                    key={level} 
                    className="px-2 py-0.5 rounded text-[9px] sm:text-[10px] font-medium bg-muted/50 text-muted-foreground"
                  >
                    {level}
                  </span>
                ))}
              </div>
            </CardContent>
          </Card>
        </Link>

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
