import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Award, ArrowRight, CheckCircle2, Lock, Target, Trophy, Crown, Medal } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import { useQuestionProgress, type LevelProgress } from '@/hooks/useQuestionProgress';
import type { CEFRLevel } from '@/lib/constants';
import { CEFR_LEVELS } from '@/lib/constants';

interface CertificateProgressCardProps {
  userId?: string;
}

const REQUIRED_ACCURACY = 90;

const getLevelTheme = (level: CEFRLevel) => {
  switch (level) {
    case 'A1':
      return { color: 'text-amber-700', bg: 'bg-amber-100', border: 'border-amber-300', icon: Medal, label: 'Beginner' };
    case 'A2':
      return { color: 'text-orange-700', bg: 'bg-orange-100', border: 'border-orange-300', icon: Medal, label: 'Elementary' };
    case 'B1':
      return { color: 'text-slate-600', bg: 'bg-slate-100', border: 'border-slate-300', icon: Trophy, label: 'Intermediate' };
    case 'B2':
      return { color: 'text-blue-600', bg: 'bg-blue-100', border: 'border-blue-300', icon: Trophy, label: 'Upper Intermediate' };
    case 'C1':
      return { color: 'text-yellow-600', bg: 'bg-yellow-100', border: 'border-yellow-300', icon: Award, label: 'Advanced' };
    case 'C2':
      return { color: 'text-purple-600', bg: 'bg-purple-100', border: 'border-purple-300', icon: Crown, label: 'Mastery' };
  }
};

interface LevelCertificateRowProps {
  level: CEFRLevel;
  progress?: LevelProgress;
  certificate?: Certificate;
  isLocked: boolean;
}

const LevelCertificateRow = ({ level, progress, certificate, isLocked }: LevelCertificateRowProps) => {
  const theme = getLevelTheme(level);
  const Icon = theme.icon;
  
  const isCompleted = progress?.remaining_questions === 0 && (progress?.total_questions || 0) > 0;
  const hasEarned = !!certificate;
  const currentAccuracy = progress?.accuracy || 0;
  const completionPercent = progress ? 
    Math.round((progress.answered_questions / (progress.total_questions || 1)) * 100) : 0;
  const needsMoreAccuracy = isCompleted && currentAccuracy < REQUIRED_ACCURACY;

  if (isLocked) {
    return (
      <div className="flex items-center gap-3 p-2.5 rounded-lg bg-muted/30 opacity-60">
        <div className={`w-8 h-8 rounded-full ${theme.bg} ${theme.border} border flex items-center justify-center`}>
          <Lock className="w-4 h-4 text-muted-foreground" />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <CEFRBadge level={level} size="sm" />
            <span className="text-xs text-muted-foreground">{theme.label}</span>
          </div>
          <p className="text-[10px] text-muted-foreground">Complete previous level first</p>
        </div>
      </div>
    );
  }

  if (hasEarned) {
    return (
      <div className="flex items-center gap-3 p-2.5 rounded-lg bg-green-50 dark:bg-green-950/30 border border-green-200 dark:border-green-800">
        <div className={`w-8 h-8 rounded-full ${theme.bg} ${theme.border} border flex items-center justify-center`}>
          <CheckCircle2 className="w-4 h-4 text-green-600" />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <CEFRBadge level={level} size="sm" />
            <span className="text-xs font-medium text-green-700 dark:text-green-400">Certificate Earned!</span>
          </div>
          <p className="text-[10px] text-green-600 dark:text-green-500">
            {certificate.total_correct}/{certificate.total_questions_answered} correct ({Math.round(certificate.score_percentage)}%)
          </p>
        </div>
        <Icon className={`w-5 h-5 ${theme.color}`} />
      </div>
    );
  }

  return (
    <div className={`p-2.5 rounded-lg border ${needsMoreAccuracy ? 'bg-amber-50 dark:bg-amber-950/30 border-amber-200 dark:border-amber-800' : 'bg-muted/30 border-border'}`}>
      <div className="flex items-center gap-3 mb-2">
        <div className={`w-8 h-8 rounded-full ${theme.bg} ${theme.border} border flex items-center justify-center`}>
          <Icon className={`w-4 h-4 ${theme.color}`} />
        </div>
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <CEFRBadge level={level} size="sm" />
            <span className="text-xs text-muted-foreground">{theme.label}</span>
          </div>
        </div>
        {needsMoreAccuracy && (
          <span className="text-[10px] font-medium text-amber-600 dark:text-amber-400">Need 90%</span>
        )}
      </div>
      
      <div className="space-y-1.5">
        <div className="flex justify-between text-[10px] text-muted-foreground">
          <span>{progress?.answered_questions || 0}/{progress?.total_questions || 0} answered</span>
          <span>{currentAccuracy.toFixed(1)}% accuracy</span>
        </div>
        <Progress value={completionPercent} className="h-1.5" />
        
        {needsMoreAccuracy ? (
          <p className="text-[10px] text-amber-600 dark:text-amber-400">
            Retry wrong questions to reach 90% accuracy
          </p>
        ) : (
          <p className="text-[10px] text-muted-foreground">
            {progress?.remaining_questions || 0} questions remaining
          </p>
        )}
      </div>
    </div>
  );
};

