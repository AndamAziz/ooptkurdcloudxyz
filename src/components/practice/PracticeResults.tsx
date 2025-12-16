import { useNavigate } from 'react-router-dom';
import { CheckCircle2, Home, RotateCcw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';

interface PracticeResultsProps {
  totalQuestions: number;
  correctCount: number;
  xpEarned: number;
  onPracticeAgain: () => void;
}

export const PracticeResults = ({
  totalQuestions,
  correctCount,
  xpEarned,
  onPracticeAgain,
}: PracticeResultsProps) => {
  const navigate = useNavigate();
  const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;

  return (
    <Card className="animate-scale-in w-full max-w-full">
      <CardHeader className="text-center pb-2 p-4 md:p-6">
        <div className="w-16 h-16 md:w-20 md:h-20 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-3 md:mb-4">
          <CheckCircle2 className="w-8 h-8 md:w-10 md:h-10 text-success" />
        </div>
        <CardTitle className="font-serif text-xl md:text-2xl">Session Complete!</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 md:space-y-6 p-4 md:p-6 pt-0">
        <div className="grid grid-cols-2 gap-3 md:gap-4 text-center">
          <div className="p-3 md:p-4 rounded-lg bg-muted">
            <p className="text-2xl md:text-3xl font-bold">{correctCount}/{totalQuestions}</p>
            <p className="text-xs md:text-sm text-muted-foreground">Correct</p>
          </div>
          <div className="p-3 md:p-4 rounded-lg bg-muted">
            <p className="text-2xl md:text-3xl font-bold">{accuracy}%</p>
            <p className="text-xs md:text-sm text-muted-foreground">Accuracy</p>
          </div>
        </div>

        <div className="p-3 md:p-4 rounded-lg bg-success/10 text-center">
          <p className="text-xl md:text-2xl font-bold text-success">+{xpEarned} XP</p>
          <p className="text-xs md:text-sm text-muted-foreground">Experience earned</p>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 md:gap-4">
          <Button variant="outline" className="flex-1 min-h-[48px]" onClick={() => navigate('/dashboard')}>
            <Home className="w-4 h-4 mr-2" />
            Dashboard
          </Button>
          <Button className="flex-1 min-h-[48px]" onClick={onPracticeAgain}>
            <RotateCcw className="w-4 h-4 mr-2" />
            Practice Again
          </Button>
        </div>
      </CardContent>
    </Card>
  );
};
