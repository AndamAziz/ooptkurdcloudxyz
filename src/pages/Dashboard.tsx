import { useEffect } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';

import { PracticeModes } from '@/components/dashboard/PracticeModes';
import { RecentSessions } from '@/components/dashboard/RecentSessions';
import { StreakCard } from '@/components/dashboard/StreakCard';
import { LevelProgressCard } from '@/components/dashboard/LevelProgressCard';
import { CertificatesCard } from '@/components/dashboard/CertificatesCard';
import { MobileStatsHeader } from '@/components/dashboard/MobileStatsHeader';
import { useAuth } from '@/contexts/AuthContext';
import { useProgress } from '@/hooks/useProgress';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import type { CEFRLevel } from '@/lib/constants';

const Dashboard = () => {
  const { profile, user } = useAuth();
  const { sessions, stats, levelStats, fetchRecentSessions, fetchStats, fetchLevelStats } = useProgress(user?.id);
  const { progress: questionProgress, fetchProgress } = useQuestionProgress(user?.id);

  useEffect(() => {
    if (user) {
      fetchRecentSessions();
      fetchStats();
      fetchLevelStats();
      fetchProgress();
    }
  }, [user, fetchRecentSessions, fetchStats, fetchLevelStats, fetchProgress]);

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

            <PracticeModes 
              displayName={displayName} 
              currentLevel={currentLevel}
              currentXP={currentXP}
              levelProgress={questionProgress.map(p => ({
                level: p.cefr,
                remaining: p.remaining_questions,
                total: p.total_questions,
                accuracy: p.accuracy
              }))}
            />
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
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