export const CertificateProgressCard = ({ userId }: CertificateProgressCardProps) => {
  const { certificates, fetchUserCertificates } = useCertificates(userId);
  const { progress, fetchProgress } = useQuestionProgress(userId);

  useEffect(() => {
    if (userId) {
      fetchUserCertificates();
      fetchProgress();
    }
  }, [userId, fetchUserCertificates, fetchProgress]);

  const earnedCertificates = certificates.filter(c => c.status === 'earned' && c.certificate_type === 'level_completion');
  
  // Determine which levels are locked (only unlock if previous level has certificate or it's A1)
  const isLevelLocked = (level: CEFRLevel, index: number): boolean => {
    if (index === 0) return false; // A1 is never locked
    const prevLevel = CEFR_LEVELS[index - 1];
    return !earnedCertificates.some(c => c.cefr_level === prevLevel);
  };

  const getCertificateForLevel = (level: CEFRLevel) => 
    earnedCertificates.find(c => c.cefr_level === level);
  
  const getProgressForLevel = (level: CEFRLevel) => 
    progress.find(p => p.cefr === level);

  return (
    <Card>
      <CardHeader className="p-3 sm:p-4 md:p-6 pb-2">
        <CardTitle className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-base sm:text-lg md:text-xl">
            <Trophy className="w-4 h-4 sm:w-5 sm:h-5 text-primary" />
            Certificate Progress
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
        <div className="space-y-2">
          {/* Summary */}
          <div className="flex items-center justify-between p-2.5 rounded-lg bg-primary/5 border border-primary/20 mb-3">
            <div className="flex items-center gap-2">
              <Award className="w-5 h-5 text-primary" />
              <div>
                <p className="text-sm font-semibold">{earnedCertificates.length}/6</p>
                <p className="text-[10px] text-muted-foreground">Certificates Earned</p>
              </div>
            </div>
            <div className="text-right">
              <p className="text-xs text-muted-foreground">Requirement</p>
              <p className="text-[10px] font-medium flex items-center gap-1">
                <Target className="w-3 h-3" />
                90%+ accuracy on all questions
              </p>
            </div>
          </div>

          {/* Level Progress */}
          {CEFR_LEVELS.map((level, index) => (
            <LevelCertificateRow
              key={level}
              level={level}
              progress={getProgressForLevel(level)}
              certificate={getCertificateForLevel(level)}
              isLocked={isLevelLocked(level, index)}
            />
          ))}
        </div>
      </CardContent>
    </Card>
  );
};
