import { useEffect, useMemo, useState } from 'react';
import { Trophy } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
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
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';

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

  // Calculate the default level - show the level with highest accuracy
  const defaultLevel = useMemo((): CEFRLevel => {
    const levelsWithProgress = questionProgress.filter(p => p.answered_questions > 0);
    if (levelsWithProgress.length > 0) {
      // Find the level with highest accuracy
      const highestAccuracyLevel = levelsWithProgress.reduce((prev, curr) => 
        curr.accuracy > prev.accuracy ? curr : prev
      );
      return highestAccuracyLevel.cefr;
    }
    return (profile?.current_cefr as CEFRLevel) || 'A1';
  }, [profile?.current_cefr, questionProgress]);

  // Selected level state for the dropdown
  const [selectedLevel, setSelectedLevel] = useState<CEFRLevel>(defaultLevel);

  // Update selected level when default changes
  useEffect(() => {
    setSelectedLevel(defaultLevel);
  }, [defaultLevel]);

  // Get selected level stats from questionProgress
  const selectedLevelStats = useMemo(() => {
    return getLevelProgress(selectedLevel);
  }, [selectedLevel, getLevelProgress]);

  // Find the level with highest accuracy
  const highestAccuracyLevel = useMemo((): CEFRLevel | null => {
    const levelsWithProgress = questionProgress.filter(p => p.answered_questions > 0);
    if (levelsWithProgress.length === 0) return null;
    return levelsWithProgress.reduce((prev, curr) => 
      curr.accuracy > prev.accuracy ? curr : prev
    ).cefr;
  }, [questionProgress]);

  // Get earned certificate levels
  const earnedCertificates = useMemo(() => {
    return certificates
      .filter(c => c.status === 'earned')
      .map(c => c.cefr_level);
  }, [certificates]);

  const hasCertificateForSelectedLevel = earnedCertificates.includes(selectedLevel);

  const currentXP = profile?.xp || 0;
  const currentStreak = profile?.streak || 0;
  const displayName = profile?.display_name || profile?.username || 'Student';

  // Selected level accuracy
  const selectedLevelAccuracy = selectedLevelStats?.accuracy || 0;
  const selectedLevelAnswered = selectedLevelStats?.answered_questions || 0;
  const selectedLevelTotal = selectedLevelStats?.total_questions || 0;

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
                currentLevel={selectedLevel}
                currentLevelAccuracy={selectedLevelAccuracy}
                currentLevelAnswered={selectedLevelAnswered}
                currentLevelTotal={selectedLevelTotal}
                questionProgress={questionProgress}
                earnedCertificates={earnedCertificates}
                onLevelChange={setSelectedLevel}
                highestAccuracyLevel={highestAccuracyLevel}
              />
            </div>

            <PracticeModes 
              displayName={displayName} 
              currentLevel={selectedLevel} 
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
            
            {/* Current Level Stats Gauge Card with Selector */}
            <Card>
              <CardHeader className="pb-2">
                <div className="flex items-center justify-between gap-2">
                  <CardTitle className="text-lg">Level Accuracy</CardTitle>
                  <Select value={selectedLevel} onValueChange={(v) => setSelectedLevel(v as CEFRLevel)}>
                    <SelectTrigger className="w-20 h-8 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent className="bg-popover border shadow-lg z-50">
                      {CEFR_LEVELS.map((level) => {
                        const levelData = questionProgress.find(p => p.cefr === level);
                        const accuracy = levelData?.accuracy || 0;
                        const hasProgress = (levelData?.answered_questions || 0) > 0;
                        const colorClass = !hasProgress ? 'text-muted-foreground' 
                          : accuracy >= 80 ? 'text-green-600' 
                          : accuracy >= 60 ? 'text-yellow-600' 
                          : 'text-orange-600';
                        return (
                          <SelectItem key={level} value={level}>
                            <span className="flex items-center gap-1.5">
                              <span className={colorClass}>{level}</span>
                              {hasProgress && (
                                <span className={`text-[9px] ${colorClass}`}>({accuracy}%)</span>
                              )}
                              {highestAccuracyLevel === level && (
                                <span className="text-[9px] bg-green-500/20 text-green-600 px-1 py-0.5 rounded font-medium">Best</span>
                              )}
                            </span>
                          </SelectItem>
                        );
                      })}
                    </SelectContent>
                  </Select>
                </div>
              </CardHeader>
              <CardContent className="flex flex-col items-center gap-2">
                <SpeedometerGauge 
                  percentage={selectedLevelAccuracy} 
                  size="md" 
                  showLabel={true}
                  showPercentage={true}
                />
                <p className="text-sm text-muted-foreground">
                  {selectedLevelAnswered}/{selectedLevelTotal} questions answered
                </p>
              </CardContent>
            </Card>
            
            {/* Current Level Details */}
            <CurrentLevelDetailsCard 
              level={selectedLevel}
              progress={selectedLevelStats}
              hasCertificate={hasCertificateForSelectedLevel}
            />
            
            {/* Level Progress with per-level accuracy */}
            <LevelProgressCard 
              questionProgress={questionProgress} 
              currentLevel={selectedLevel} 
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
