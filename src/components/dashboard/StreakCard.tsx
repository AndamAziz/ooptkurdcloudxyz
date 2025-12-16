import { Flame } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { isToday, isYesterday, parseISO } from 'date-fns';

interface StreakCardProps {
  streak: number;
  lastPracticeDate: string | null;
}

export const StreakCard = ({ streak, lastPracticeDate }: StreakCardProps) => {
  const getStreakStatus = () => {
    if (!lastPracticeDate) return 'Start your streak today!';
    const date = parseISO(lastPracticeDate);
    if (isToday(date)) return 'Keep it going!';
    if (isYesterday(date)) return 'Practice today to keep your streak!';
    return 'Start a new streak today!';
  };

  return (
    <Card className="overflow-hidden w-full max-w-full">
      <div className="bg-gradient-to-r from-orange-500 to-red-500 p-3 md:p-4 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 md:gap-3">
            <Flame className="w-6 h-6 md:w-8 md:h-8" />
            <div>
              <p className="text-2xl md:text-3xl font-bold">{streak}</p>
              <p className="text-xs md:text-sm opacity-90">Day Streak</p>
            </div>
          </div>
        </div>
      </div>
      <CardContent className="p-3 md:p-4">
        <p className="text-xs md:text-sm text-muted-foreground">{getStreakStatus()}</p>
      </CardContent>
    </Card>
  );
};
