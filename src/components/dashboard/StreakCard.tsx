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
    <Card className="overflow-hidden">
      <div className="bg-gradient-to-r from-orange-500 to-red-500 p-4 text-white">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Flame className="w-8 h-8" />
            <div>
              <p className="text-3xl font-bold">{streak}</p>
              <p className="text-sm opacity-90">Day Streak</p>
            </div>
          </div>
        </div>
      </div>
      <CardContent className="p-4">
        <p className="text-sm text-muted-foreground">{getStreakStatus()}</p>
      </CardContent>
    </Card>
  );
};
