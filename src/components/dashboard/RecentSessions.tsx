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
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Clock className="w-5 h-5" />
          Recent Practice
        </CardTitle>
      </CardHeader>
      <CardContent>
        {sessions.length === 0 ? (
          <div className="text-center py-8 text-muted-foreground">
            <BookOpen className="w-12 h-12 mx-auto mb-3 opacity-50" />
            <p>No practice sessions yet</p>
            <p className="text-sm">Start your first practice above!</p>
          </div>
        ) : (
          <div className="space-y-3">
            {sessions.map((session) => (
              <div
                key={session.id}
                className="flex items-center justify-between p-4 rounded-lg bg-muted/50"
              >
                <div className="flex items-center gap-3">
                  <CEFRBadge level={session.cefr} size="sm" />
                  <div>
                    <p className="font-medium">
                      {session.correct_count}/{session.total_questions} correct
                    </p>
                    <p className="text-sm text-muted-foreground">
                      {format(new Date(session.started_at), 'MMM d, yyyy')}
                    </p>
                  </div>
                </div>
                <div className="text-right">
                  <p className="font-semibold text-success">+{session.xp_earned} XP</p>
                  <p className="text-sm text-muted-foreground">
                    {session.accuracy}% accuracy
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
