import { useState } from 'react';
import { Clock, BookOpen, TrendingUp, TrendingDown, Minus, ChevronDown, ChevronUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { format, formatDistanceToNow } from 'date-fns';
import type { PracticeSession } from '@/hooks/useProgress';
import { cn } from '@/lib/utils';

interface RecentSessionsProps {
  sessions: PracticeSession[];
}

export const RecentSessions = ({ sessions }: RecentSessionsProps) => {
  const [expandedSession, setExpandedSession] = useState<string | null>(null);

  // Calculate trend compared to previous session
  const getTrend = (currentIndex: number) => {
    if (currentIndex >= sessions.length - 1) return 'neutral';
    const current = sessions[currentIndex].accuracy || 0;
    const previous = sessions[currentIndex + 1].accuracy || 0;
    if (current > previous) return 'up';
    if (current < previous) return 'down';
    return 'neutral';
  };

  const TrendIcon = ({ trend }: { trend: string }) => {
    if (trend === 'up') return <TrendingUp className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-green-500" />;
    if (trend === 'down') return <TrendingDown className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-red-500" />;
    return <Minus className="w-3 h-3 sm:w-3.5 sm:h-3.5 text-muted-foreground" />;
  };

  // Calculate estimated time spent (if not stored, estimate based on questions)
  const getTimeSpent = (session: PracticeSession) => {
    if (session.ended_at && session.started_at) {
      const start = new Date(session.started_at);
      const end = new Date(session.ended_at);
      const diffMs = end.getTime() - start.getTime();
      const diffMin = Math.round(diffMs / 60000);
      return diffMin > 0 ? `${diffMin} min` : '< 1 min';
    }
    // Estimate: ~30 seconds per question
    const estimatedMin = Math.round(((session.total_questions || 10) * 30) / 60);
    return `~${estimatedMin} min`;
  };

  return (
    <Card className="w-full max-w-full">
      <CardHeader className="p-3 sm:p-4 md:p-6">
        <CardTitle className="flex items-center gap-2 text-base sm:text-lg md:text-xl">
          <Clock className="w-4 h-4 sm:w-5 sm:h-5" />
          Recent Practice
        </CardTitle>
      </CardHeader>
      <CardContent className="p-3 sm:p-4 md:p-6 pt-0">
        {sessions.length === 0 ? (
          <div className="text-center py-4 sm:py-6 md:py-8 text-muted-foreground">
            <BookOpen className="w-8 h-8 sm:w-10 sm:h-10 md:w-12 md:h-12 mx-auto mb-2 sm:mb-3 opacity-50" />
            <p className="text-xs sm:text-sm md:text-base">No practice sessions yet</p>
            <p className="text-[10px] sm:text-xs md:text-sm">Start your first practice above!</p>
          </div>
        ) : (
          <div className="space-y-2">
            {sessions.map((session, index) => {
              const trend = getTrend(index);
              const isExpanded = expandedSession === session.id;
              const timeSpent = getTimeSpent(session);
              
              return (
                <div
                  key={session.id}
                  className={cn(
                    "rounded-lg bg-muted/50 transition-all active:scale-[0.99]",
                    isExpanded && "ring-1 ring-primary/20"
                  )}
                >
                  {/* Main row - always visible */}
                  <button
                    className="w-full flex items-center justify-between p-2.5 sm:p-3 md:p-4 gap-2 sm:gap-3 text-left"
                    onClick={() => setExpandedSession(isExpanded ? null : session.id)}
                  >
                    <div className="flex items-center gap-2 sm:gap-3 min-w-0 flex-1">
                      <CEFRBadge level={session.cefr} size="sm" />
                      <div className="min-w-0 flex-1">
                        <div className="flex items-center gap-1.5 sm:gap-2">
                          <p className="font-medium text-xs sm:text-sm md:text-base truncate">
                            {session.correct_count}/{session.total_questions} correct
                          </p>
                          <TrendIcon trend={trend} />
                        </div>
                        <div className="flex items-center gap-1.5 text-[10px] sm:text-xs text-muted-foreground">
                          <span>{format(new Date(session.started_at), 'MMM d, yyyy')}</span>
                          <span>•</span>
                          <span>{timeSpent}</span>
                        </div>
                      </div>
                    </div>
                    
                    <div className="flex items-center gap-2 sm:gap-3 flex-shrink-0">
                      <div className="text-right">
                        <p className="font-semibold text-green-600 dark:text-green-400 text-xs sm:text-sm md:text-base">
                          +{session.xp_earned} XP
                        </p>
                        <p className={cn(
                          "text-[10px] sm:text-xs font-medium",
                          (session.accuracy || 0) >= 80 ? "text-green-500" :
                          (session.accuracy || 0) >= 60 ? "text-yellow-500" :
                          (session.accuracy || 0) >= 40 ? "text-orange-500" : "text-red-500"
                        )}>
                          {session.accuracy}%
                        </p>
                      </div>
                      {isExpanded ? (
                        <ChevronUp className="w-4 h-4 text-muted-foreground" />
                      ) : (
                        <ChevronDown className="w-4 h-4 text-muted-foreground" />
                      )}
                    </div>
                  </button>
                  
                  {/* Expanded details */}
                  {isExpanded && (
                    <div className="px-2.5 sm:px-3 md:px-4 pb-2.5 sm:pb-3 md:pb-4 pt-0 border-t border-border/50">
                      <div className="grid grid-cols-3 gap-2 sm:gap-3 mt-2 sm:mt-3">
                        <div className="text-center p-2 rounded-lg bg-background/50">
                          <p className="text-[10px] sm:text-xs text-muted-foreground">Mode</p>
                          <p className="text-xs sm:text-sm font-medium capitalize">{session.mode || '10 Questions'}</p>
                        </div>
                        <div className="text-center p-2 rounded-lg bg-background/50">
                          <p className="text-[10px] sm:text-xs text-muted-foreground">Time</p>
                          <p className="text-xs sm:text-sm font-medium">{timeSpent}</p>
                        </div>
                        <div className="text-center p-2 rounded-lg bg-background/50">
                          <p className="text-[10px] sm:text-xs text-muted-foreground">Started</p>
                          <p className="text-xs sm:text-sm font-medium">
                            {formatDistanceToNow(new Date(session.started_at), { addSuffix: true })}
                          </p>
                        </div>
                      </div>
                      {session.summary_text && (
                        <p className="text-[10px] sm:text-xs text-muted-foreground mt-2 sm:mt-3 p-2 bg-background/50 rounded">
                          {session.summary_text}
                        </p>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
