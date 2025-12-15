import { Loader2 } from 'lucide-react';
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
import { PRACTICE_MODES, CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';

interface PracticeSetupProps {
  selectedLevel: CEFRLevel;
  mode: string;
  loading: boolean;
  onLevelChange: (level: CEFRLevel) => void;
  onModeChange: (mode: string) => void;
  onStart: () => void;
}

export const PracticeSetup = ({
  selectedLevel,
  mode,
  loading,
  onLevelChange,
  onModeChange,
  onStart,
}: PracticeSetupProps) => {
  return (
    <Card className="animate-fade-in">
      <CardHeader className="text-center">
        <CardTitle className="font-serif text-2xl">Start Practice Session</CardTitle>
      </CardHeader>
      <CardContent className="space-y-6">
        <div>
          <label className="text-sm font-medium mb-2 block">Select Level</label>
          <Select value={selectedLevel} onValueChange={(v) => onLevelChange(v as CEFRLevel)}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {CEFR_LEVELS.map((level) => (
                <SelectItem key={level} value={level}>
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
          <div className="grid grid-cols-2 gap-4">
            <Button
              variant={mode === PRACTICE_MODES.TEN_QUESTIONS ? 'default' : 'outline'}
              className="h-auto py-4"
              onClick={() => onModeChange(PRACTICE_MODES.TEN_QUESTIONS)}
            >
              <div className="text-center">
                <p className="font-semibold">10 Questions</p>
                <p className="text-xs opacity-80">Quick practice</p>
              </div>
            </Button>
            <Button
              variant={mode === PRACTICE_MODES.TIMED ? 'default' : 'outline'}
              className="h-auto py-4"
              onClick={() => onModeChange(PRACTICE_MODES.TIMED)}
            >
              <div className="text-center">
                <p className="font-semibold">Timed Mode</p>
                <p className="text-xs opacity-80">10 minutes</p>
              </div>
            </Button>
          </div>
        </div>

        <Button 
          className="w-full" 
          size="lg" 
          onClick={onStart}
          disabled={loading}
        >
          {loading ? (
            <Loader2 className="w-4 h-4 mr-2 animate-spin" />
          ) : null}
          Start Practice
        </Button>
      </CardContent>
    </Card>
  );
};
