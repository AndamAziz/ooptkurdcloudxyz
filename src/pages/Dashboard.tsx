import { useEffect } from 'react';
import { Trophy } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { DonationButton } from '@/components/DonationButton';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';

import { PracticeModes } from '@/components/dashboard/PracticeModes';
import { RecentSessions } from '@/components/dashboard/RecentSessions';
import { StreakCard } from '@/components/dashboard/StreakCard';
import { LevelProgressCard } from '@/components/dashboard/LevelProgressCard';
import { CertificatesCard } from '@/components/dashboard/CertificatesCard';
import { MobileStatsHeader } from '@/components/dashboard/MobileStatsHeader';
import { useAuth } from '@/contexts/AuthContext';
import { useProgress } from '@/hooks/useProgress';
import type { CEFRLevel } from '@/lib/constants';

const Dashboard = () => {
  const { profile, user } = useAuth();
  const { sessions, stats, levelStats, fetchRecentSessions, fetchStats, fetchLevelStats } = useProgress(user?.id);

  useEffect(() => {
    if (user) {
      fetchRecentSessions();
      fetchStats();
      fetchLevelStats();
    }
  }, [user, fetchRecentSessions, fetchStats, fetchLevelStats]);

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
            {/* Mobile Stats Header - Combined Streak + Stats at top */}
            <div className="lg:hidden">
              <MobileStatsHeader
                streak={currentStreak}
                lastPracticeDate={profile?.last_practice_date || null}
                totalXP={currentXP}
                totalAttempts={stats.totalAttempts}
                totalCorrect={stats.totalCorrect}
                accuracy={stats.accuracy}
                levelStats={levelStats}
              />
            </div>

            <PracticeModes displayName={displayName} currentLevel={currentLevel} />
            <RecentSessions sessions={sessions} />
            
            {/* Certificates - Mobile only */}
            <div className="lg:hidden">
              <CertificatesCard userId={user?.id} />
            </div>
          </div>

          {/* Sidebar - Desktop only */}
          <div className="hidden lg:block space-y-6">
            <StreakCard 
              streak={currentStreak} 
              lastPracticeDate={profile?.last_practice_date || null} 
            />
            
            {/* Your Stats Gauge Card */}
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-lg">Your Stats</CardTitle>
              </CardHeader>
              <CardContent className="flex justify-center">
                <SpeedometerGauge 
                  percentage={stats.accuracy} 
                  size="md" 
                  showLabel={true}
                  showPercentage={true}
                />
              </CardContent>
            </Card>
            
            <LevelProgressCard levelStats={levelStats} />
            <CertificatesCard userId={user?.id} />

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
