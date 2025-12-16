import { Card, CardContent } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { XPProgressBar } from '@/components/XPProgressBar';
import { LEVEL_DESCRIPTIONS, type CEFRLevel } from '@/lib/constants';

interface WelcomeCardProps {
  displayName: string;
  currentLevel: CEFRLevel;
  currentXP: number;
}

export const WelcomeCard = ({ displayName, currentLevel, currentXP }: WelcomeCardProps) => {
  return (
    <Card className="overflow-hidden w-full max-w-full">
      <div className="bg-gradient-to-r from-primary to-primary/80 p-4 md:p-6 text-primary-foreground">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div>
            <h1 className="font-serif text-xl md:text-2xl font-bold mb-1">
              Welcome back, {displayName}!
            </h1>
            <p className="text-primary-foreground/80 text-sm md:text-base">
              Continue your journey to English mastery
            </p>
          </div>
          <CEFRBadge level={currentLevel} size="lg" />
        </div>
      </div>
      <CardContent className="p-4 md:p-6">
        <div className="mb-3 md:mb-4">
          <div className="flex items-center justify-between mb-2">
            <span className="font-medium text-sm md:text-base">Level Progress</span>
            <span className="text-xs md:text-sm text-muted-foreground">{currentLevel}</span>
          </div>
          <XPProgressBar currentXP={currentXP} currentLevel={currentLevel} />
        </div>
        <p className="text-xs md:text-sm text-muted-foreground">
          {LEVEL_DESCRIPTIONS[currentLevel]}
        </p>
      </CardContent>
    </Card>
  );
};
