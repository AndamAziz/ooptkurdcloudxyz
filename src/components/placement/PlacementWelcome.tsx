import { GraduationCap, Clock, Target, SkipForward } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

interface PlacementWelcomeProps {
  onStartTest: () => void;
  onSkip: () => void;
  isLoading?: boolean;
}

export const PlacementWelcome = ({ onStartTest, onSkip, isLoading }: PlacementWelcomeProps) => {
  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-4">
      <Card className="w-full max-w-lg">
        <CardHeader className="text-center space-y-4">
          <div className="mx-auto w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center">
            <GraduationCap className="w-8 h-8 text-primary" />
          </div>
          <CardTitle className="text-2xl md:text-3xl">Welcome to CEFR Practice!</CardTitle>
          <CardDescription className="text-base">
            Let's discover your English level together
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="bg-muted/50 rounded-lg p-4 space-y-4">
            <h3 className="font-semibold flex items-center gap-2">
              <Target className="w-5 h-5 text-primary" />
              Take the Placement Test
            </h3>
            <ul className="space-y-2 text-sm text-muted-foreground">
              <li className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 bg-primary rounded-full" />
                50 questions from all levels
              </li>
              <li className="flex items-center gap-2">
                <span className="w-1.5 h-1.5 bg-primary rounded-full" />
                Find your perfect starting point
              </li>
              <li className="flex items-center gap-2">
                <Clock className="w-4 h-4" />
                Takes about 15-20 minutes
              </li>
            </ul>
          </div>

          <div className="space-y-3">
            <Button 
              onClick={onStartTest} 
              className="w-full" 
              size="lg"
              disabled={isLoading}
            >
              {isLoading ? 'Loading...' : 'Start Test →'}
            </Button>

            <div className="relative">
              <div className="absolute inset-0 flex items-center">
                <span className="w-full border-t" />
              </div>
              <div className="relative flex justify-center text-xs uppercase">
                <span className="bg-card px-2 text-muted-foreground">OR</span>
              </div>
            </div>

            <Button 
              variant="ghost" 
              onClick={onSkip} 
              className="w-full text-muted-foreground"
              disabled={isLoading}
            >
              <SkipForward className="w-4 h-4 mr-2" />
              Skip and start from A1 →
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};
