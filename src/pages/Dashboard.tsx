import { useEffect } from 'react';
import { Trophy } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { DonationButton } from '@/components/DonationButton';
import { WelcomeCard } from '@/components/dashboard/WelcomeCard';
import { PracticeModes } from '@/components/dashboard/PracticeModes';
import { RecentSessions } from '@/components/dashboard/RecentSessions';
import { StreakCard } from '@/components/dashboard/StreakCard';
import { StatsCard } from '@/components/dashboard/StatsCard';
import { useAuth } from '@/contexts/AuthContext';
import { useProgress } from '@/hooks/useProgress';
import type { CEFRLevel } from '@/lib/constants';

const Dashboard = () => {
  const { profile, user } = useAuth();
  const { sessions, stats, fetchRecentSessions, fetchStats } = useProgress(user?.id);

  useEffect(() => {
    if (user) {
      fetchRecentSessions();
      fetchStats();
    }
  }, [user, fetchRecentSessions, fetchStats]);

  const currentLevel = (profile?.current_cefr || 'A1') as CEFRLevel;
  const currentXP = profile?.xp || 0;
  const currentStreak = profile?.streak || 0;
  const displayName = profile?.display_name || profile?.username || 'Student';

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />
      
      <main className="container py-4 md:py-8">
        <div className="grid gap-4 md:gap-6 lg:grid-cols-3">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-4 md:space-y-6">
            <WelcomeCard
              displayName={displayName}
              currentLevel={currentLevel}
              currentXP={currentXP}
            />
            
            {/* Streak Card - Mobile only (shows above practice modes) */}
            <div className="lg:hidden">
              <StreakCard 
                streak={currentStreak} 
                lastPracticeDate={profile?.last_practice_date || null} 
              />
            </div>
            
            <PracticeModes />
            <RecentSessions sessions={sessions} />
            
            {/* Stats Card - Mobile only */}
            <div className="lg:hidden">
              <StatsCard
                totalXP={currentXP}
                totalAttempts={stats.totalAttempts}
                totalCorrect={stats.totalCorrect}
                accuracy={stats.accuracy}
              />
            </div>
          </div>

          {/* Sidebar - Desktop only */}
          <div className="hidden lg:block space-y-6">
            <StreakCard 
              streak={currentStreak} 
              lastPracticeDate={profile?.last_practice_date || null} 
            />
            <StatsCard
              totalXP={currentXP}
              totalAttempts={stats.totalAttempts}
              totalCorrect={stats.totalCorrect}
              accuracy={stats.accuracy}
            />

            {/* Donation Card */}
            <Card className="border-2 border-dashed">
              <CardContent className="p-4 md:p-6 text-center">
                <Trophy className="w-8 h-8 md:w-10 md:h-10 mx-auto mb-3 text-warning" />
                <h3 className="font-semibold mb-2 text-sm md:text-base">Support Our Mission</h3>
                <p className="text-xs md:text-sm text-muted-foreground mb-4">
                  Help us keep this resource free for all learners
                </p>
                <DonationButton size="default" className="w-full" />
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
