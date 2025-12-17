import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Trophy, Medal, Crown, Target, TrendingUp, ArrowLeft } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { supabase } from '@/integrations/supabase/client';
import { useAuth } from '@/contexts/AuthContext';
import { cn } from '@/lib/utils';
import type { CEFRLevel } from '@/lib/constants';

interface LeaderboardEntry {
  rank: number;
  user_id: string;
  username: string | null;
  display_name: string | null;
  xp: number;
  current_cefr: CEFRLevel;
  total_attempts: number;
  correct_attempts: number;
  accuracy: number;
}

const Leaderboard = () => {
  const { user } = useAuth();
  const [leaders, setLeaders] = useState<LeaderboardEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [userRank, setUserRank] = useState<LeaderboardEntry | null>(null);

  useEffect(() => {
    fetchLeaderboard();
  }, [user]);

  const fetchLeaderboard = async () => {
    const { data: profiles } = await supabase
      .from('leaderboard_view')
      .select('user_id, username, display_name, xp, current_cefr')
      .limit(50);

    if (!profiles) {
      setLoading(false);
      return;
    }

    const userIds = profiles.map(p => p.user_id);
    const { data: attempts } = await supabase
      .from('attempts')
      .select('user_id, is_correct')
      .in('user_id', userIds);

    const accuracyMap: Record<string, { total: number; correct: number }> = {};
    attempts?.forEach(a => {
      if (!accuracyMap[a.user_id]) {
        accuracyMap[a.user_id] = { total: 0, correct: 0 };
      }
      accuracyMap[a.user_id].total++;
      if (a.is_correct) {
        accuracyMap[a.user_id].correct++;
      }
    });

    const leaderboardData: LeaderboardEntry[] = profiles.map((p, index) => {
      const stats = accuracyMap[p.user_id] || { total: 0, correct: 0 };
      return {
        rank: index + 1,
        user_id: p.user_id,
        username: p.username,
        display_name: p.display_name,
        xp: p.xp || 0,
        current_cefr: (p.current_cefr || 'A1') as CEFRLevel,
        total_attempts: stats.total,
        correct_attempts: stats.correct,
        accuracy: stats.total > 0 ? Math.round((stats.correct / stats.total) * 100) : 0,
      };
    });

    setLeaders(leaderboardData);

    if (user) {
      const currentUserEntry = leaderboardData.find(l => l.user_id === user.id);
      if (currentUserEntry) {
        setUserRank(currentUserEntry);
      }
    }

    setLoading(false);
  };

  const getRankIcon = (rank: number) => {
    switch (rank) {
      case 1:
        return <Crown className="w-5 h-5 md:w-6 md:h-6 text-yellow-500" />;
      case 2:
        return <Medal className="w-5 h-5 md:w-6 md:h-6 text-gray-400" />;
      case 3:
        return <Medal className="w-5 h-5 md:w-6 md:h-6 text-amber-600" />;
      default:
        return <span className="w-5 h-5 md:w-6 md:h-6 flex items-center justify-center text-xs md:text-sm font-bold text-muted-foreground">#{rank}</span>;
    }
  };

  const getInitials = (entry: LeaderboardEntry) => {
    if (entry.display_name) {
      return entry.display_name.substring(0, 2).toUpperCase();
    }
    if (entry.username) {
      return entry.username.substring(0, 2).toUpperCase();
    }
    return 'U';
  };

  const getName = (entry: LeaderboardEntry) => {
    return entry.display_name || entry.username || 'Anonymous';
  };

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />
      
      <main className="container py-4 md:py-8 px-4">
        <div className="max-w-4xl mx-auto">
          {/* Back Button */}
          <Link to="/dashboard">
            <Button variant="ghost" size="sm" className="mb-4 -ml-2 h-10">
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Dashboard
            </Button>
          </Link>

          {/* Header */}
          <div className="text-center mb-6 md:mb-8">
            <div className="inline-flex items-center justify-center w-12 h-12 md:w-16 md:h-16 rounded-full bg-warning/10 mb-3 md:mb-4">
              <Trophy className="w-6 h-6 md:w-8 md:h-8 text-warning" />
            </div>
            <h1 className="font-serif text-xl md:text-3xl font-bold mb-2">Leaderboard</h1>
            <p className="text-sm md:text-base text-muted-foreground">Top learners ranked by XP and accuracy</p>
          </div>

          {/* Current User Rank Card */}
          {userRank && (
            <Card className="mb-4 md:mb-6 border-primary/20 bg-primary/5">
              <CardContent className="p-3 md:p-4">
                <div className="flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 md:gap-4">
                    <div className="w-10 h-10 md:w-12 md:h-12 rounded-full bg-primary flex items-center justify-center text-primary-foreground font-bold text-sm md:text-base">
                      #{userRank.rank}
                    </div>
                    <div>
                      <p className="font-semibold text-sm md:text-base">Your Ranking</p>
                      <p className="text-xs md:text-sm text-muted-foreground">
                        {userRank.xp.toLocaleString()} XP • {userRank.accuracy}%
                      </p>
                    </div>
                  </div>
                  <CEFRBadge level={userRank.current_cefr} />
                </div>
              </CardContent>
            </Card>
          )}

          {/* Top 3 Podium - Hidden on very small screens */}
          {leaders.length >= 3 && (
            <div className="hidden sm:grid grid-cols-3 gap-2 md:gap-4 mb-6 md:mb-8">
              {/* 2nd Place */}
              <div className="flex flex-col items-center pt-6 md:pt-8">
                <div className="relative">
                  <Avatar className="w-12 h-12 md:w-16 md:h-16 border-4 border-gray-300">
                    <AvatarFallback className="bg-gray-200 text-gray-700 text-sm md:text-lg">
                      {getInitials(leaders[1])}
                    </AvatarFallback>
                  </Avatar>
                  <div className="absolute -bottom-2 left-1/2 -translate-x-1/2">
                    <Medal className="w-5 h-5 md:w-6 md:h-6 text-gray-400" />
                  </div>
                </div>
                <p className="mt-3 md:mt-4 font-semibold text-xs md:text-sm truncate max-w-full text-center">{getName(leaders[1])}</p>
                <p className="text-xs md:text-sm text-muted-foreground">{leaders[1].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[1].current_cefr} size="sm" className="mt-1" />
              </div>

              {/* 1st Place */}
              <div className="flex flex-col items-center">
                <div className="relative">
                  <div className="absolute -top-5 md:-top-6 left-1/2 -translate-x-1/2">
                    <Crown className="w-6 h-6 md:w-8 md:h-8 text-yellow-500" />
                  </div>
                  <Avatar className="w-16 h-16 md:w-20 md:h-20 border-4 border-yellow-400">
                    <AvatarFallback className="bg-yellow-100 text-yellow-700 text-lg md:text-xl">
                      {getInitials(leaders[0])}
                    </AvatarFallback>
                  </Avatar>
                </div>
                <p className="mt-3 md:mt-4 font-bold text-sm md:text-base truncate max-w-full text-center">{getName(leaders[0])}</p>
                <p className="text-xs md:text-sm text-muted-foreground">{leaders[0].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[0].current_cefr} size="sm" className="mt-1" />
              </div>

              {/* 3rd Place */}
              <div className="flex flex-col items-center pt-10 md:pt-12">
                <div className="relative">
                  <Avatar className="w-10 h-10 md:w-14 md:h-14 border-4 border-amber-600">
                    <AvatarFallback className="bg-amber-100 text-amber-700 text-sm">
                      {getInitials(leaders[2])}
                    </AvatarFallback>
                  </Avatar>
                  <div className="absolute -bottom-2 left-1/2 -translate-x-1/2">
                    <Medal className="w-4 h-4 md:w-5 md:h-5 text-amber-600" />
                  </div>
                </div>
                <p className="mt-3 md:mt-4 font-semibold text-xs md:text-sm truncate max-w-full text-center">{getName(leaders[2])}</p>
                <p className="text-xs md:text-sm text-muted-foreground">{leaders[2].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[2].current_cefr} size="sm" className="mt-1" />
              </div>
            </div>
          )}

          {/* Full Leaderboard */}
          <Card className="w-full max-w-full">
            <CardHeader className="p-4 md:p-6">
              <CardTitle className="flex items-center gap-2 text-lg md:text-xl">
                <TrendingUp className="w-5 h-5" />
                All Rankings
              </CardTitle>
            </CardHeader>
            <CardContent className="p-4 md:p-6 pt-0">
              {loading ? (
                <div className="text-center py-6 md:py-8 text-muted-foreground text-sm md:text-base">
                  Loading leaderboard...
                </div>
              ) : leaders.length === 0 ? (
                <div className="text-center py-6 md:py-8 text-muted-foreground">
                  <Target className="w-10 h-10 md:w-12 md:h-12 mx-auto mb-3 opacity-50" />
                  <p className="text-sm md:text-base">No students yet</p>
                  <p className="text-xs md:text-sm">Be the first to practice!</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {leaders.map((entry) => (
                    <div
                      key={entry.user_id}
                      className={cn(
                        "flex items-center gap-2 md:gap-4 p-2 md:p-3 rounded-lg transition-colors",
                        entry.user_id === user?.id && "bg-primary/10 border border-primary/20",
                        entry.rank <= 3 && "bg-muted/50"
                      )}
                    >
                      <div className="w-6 md:w-8 flex justify-center flex-shrink-0">
                        {getRankIcon(entry.rank)}
                      </div>
                      
                      <Avatar className="w-8 h-8 md:w-10 md:h-10 flex-shrink-0">
                        <AvatarFallback className={cn(
                          "text-xs md:text-sm",
                          entry.rank === 1 && "bg-yellow-100 text-yellow-700",
                          entry.rank === 2 && "bg-gray-100 text-gray-700",
                          entry.rank === 3 && "bg-amber-100 text-amber-700",
                          entry.rank > 3 && "bg-muted"
                        )}>
                          {getInitials(entry)}
                        </AvatarFallback>
                      </Avatar>
                      
                      <div className="flex-1 min-w-0">
                        <p className="font-medium truncate text-sm md:text-base">
                          {getName(entry)}
                          {entry.user_id === user?.id && (
                            <span className="ml-1 md:ml-2 text-xs text-primary">(You)</span>
                          )}
                        </p>
                        <p className="text-xs md:text-sm text-muted-foreground hidden sm:block">
                          {entry.total_attempts} questions
                        </p>
                      </div>
                      
                      <div className="text-right flex-shrink-0">
                        <p className="font-bold text-sm md:text-base">{entry.xp.toLocaleString()}</p>
                        <p className="text-xs md:text-sm text-success">{entry.accuracy}%</p>
                      </div>
                      
                      <div className="hidden sm:block flex-shrink-0">
                        <CEFRBadge level={entry.current_cefr} size="sm" />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </CardContent>
          </Card>
        </div>
      </main>
    </div>
  );
};

export default Leaderboard;
