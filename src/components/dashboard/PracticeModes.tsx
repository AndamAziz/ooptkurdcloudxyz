import { Link } from 'react-router-dom';
import { Play, Target, Timer } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { CEFR_LEVELS } from '@/lib/constants';

export const PracticeModes = () => {
  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-lg md:text-xl">
          <Play className="w-5 h-5" />
          Start Practice
        </CardTitle>
        <CardDescription className="text-sm">
          Choose your practice mode and level
        </CardDescription>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-0">
        <div className="grid gap-3 md:gap-4 grid-cols-1 sm:grid-cols-2">
          <Link to="/practice?mode=10_questions" className="block">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary h-full">
              <CardContent className="p-4 md:p-6 text-center">
                <div className="w-10 h-10 md:w-12 md:h-12 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-3 md:mb-4">
                  <Target className="w-5 h-5 md:w-6 md:h-6 text-primary" />
                </div>
                <h3 className="font-semibold mb-1 text-sm md:text-base">10 Questions</h3>
                <p className="text-xs md:text-sm text-muted-foreground">
                  Quick practice session
                </p>
              </CardContent>
            </Card>
          </Link>
          
          <Link to="/practice?mode=timed" className="block">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary h-full">
              <CardContent className="p-4 md:p-6 text-center">
                <div className="w-10 h-10 md:w-12 md:h-12 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-3 md:mb-4">
                  <Timer className="w-5 h-5 md:w-6 md:h-6 text-warning" />
                </div>
                <h3 className="font-semibold mb-1 text-sm md:text-base">Timed Mode</h3>
                <p className="text-xs md:text-sm text-muted-foreground">
                  10 minutes challenge
                </p>
              </CardContent>
            </Card>
          </Link>
        </div>

        <div className="mt-4 md:mt-6">
          <p className="text-xs md:text-sm font-medium mb-2 md:mb-3">Select Level:</p>
          <div className="flex flex-wrap gap-2">
            {CEFR_LEVELS.map((level) => (
              <Link key={level} to={`/practice?level=${level}`}>
                <Button variant="outline" size="sm" className="min-h-[44px] touch-target">
                  <CEFRBadge level={level} size="sm" />
                </Button>
              </Link>
            ))}
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
