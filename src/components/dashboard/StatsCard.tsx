import { TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';

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
      <CardContent className="p-4 md:p-6 pt-0 space-y-4 md:space-y-6">
        {/* Accuracy Gauge */}
        {totalAttempts > 0 && (
          <div className="flex justify-center pb-2">
            <SpeedometerGauge 
              percentage={accuracy} 
              size="sm"
              showLabel={false}
              showPercentage={true}
            />
          </div>
        )}

        {/* Stats list */}
        <div className="space-y-3">
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
        </div>
      </CardContent>
    </Card>
  );
};
