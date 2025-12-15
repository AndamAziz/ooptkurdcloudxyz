import { useEffect, useState } from 'react';
import { Trophy, Medal, Crown, Target, TrendingUp } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
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
    // Fetch top profiles by XP
    const { data: profiles } = await supabase
      .from('profiles')
      .select('user_id, username, display_name, xp, current_cefr')
      .order('xp', { ascending: false })
      .limit(50);

    if (!profiles) {
      setLoading(false);
      return;
    }

    // Fetch attempts for accuracy calculation
    const userIds = profiles.map(p => p.user_id);
    const { data: attempts } = await supabase
      .from('attempts')
      .select('user_id, is_correct')
      .in('user_id', userIds);

    // Calculate accuracy for each user
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

    // Find current user's rank
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
        return <Crown className="w-6 h-6 text-yellow-500" />;
      case 2:
        return <Medal className="w-6 h-6 text-gray-400" />;
      case 3:
        return <Medal className="w-6 h-6 text-amber-600" />;
      default:
        return <span className="w-6 h-6 flex items-center justify-center text-sm font-bold text-muted-foreground">#{rank}</span>;
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
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container py-8">
        <div className="max-w-4xl mx-auto">
          {/* Header */}
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-16 h-16 rounded-full bg-warning/10 mb-4">
              <Trophy className="w-8 h-8 text-warning" />
            </div>
            <h1 className="font-serif text-3xl font-bold mb-2">Leaderboard</h1>
            <p className="text-muted-foreground">Top learners ranked by XP and accuracy</p>
          </div>

          {/* Current User Rank Card */}
          {userRank && (
            <Card className="mb-6 border-primary/20 bg-primary/5">
              <CardContent className="p-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-4">
                    <div className="w-12 h-12 rounded-full bg-primary flex items-center justify-center text-primary-foreground font-bold">
                      #{userRank.rank}
                    </div>
                    <div>
                      <p className="font-semibold">Your Ranking</p>
                      <p className="text-sm text-muted-foreground">
                        {userRank.xp.toLocaleString()} XP • {userRank.accuracy}% accuracy
                      </p>
                    </div>
                  </div>
                  <CEFRBadge level={userRank.current_cefr} />
                </div>
              </CardContent>
            </Card>
          )}

          {/* Top 3 Podium */}
          {leaders.length >= 3 && (
            <div className="grid grid-cols-3 gap-4 mb-8">
              {/* 2nd Place */}
              <div className="flex flex-col items-center pt-8">
                <div className="relative">
                  <Avatar className="w-16 h-16 border-4 border-gray-300">
                    <AvatarFallback className="bg-gray-200 text-gray-700 text-lg">
                      {getInitials(leaders[1])}
                    </AvatarFallback>
                  </Avatar>
                  <div className="absolute -bottom-2 left-1/2 -translate-x-1/2">
                    <Medal className="w-6 h-6 text-gray-400" />
                  </div>
                </div>
                <p className="mt-4 font-semibold text-sm truncate max-w-full">{getName(leaders[1])}</p>
                <p className="text-sm text-muted-foreground">{leaders[1].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[1].current_cefr} size="sm" className="mt-1" />
              </div>

              {/* 1st Place */}
              <div className="flex flex-col items-center">
                <div className="relative">
                  <div className="absolute -top-6 left-1/2 -translate-x-1/2">
                    <Crown className="w-8 h-8 text-yellow-500" />
                  </div>
                  <Avatar className="w-20 h-20 border-4 border-yellow-400">
                    <AvatarFallback className="bg-yellow-100 text-yellow-700 text-xl">
                      {getInitials(leaders[0])}
                    </AvatarFallback>
                  </Avatar>
                </div>
                <p className="mt-4 font-bold truncate max-w-full">{getName(leaders[0])}</p>
                <p className="text-sm text-muted-foreground">{leaders[0].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[0].current_cefr} size="sm" className="mt-1" />
              </div>

              {/* 3rd Place */}
              <div className="flex flex-col items-center pt-12">
                <div className="relative">
                  <Avatar className="w-14 h-14 border-4 border-amber-600">
                    <AvatarFallback className="bg-amber-100 text-amber-700">
                      {getInitials(leaders[2])}
                    </AvatarFallback>
                  </Avatar>
                  <div className="absolute -bottom-2 left-1/2 -translate-x-1/2">
                    <Medal className="w-5 h-5 text-amber-600" />
                  </div>
                </div>
                <p className="mt-4 font-semibold text-sm truncate max-w-full">{getName(leaders[2])}</p>
                <p className="text-sm text-muted-foreground">{leaders[2].xp.toLocaleString()} XP</p>
                <CEFRBadge level={leaders[2].current_cefr} size="sm" className="mt-1" />
              </div>
            </div>
          )}

          {/* Full Leaderboard */}
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2">
                <TrendingUp className="w-5 h-5" />
                All Rankings
              </CardTitle>
            </CardHeader>
            <CardContent>
              {loading ? (
                <div className="text-center py-8 text-muted-foreground">
                  Loading leaderboard...
                </div>
              ) : leaders.length === 0 ? (
                <div className="text-center py-8 text-muted-foreground">
                  <Target className="w-12 h-12 mx-auto mb-3 opacity-50" />
                  <p>No students yet</p>
                  <p className="text-sm">Be the first to practice!</p>
                </div>
              ) : (
                <div className="space-y-2">
                  {leaders.map((entry) => (
                    <div
                      key={entry.user_id}
                      className={cn(
                        "flex items-center gap-4 p-3 rounded-lg transition-colors",
                        entry.user_id === user?.id && "bg-primary/10 border border-primary/20",
                        entry.rank <= 3 && "bg-muted/50"
                      )}
                    >
                      <div className="w-8 flex justify-center">
                        {getRankIcon(entry.rank)}
                      </div>
                      
                      <Avatar className="w-10 h-10">
                        <AvatarFallback className={cn(
                          entry.rank === 1 && "bg-yellow-100 text-yellow-700",
                          entry.rank === 2 && "bg-gray-100 text-gray-700",
                          entry.rank === 3 && "bg-amber-100 text-amber-700",
                          entry.rank > 3 && "bg-muted"
                        )}>
                          {getInitials(entry)}
                        </AvatarFallback>
                      </Avatar>
                      
                      <div className="flex-1 min-w-0">
                        <p className="font-medium truncate">
                          {getName(entry)}
                          {entry.user_id === user?.id && (
                            <span className="ml-2 text-xs text-primary">(You)</span>
                          )}
                        </p>
                        <p className="text-sm text-muted-foreground">
                          {entry.total_attempts} questions answered
                        </p>
                      </div>
                      
                      <div className="text-right">
                        <p className="font-bold">{entry.xp.toLocaleString()} XP</p>
                        <p className="text-sm text-success">{entry.accuracy}% accuracy</p>
                      </div>
                      
                      <CEFRBadge level={entry.current_cefr} size="sm" />
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
