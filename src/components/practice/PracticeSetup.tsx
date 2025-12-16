import { Loader2, AlertTriangle } from 'lucide-react';
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
}: PracticeSetupProps) => {
  const hasQuestionsRemaining = !levelProgress || levelProgress.remaining_questions > 0;

  return (
    <Card className="animate-fade-in w-full max-w-full">
      <CardHeader className="text-center p-4 md:p-6">
        <CardTitle className="font-serif text-xl md:text-2xl">Start Practice Session</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 md:space-y-6 p-4 md:p-6 pt-0">
        <div>
          <label className="text-sm font-medium mb-2 block">Select Level</label>
          <Select value={selectedLevel} onValueChange={(v) => onLevelChange(v as CEFRLevel)}>
            <SelectTrigger className="min-h-[48px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent className="bg-popover">
              {CEFR_LEVELS.map((level) => (
                <SelectItem key={level} value={level} className="min-h-[44px]">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={level} size="sm" />
                    <span>{level}</span>
                  </div>
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>

        <div>
          <label className="text-sm font-medium mb-2 block">Select Mode</label>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 md:gap-4">
            <Button
              variant={mode === PRACTICE_MODES.TEN_QUESTIONS ? 'default' : 'outline'}
              className="h-auto py-4 min-h-[72px]"
              onClick={() => onModeChange(PRACTICE_MODES.TEN_QUESTIONS)}
            >
              <div className="text-center">
                <p className="font-semibold text-sm md:text-base">10 Questions</p>
                <p className="text-xs opacity-80">Quick practice</p>
              </div>
            </Button>
            <Button
              variant={mode === PRACTICE_MODES.TIMED ? 'default' : 'outline'}
              className="h-auto py-4 min-h-[72px]"
              onClick={() => onModeChange(PRACTICE_MODES.TIMED)}
            >
              <div className="text-center">
                <p className="font-semibold text-sm md:text-base">Timed Mode</p>
                <p className="text-xs opacity-80">10 minutes</p>
              </div>
            </Button>
          </div>
        </div>

        <Button 
          className="w-full min-h-[52px]" 
          size="lg" 
          onClick={onStart}
          disabled={loading || !hasQuestionsRemaining}
        >
          {loading ? (
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
          ) : null}
          {!hasQuestionsRemaining ? 'All Questions Completed' : 'Start Practice'}
        </Button>

        {!hasQuestionsRemaining && (
          <div className="flex items-center gap-2 p-3 rounded-lg bg-amber-500/10 border border-amber-500/20 text-amber-600 dark:text-amber-400">
            <AlertTriangle className="w-4 h-4 flex-shrink-0" />
            <p className="text-xs">
              You've completed all questions in {selectedLevel}. Reset your progress or try another level!
            </p>
          </div>
        )}

        {/* Question Progress Card */}
        <QuestionProgressCard
          levelProgress={levelProgress}
          selectedLevel={selectedLevel}
          onResetProgress={onResetProgress}
          isResetting={isResetting}
        />
      </CardContent>
    </Card>
  );
};
