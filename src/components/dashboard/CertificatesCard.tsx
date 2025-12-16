import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Award, ArrowRight, CheckCircle2, Lock, Target, Sparkles } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useCertificates } from '@/hooks/useCertificates';
import { useAuth } from '@/contexts/AuthContext';
import { format } from 'date-fns';
import type { CEFRLevel } from '@/lib/constants';
import { CEFR_LEVELS, LEVEL_THRESHOLDS } from '@/lib/constants';

interface CertificatesCardProps {
  userId?: string;
}

export const CertificatesCard = ({ userId }: CertificatesCardProps) => {
  const { certificates, fetchUserCertificates } = useCertificates(userId);
  const { profile } = useAuth();

  useEffect(() => {
    if (userId) {
      fetchUserCertificates();
    }
  }, [userId, fetchUserCertificates]);

  const earnedCertificates = certificates.filter(c => c.status === 'earned');
  const latestCertificate = earnedCertificates[0];
  
  // Calculate next certificate progress
  const currentXP = profile?.xp || 0;
  const currentLevel = profile?.current_cefr || 'A1';
  
  // Find next level to unlock
  const currentLevelIndex = CEFR_LEVELS.indexOf(currentLevel as CEFRLevel);
  const nextLevel = CEFR_LEVELS[Math.min(currentLevelIndex, CEFR_LEVELS.length - 1)] as CEFRLevel;
  
  // Check if certificate already earned for current level
  const hasCertForCurrentLevel = earnedCertificates.some(c => c.cefr_level === currentLevel);
  const targetLevel = hasCertForCurrentLevel && currentLevelIndex < CEFR_LEVELS.length - 1 
    ? CEFR_LEVELS[currentLevelIndex + 1] as CEFRLevel
    : currentLevel as CEFRLevel;
  
  // Use existing LEVEL_THRESHOLDS for XP requirements
  const targetXP = LEVEL_THRESHOLDS[CEFR_LEVELS[Math.min(currentLevelIndex + 1, CEFR_LEVELS.length - 1)] as CEFRLevel];
  const previousLevelXP = currentLevelIndex > 0 ? LEVEL_THRESHOLDS[CEFR_LEVELS[currentLevelIndex - 1] as CEFRLevel] : 0;
  const xpProgress = Math.min(((currentXP - previousLevelXP) / (targetXP - previousLevelXP)) * 100, 100);
  const xpNeeded = Math.max(targetXP - currentXP, 0);

  return (
    <Card>
      <CardHeader className="p-3 sm:p-4 md:p-6 pb-2">
        <CardTitle className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-base sm:text-lg md:text-xl">
            <Award className="w-4 h-4 sm:w-5 sm:h-5" />
            Certificates
          </div>
          <Link to="/certificates">
            <Button variant="ghost" size="sm" className="text-primary h-8 px-2 sm:px-3">
              View All
              <ArrowRight className="w-3 h-3 sm:w-4 sm:h-4 ml-1" />
            </Button>
          </Link>
        </CardTitle>
      </CardHeader>
      <CardContent className="p-3 sm:p-4 md:p-6 pt-2">
        {earnedCertificates.length === 0 ? (
          // Motivating empty state
          <div className="space-y-3 sm:space-y-4">
            {/* Locked certificate preview */}
            <div className="relative overflow-hidden rounded-xl border-2 border-dashed border-muted-foreground/30 p-3 sm:p-4 bg-gradient-to-br from-muted/30 to-muted/10">
              {/* Blurred certificate preview */}
              <div className="absolute inset-0 flex items-center justify-center">
                <div className="w-20 h-28 sm:w-24 sm:h-32 bg-gradient-to-br from-primary/10 to-primary/5 rounded-lg blur-sm" />
              </div>
              
              <div className="relative text-center space-y-2">
                <div className="inline-flex items-center justify-center w-12 h-12 sm:w-14 sm:h-14 rounded-full bg-muted/80 backdrop-blur">
                  <Lock className="w-5 h-5 sm:w-6 sm:h-6 text-muted-foreground" />
                </div>
                <div>
                  <p className="font-semibold text-sm sm:text-base flex items-center justify-center gap-1.5">
                    <CEFRBadge level={targetLevel} size="sm" />
                    Certificate
                  </p>
                  <p className="text-[10px] sm:text-xs text-muted-foreground">Complete {targetLevel} to unlock!</p>
                </div>
              </div>
            </div>
            
            {/* Progress toward certificate */}
            <div className="space-y-2">
              <div className="flex items-center justify-between text-xs sm:text-sm">
                <span className="flex items-center gap-1.5 text-muted-foreground">
                  <Target className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                  Progress to {targetLevel}
                </span>
                <span className="font-medium">{Math.round(xpProgress)}%</span>
              </div>
              <Progress value={xpProgress} className="h-2 sm:h-2.5" />
              
              {/* Motivating message */}
              <div className="flex items-start gap-2 p-2 sm:p-2.5 rounded-lg bg-primary/5 border border-primary/20">
                <Sparkles className="w-4 h-4 text-primary flex-shrink-0 mt-0.5" />
                <p className="text-[10px] sm:text-xs text-muted-foreground leading-relaxed">
                  {xpNeeded > 0 ? (
                    <>You need <span className="font-semibold text-primary">{xpNeeded.toLocaleString()} more XP</span> to earn your {targetLevel} certificate!</>
                  ) : (
                    <>Complete the level assessment to earn your certificate!</>
                  )}
                </p>
              </div>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex items-center justify-between p-2.5 sm:p-3 rounded-lg bg-muted/50">
              <div className="flex items-center gap-2">
                <div className="w-9 h-9 sm:w-10 sm:h-10 rounded-full bg-primary/10 flex items-center justify-center">
                  <Award className="w-4 h-4 sm:w-5 sm:h-5 text-primary" />
                </div>
                <div>
                  <p className="font-semibold text-xl sm:text-2xl">{earnedCertificates.length}</p>
                  <p className="text-[10px] sm:text-xs text-muted-foreground">Total Earned</p>
                </div>
              </div>
              <div className="text-right">
                <p className="font-semibold text-green-500 flex items-center gap-1 text-sm sm:text-base">
                  <CheckCircle2 className="w-3.5 h-3.5 sm:w-4 sm:h-4" />
                  {certificates.filter(c => c.verified_by_admin).length}
                </p>
                <p className="text-[10px] sm:text-xs text-muted-foreground">Verified</p>
              </div>
            </div>

            {latestCertificate && (
              <div className="p-2.5 sm:p-3 rounded-lg border">
                <p className="text-[10px] sm:text-xs text-muted-foreground mb-2">Latest Certificate</p>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={latestCertificate.cefr_level as CEFRLevel} size="sm" />
                    <span className="text-xs sm:text-sm font-medium">
                      {latestCertificate.score_percentage}% Score
                    </span>
                  </div>
                  <span className="text-[10px] sm:text-xs text-muted-foreground">
                    {format(new Date(latestCertificate.issued_at), 'MMM d')}
                  </span>
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
