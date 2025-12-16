import { useEffect, useMemo } from 'react';
import { Trophy } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { DonationButton } from '@/components/DonationButton';
import { SpeedometerGauge } from '@/components/SpeedometerGauge';

import { PracticeModes } from '@/components/dashboard/PracticeModes';
import { RecentSessions } from '@/components/dashboard/RecentSessions';
import { StreakCard } from '@/components/dashboard/StreakCard';
import { LevelProgressCard } from '@/components/dashboard/LevelProgressCard';
import { CertificateProgressCard } from '@/components/dashboard/CertificateProgressCard';
import { MobileStatsHeader } from '@/components/dashboard/MobileStatsHeader';
import { useAuth } from '@/contexts/AuthContext';
import { useProgress } from '@/hooks/useProgress';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';

// Threshold for considering a level "completed" (accuracy percentage)
const LEVEL_COMPLETION_THRESHOLD = 60;

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

  // Calculate the effective current level based on levelStats (same data as sidebar)
  const currentLevel = useMemo((): CEFRLevel => {
    if (!levelStats || levelStats.length === 0) {
      return (profile?.current_cefr as CEFRLevel) || 'A1';
    }

    // Use the same logic as LevelProgressCard for consistency
    const isLevelUnlocked = (index: number): boolean => {
      if (index === 0) return true;
      const prevLevel = levelStats[index - 1];
      return prevLevel.attempts >= 10 && prevLevel.accuracy >= LEVEL_COMPLETION_THRESHOLD;
    };

    const isLevelCompleted = (index: number): boolean => {
      if (!isLevelUnlocked(index)) return false;
      const level = levelStats[index];
      return level.attempts >= 10 && level.accuracy >= LEVEL_COMPLETION_THRESHOLD;
    };

    // Find the first unlocked level that isn't completed
    for (let i = 0; i < levelStats.length; i++) {
      if (!isLevelUnlocked(i)) {
        return levelStats[Math.max(0, i - 1)].level;
      }
      if (!isLevelCompleted(i)) {
        return levelStats[i].level;
      }
    }
    
    // All levels completed, return last level
    return levelStats[levelStats.length - 1].level;
  }, [levelStats, profile?.current_cefr]);

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
              levelProgress={questionProgress.map(p => ({
                level: p.cefr,
                remaining: p.remaining_questions,
                total: p.total_questions,
                accuracy: p.accuracy
              }))}
            />
            <RecentSessions sessions={sessions} />
            
            {/* Certificate Progress - Mobile only */}
            <div className="lg:hidden">
              <CertificateProgressCard userId={user?.id} />
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
            <CertificateProgressCard userId={user?.id} />

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
