import { TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';

interface StatsCardProps {
  totalXP: number;
  totalAttempts: number;
  totalCorrect: number;
  accuracy: number;
}

export const StatsCard = ({ totalXP, totalAttempts, totalCorrect, accuracy }: StatsCardProps) => {
  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-lg md:text-xl">
          <TrendingUp className="w-5 h-5" />
          Your Stats
        </CardTitle>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-0 space-y-3 md:space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm md:text-base">Total XP</span>
          <span className="font-bold text-base md:text-lg">{totalXP.toLocaleString()}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm md:text-base">Questions Answered</span>
          <span className="font-bold text-base md:text-lg">{totalAttempts}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm md:text-base">Correct Answers</span>
          <span className="font-bold text-base md:text-lg text-success">{totalCorrect}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground text-sm md:text-base">Accuracy</span>
          <span className="font-bold text-base md:text-lg">{accuracy}%</span>
        </div>
      </CardContent>
    </Card>
  );
};
