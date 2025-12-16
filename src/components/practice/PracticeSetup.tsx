import { Loader2, AlertTriangle, GraduationCap, Sparkles } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { CEFRBadge } from '@/components/CEFRBadge';
import { QuestionProgressCard } from './QuestionProgressCard';
import { PRACTICE_MODES, CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import type { LevelProgress } from '@/hooks/useQuestionProgress';

interface PracticeSetupProps {
  selectedLevel: CEFRLevel;
  mode: string;
  loading: boolean;
  levelProgress?: LevelProgress;
  onLevelChange: (level: CEFRLevel) => void;
  onModeChange: (mode: string) => void;
  onStart: () => void;
  onResetProgress: () => void;
  isResetting?: boolean;
  isPlacementMode?: boolean;
}

export const PracticeSetup = ({
  selectedLevel,
  mode,
  loading,
  levelProgress,
  onLevelChange,
  onModeChange,
  onStart,
  onResetProgress,
  isResetting = false,
  isPlacementMode = false,
}: PracticeSetupProps) => {
  const hasQuestionsRemaining = !levelProgress || levelProgress.remaining_questions > 0;

  return (
    <Card className="animate-fade-in w-full max-w-full overflow-hidden">
      <CardHeader className="text-center p-3 sm:p-4 md:p-6">
        {isPlacementMode ? (
          <>
            <div className="w-14 h-14 sm:w-16 sm:h-16 rounded-2xl bg-gradient-to-br from-primary to-primary/70 flex items-center justify-center mx-auto mb-3 shadow-lg shadow-primary/30">
              <GraduationCap className="w-7 h-7 sm:w-8 sm:h-8 text-primary-foreground" />
            </div>
            <CardTitle className="font-serif text-lg sm:text-xl md:text-2xl">Placement Test</CardTitle>
            <p className="text-xs sm:text-sm text-muted-foreground mt-2">
              Discover your English level with mixed questions from A1 to C2
            </p>
          </>
        ) : (
          <CardTitle className="font-serif text-lg sm:text-xl md:text-2xl">Start Practice Session</CardTitle>
        )}
      </CardHeader>
      <CardContent className="space-y-3 sm:space-y-4 md:space-y-6 p-3 sm:p-4 md:p-6 pt-0">
        {isPlacementMode ? (
          <div className="space-y-3">
            <div className="p-3 sm:p-4 rounded-xl bg-gradient-to-r from-primary/10 via-primary/5 to-transparent border border-primary/20">
              <div className="flex items-center gap-2 mb-2">
                <Sparkles className="w-4 h-4 text-primary" />
                <span className="text-sm font-medium text-primary">Comprehensive Level Assessment</span>
              </div>
              <div className="flex flex-wrap gap-1.5">
                {CEFR_LEVELS.map((level) => (
                  <span 
                    key={level} 
                    className="px-2 py-1 rounded-md text-xs font-medium bg-muted/50 text-muted-foreground"
                  >
                    {level}: 50
                  </span>
                ))}
              </div>
            </div>
            <div className="text-xs sm:text-sm text-muted-foreground text-center space-y-1">
              <p className="font-medium text-foreground">300 Total Questions</p>
              <p>• 50 questions from each level (A1 - C2)</p>
              <p>• Only unanswered questions will be shown</p>
              <p>• Results determine your recommended level</p>
            </div>
          </div>
        ) : (
          <>
            <div>
              <label className="text-xs sm:text-sm font-medium mb-1.5 sm:mb-2 block">Select Level</label>
              <Select value={selectedLevel} onValueChange={(v) => onLevelChange(v as CEFRLevel)}>
                <SelectTrigger className="min-h-[44px] sm:min-h-[48px]">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="bg-popover z-50">
                  {CEFR_LEVELS.map((level) => (
                    <SelectItem key={level} value={level} className="min-h-[40px] sm:min-h-[44px]">
                      <div className="flex items-center gap-2">
                        <CEFRBadge level={level} size="sm" />
                        <span className="text-sm">{level}</span>
                      </div>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <label className="text-xs sm:text-sm font-medium mb-1.5 sm:mb-2 block">Select Mode</label>
              <div className="grid grid-cols-2 gap-2 sm:gap-3 md:gap-4">
                <Button
                  variant={mode === PRACTICE_MODES.FIFTY_QUESTIONS ? 'default' : 'outline'}
                  className="h-auto py-3 sm:py-4 min-h-[60px] sm:min-h-[72px]"
                  onClick={() => onModeChange(PRACTICE_MODES.FIFTY_QUESTIONS)}
                >
                  <div className="text-center">
                    <p className="font-semibold text-xs sm:text-sm md:text-base">50 Questions</p>
                    <p className="text-[10px] sm:text-xs opacity-80">Full practice</p>
                  </div>
                </Button>
                <Button
                  variant={mode === PRACTICE_MODES.TIMED ? 'default' : 'outline'}
                  className="h-auto py-3 sm:py-4 min-h-[60px] sm:min-h-[72px]"
                  onClick={() => onModeChange(PRACTICE_MODES.TIMED)}
                >
                  <div className="text-center">
                    <p className="font-semibold text-xs sm:text-sm md:text-base">Timed Mode</p>
                    <p className="text-[10px] sm:text-xs opacity-80">30 minutes</p>
                  </div>
                </Button>
              </div>
            </div>
          </>
        )}

        <Button 
          className="w-full min-h-[48px] sm:min-h-[52px] text-sm sm:text-base" 
          size="lg" 
          onClick={onStart}
          disabled={loading || (!isPlacementMode && !hasQuestionsRemaining)}
        >
          {loading ? (
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
          ) : null}
          {isPlacementMode ? 'Start Placement Test' : (!hasQuestionsRemaining ? 'All Questions Completed' : 'Start Practice')}
        </Button>

        {!isPlacementMode && !hasQuestionsRemaining && (
          <div className="flex items-start sm:items-center gap-2 p-2.5 sm:p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400">
            <AlertTriangle className="w-4 h-4 flex-shrink-0 mt-0.5 sm:mt-0" />
            <p className="text-[10px] sm:text-xs leading-tight">
              You've completed all questions in {selectedLevel}. Reset your progress or try another level!
            </p>
          </div>
        )}

        {/* Question Progress Card - only show for regular practice */}
        {!isPlacementMode && (
          <QuestionProgressCard
            levelProgress={levelProgress}
            selectedLevel={selectedLevel}
            onResetProgress={onResetProgress}
            isResetting={isResetting}
          />
        )}
      </CardContent>
    </Card>
  );
};
