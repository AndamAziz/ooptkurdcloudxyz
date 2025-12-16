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
import { CurrentLevelDetailsCard } from '@/components/dashboard/CurrentLevelDetailsCard';
import { MobileStatsHeader } from '@/components/dashboard/MobileStatsHeader';
import { useAuth } from '@/contexts/AuthContext';
import { useProgress } from '@/hooks/useProgress';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import { useCertificates } from '@/hooks/useCertificates';
import { type CEFRLevel } from '@/lib/constants';

const Dashboard = () => {
  const { profile, user } = useAuth();
  const { sessions, stats, fetchRecentSessions, fetchStats } = useProgress(user?.id);
  const { progress: questionProgress, fetchProgress, getLevelProgress } = useQuestionProgress(user?.id);
  const { certificates, fetchUserCertificates } = useCertificates(user?.id);

  useEffect(() => {
    if (user) {
      fetchRecentSessions();
      fetchStats();
      fetchProgress();
      fetchUserCertificates();
    }
  }, [user, fetchRecentSessions, fetchStats, fetchProgress, fetchUserCertificates]);

  // Calculate the effective current level based on profile
  const currentLevel = useMemo((): CEFRLevel => {
    return (profile?.current_cefr as CEFRLevel) || 'A1';
  }, [profile?.current_cefr]);

  // Get current level stats from questionProgress
  const currentLevelStats = useMemo(() => {
    return getLevelProgress(currentLevel);
  }, [currentLevel, getLevelProgress]);

  // Get earned certificate levels
  const earnedCertificates = useMemo(() => {
    return certificates
      .filter(c => c.status === 'earned')
      .map(c => c.cefr_level);
  }, [certificates]);

  const hasCertificateForCurrentLevel = earnedCertificates.includes(currentLevel);

  const currentXP = profile?.xp || 0;
  const currentStreak = profile?.streak || 0;
  const displayName = profile?.display_name || profile?.username || 'Student';

  // Current level accuracy (not overall)
  const currentLevelAccuracy = currentLevelStats?.accuracy || 0;
  const currentLevelAnswered = currentLevelStats?.answered_questions || 0;
  const currentLevelTotal = currentLevelStats?.total_questions || 0;

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
                overallAccuracy={stats.accuracy}
                currentLevel={currentLevel}
                currentLevelAccuracy={currentLevelAccuracy}
                currentLevelAnswered={currentLevelAnswered}
                currentLevelTotal={currentLevelTotal}
                questionProgress={questionProgress}
                earnedCertificates={earnedCertificates}
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
            
            {/* Current Level Stats Gauge Card */}
            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-lg">{currentLevel} Level Accuracy</CardTitle>
              </CardHeader>
              <CardContent className="flex flex-col items-center gap-2">
                <SpeedometerGauge 
                  percentage={currentLevelAccuracy} 
                  size="md" 
                  showLabel={true}
                  showPercentage={true}
                />
                <p className="text-sm text-muted-foreground">
                  {currentLevelAnswered}/{currentLevelTotal} questions answered
                </p>
              </CardContent>
            </Card>
            
            {/* Current Level Details */}
            <CurrentLevelDetailsCard 
              level={currentLevel}
              progress={currentLevelStats}
              hasCertificate={hasCertificateForCurrentLevel}
            />
            
            {/* Level Progress with per-level accuracy */}
            <LevelProgressCard 
              questionProgress={questionProgress} 
              currentLevel={currentLevel} 
              userId={user?.id} 
            />
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
