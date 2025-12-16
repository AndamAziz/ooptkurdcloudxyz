import { Clock, BookOpen } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { format } from 'date-fns';
import type { PracticeSession } from '@/hooks/useProgress';

interface RecentSessionsProps {
  sessions: PracticeSession[];
}

export const RecentSessions = ({ sessions }: RecentSessionsProps) => {
  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-lg md:text-xl">
          <Clock className="w-5 h-5" />
          Recent Practice
        </CardTitle>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-0">
        {sessions.length === 0 ? (
          <div className="text-center py-6 md:py-8 text-muted-foreground">
            <BookOpen className="w-10 h-10 md:w-12 md:h-12 mx-auto mb-3 opacity-50" />
            <p className="text-sm md:text-base">No practice sessions yet</p>
            <p className="text-xs md:text-sm">Start your first practice above!</p>
          </div>
        ) : (
          <div className="space-y-2 md:space-y-3">
            {sessions.map((session) => (
              <div
                key={session.id}
                className="flex items-center justify-between p-3 md:p-4 rounded-lg bg-muted/50 gap-3"
              >
                <div className="flex items-center gap-2 md:gap-3 min-w-0">
                  <CEFRBadge level={session.cefr} size="sm" />
                  <div className="min-w-0">
                    <p className="font-medium text-sm md:text-base truncate">
                      {session.correct_count}/{session.total_questions} correct
                    </p>
                    <p className="text-xs md:text-sm text-muted-foreground">
                      {format(new Date(session.started_at), 'MMM d, yyyy')}
                    </p>
                  </div>
                </div>
                <div className="text-right flex-shrink-0">
                  <p className="font-semibold text-success text-sm md:text-base">+{session.xp_earned} XP</p>
                  <p className="text-xs md:text-sm text-muted-foreground">
                    {session.accuracy}%
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
