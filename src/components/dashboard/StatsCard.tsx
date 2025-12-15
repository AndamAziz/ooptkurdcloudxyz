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
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <TrendingUp className="w-5 h-5" />
          Your Stats
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Total XP</span>
          <span className="font-bold text-lg">{totalXP.toLocaleString()}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Questions Answered</span>
          <span className="font-bold text-lg">{totalAttempts}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Correct Answers</span>
          <span className="font-bold text-lg text-success">{totalCorrect}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-muted-foreground">Accuracy</span>
          <span className="font-bold text-lg">{accuracy}%</span>
        </div>
      </CardContent>
    </Card>
  );
};
