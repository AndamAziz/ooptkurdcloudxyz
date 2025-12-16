import { TrendingUp, CheckCircle, Lock, Award } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { CEFRBadge } from '@/components/CEFRBadge';
import { Progress } from '@/components/ui/progress';
import type { LevelProgress } from '@/hooks/useQuestionProgress';
import { cn } from '@/lib/utils';
import { useCertificates } from '@/hooks/useCertificates';
import { useEffect } from 'react';
import type { CEFRLevel } from '@/lib/constants';

interface LevelProgressCardProps {
  questionProgress: LevelProgress[];
  currentLevel?: CEFRLevel;
  userId?: string;
}

export const LevelProgressCard = ({ questionProgress, currentLevel, userId }: LevelProgressCardProps) => {
  const { certificates, fetchUserCertificates } = useCertificates(userId);

  useEffect(() => {
    if (userId) {
      fetchUserCertificates();
    }
  }, [userId, fetchUserCertificates]);

  const hasCertificate = (level: CEFRLevel) => {
    return certificates.some(c => c.cefr_level === level && c.status === 'earned');
  };

  const isLevelLocked = (level: CEFRLevel, index: number) => {
    if (index === 0) return false; // A1 never locked
    const levels: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
    const prevLevel = levels[index - 1];
    return !hasCertificate(prevLevel);
  };

  const getProgressColor = (accuracy: number) => {
    if (accuracy >= 90) return 'bg-green-500';
    if (accuracy >= 70) return 'bg-yellow-500';
    if (accuracy >= 50) return 'bg-orange-500';
    return 'bg-red-500';
  };

  return (
    <Card className="w-full">
      <CardHeader className="p-4 pb-2">
        <CardTitle className="flex items-center gap-2 text-lg">
          <TrendingUp className="w-5 h-5" />
          Level Progress
        </CardTitle>
        <p className="text-xs text-muted-foreground">
          Complete all questions with 90%+ accuracy to earn certificates
        </p>
      </CardHeader>
      <CardContent className="p-4 pt-2">
        <div className="space-y-3">
          {questionProgress.map((level, index) => {
            const locked = isLevelLocked(level.cefr, index);
            const earned = hasCertificate(level.cefr);
            const isCurrentLevelItem = level.cefr === currentLevel;
            const hasProgress = level.answered_questions > 0;
            
            return (
              <div 
                key={level.cefr}
                className={cn(
                  "p-3 rounded-lg border transition-all",
                  locked && "opacity-50 bg-muted/30",
                  isCurrentLevelItem && !locked && "ring-2 ring-primary border-primary/40 bg-primary/5",
                  earned && !isCurrentLevelItem && "border-green-500/30 bg-green-500/5"
                )}
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={level.cefr} size="sm" />
                    {locked && <Lock className="w-3.5 h-3.5 text-muted-foreground" />}
                    {earned && <Award className="w-4 h-4 text-green-500" />}
                    {isCurrentLevelItem && !locked && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-primary/20 text-primary font-medium">
                        Current
                      </span>
                    )}
                  </div>
                  <div className="text-right text-sm">
                    {hasProgress && !locked ? (
                      <div className="flex flex-col items-end">
                        <span className={cn(
                          "font-bold",
                          level.accuracy >= 90 ? "text-green-500" : 
                          level.accuracy >= 70 ? "text-yellow-500" : "text-orange-500"
                        )}>
                          {level.accuracy}%
                        </span>
                        <span className="text-xs text-muted-foreground">
                          {level.answered_questions}/{level.total_questions}
                        </span>
                      </div>
                    ) : locked ? (
                      <span className="text-muted-foreground">Locked</span>
                    ) : (
                      <span className="text-muted-foreground">Not started</span>
                    )}
                  </div>
                </div>
                
                {!locked && (
                  <div className="space-y-1.5">
                    {/* Completion progress bar */}
                    <div className="flex items-center gap-2">
                      <Progress 
                        value={level.completion_percent} 
                        className="h-1.5 flex-1"
                      />
                      <span className="text-[10px] text-muted-foreground w-8 text-right">
                        {level.completion_percent}%
                      </span>
                    </div>
                    
                    {/* Stats row */}
                    <div className="flex justify-between text-[10px] text-muted-foreground">
                      <span>
                        ✅ {level.correct_count} correct • ❌ {level.wrong_count} wrong
                      </span>
                      <span>{level.remaining_questions} remaining</span>
                    </div>
                    
                    {/* Certificate progress */}
                    {hasProgress && (
                      <div className={cn(
                        "text-[10px] px-2 py-1 rounded mt-1",
                        earned ? "bg-green-500/10 text-green-600" :
                        level.can_earn_certificate ? "bg-green-500/10 text-green-600" :
                        "bg-muted/50 text-muted-foreground"
                      )}>
                        {earned ? (
                          <span className="flex items-center gap-1">
                            <Award className="w-3 h-3" />
                            Certificate Earned!
                          </span>
                        ) : level.can_earn_certificate ? (
                          <span>🎓 Ready to claim certificate!</span>
                        ) : (
                          <span>
                            🎯 Need {level.correct_needed_for_cert} more correct ({level.certificate_target} target)
                          </span>
                        )}
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </CardContent>
    </Card>
  );
};
