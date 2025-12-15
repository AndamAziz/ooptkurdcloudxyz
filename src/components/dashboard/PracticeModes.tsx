import { Link } from 'react-router-dom';
import { Play, Target, Timer } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { CEFR_LEVELS } from '@/lib/constants';

export const PracticeModes = () => {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Play className="w-5 h-5" />
          Start Practice
        </CardTitle>
        <CardDescription>
          Choose your practice mode and level
        </CardDescription>
      </CardHeader>
      <CardContent>
        <div className="grid gap-4 sm:grid-cols-2">
          <Link to="/practice?mode=10_questions">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary">
              <CardContent className="p-6 text-center">
                <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-4">
                  <Target className="w-6 h-6 text-primary" />
                </div>
                <h3 className="font-semibold mb-1">10 Questions</h3>
                <p className="text-sm text-muted-foreground">
                  Quick practice session
                </p>
              </CardContent>
            </Card>
          </Link>
          
          <Link to="/practice?mode=timed">
            <Card className="card-hover cursor-pointer border-2 hover:border-primary">
              <CardContent className="p-6 text-center">
                <div className="w-12 h-12 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-4">
                  <Timer className="w-6 h-6 text-warning" />
                </div>
                <h3 className="font-semibold mb-1">Timed Mode</h3>
                <p className="text-sm text-muted-foreground">
                  10 minutes challenge
                </p>
              </CardContent>
            </Card>
          </Link>
        </div>

        <div className="mt-6">
          <p className="text-sm font-medium mb-3">Select Level:</p>
          <div className="flex flex-wrap gap-2">
            {CEFR_LEVELS.map((level) => (
              <Link key={level} to={`/practice?level=${level}`}>
                <Button variant="outline" size="sm">
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
