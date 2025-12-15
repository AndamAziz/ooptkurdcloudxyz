import { cn } from '@/lib/utils';
import { LEVEL_THRESHOLDS, getNextLevel, type CEFRLevel } from '@/lib/constants';

interface XPProgressBarProps {
  currentXP: number;
  currentLevel: CEFRLevel;
  className?: string;
  showLabels?: boolean;
}

export const XPProgressBar = ({ 
  currentXP, 
  currentLevel, 
  className,
  showLabels = true 
}: XPProgressBarProps) => {
  const nextLevel = getNextLevel(currentLevel);
  const currentThreshold = LEVEL_THRESHOLDS[currentLevel];
  const nextThreshold = nextLevel ? LEVEL_THRESHOLDS[nextLevel] : currentThreshold;
  
  const progressInLevel = currentXP - currentThreshold;
  const levelRange = nextThreshold - currentThreshold;
  const percentage = nextLevel 
    ? Math.min((progressInLevel / levelRange) * 100, 100)
    : 100;

  return (
    <div className={cn('space-y-1', className)}>
      {showLabels && (
        <div className="flex justify-between text-sm">
          <span className="text-muted-foreground">
            {currentXP.toLocaleString()} XP
          </span>
          {nextLevel && (
            <span className="text-muted-foreground">
              {nextThreshold.toLocaleString()} XP for {nextLevel}
            </span>
          )}
        </div>
      )}
      <div className="xp-progress-bar">
        <div 
          className="xp-progress-fill"
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
};
