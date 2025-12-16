import { Award, Target, CheckCircle, XCircle, HelpCircle } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { cn } from '@/lib/utils';
import type { LevelProgress } from '@/hooks/useQuestionProgress';
import type { CEFRLevel } from '@/lib/constants';

interface CurrentLevelDetailsCardProps {
  level: CEFRLevel;
  progress?: LevelProgress;
  hasCertificate?: boolean;
}

export const CurrentLevelDetailsCard = ({ 
  level, 
  progress,
  hasCertificate = false 
}: CurrentLevelDetailsCardProps) => {
  if (!progress) {
    return (
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-lg flex items-center gap-2">
            <Target className="w-5 h-5" />
            {level} Level Details
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">No progress yet. Start practicing!</p>
        </CardContent>
      </Card>
    );
  }

  const certProgress = (progress.correct_count / progress.certificate_target) * 100;
  const completionProgress = progress.completion_percent;

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-lg flex items-center gap-2">
          <Target className="w-5 h-5" />
          {level} Level Details
          {hasCertificate && (
            <span className="ml-auto flex items-center gap-1 text-sm font-normal text-green-500">
              <Award className="w-4 h-4" />
              Certified
            </span>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        {/* Stats Grid */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <div className="p-3 rounded-lg bg-muted/50 text-center">
            <HelpCircle className="w-5 h-5 mx-auto mb-1 text-primary" />
            <p className="text-xl font-bold">{progress.answered_questions}</p>
            <p className="text-xs text-muted-foreground">of {progress.total_questions}</p>
            <p className="text-[10px] text-muted-foreground uppercase">Answered</p>
          </div>
          <div className="p-3 rounded-lg bg-green-500/10 text-center">
            <CheckCircle className="w-5 h-5 mx-auto mb-1 text-green-500" />
            <p className="text-xl font-bold text-green-600">{progress.correct_count}</p>
            <p className="text-xs text-muted-foreground">{progress.accuracy}% acc</p>
            <p className="text-[10px] text-muted-foreground uppercase">Correct</p>
          </div>
          <div className="p-3 rounded-lg bg-red-500/10 text-center">
            <XCircle className="w-5 h-5 mx-auto mb-1 text-red-500" />
            <p className="text-xl font-bold text-red-600">{progress.wrong_count}</p>
            <p className="text-xs text-muted-foreground">to retry</p>
            <p className="text-[10px] text-muted-foreground uppercase">Wrong</p>
          </div>
          <div className="p-3 rounded-lg bg-muted/50 text-center">
            <Target className="w-5 h-5 mx-auto mb-1 text-muted-foreground" />
            <p className="text-xl font-bold">{progress.remaining_questions}</p>
            <p className="text-xs text-muted-foreground">questions</p>
            <p className="text-[10px] text-muted-foreground uppercase">Remaining</p>
          </div>
        </div>

        {/* Completion Progress */}
        <div>
          <div className="flex items-center justify-between text-sm mb-1">
            <span className="text-muted-foreground">Level Completion</span>
            <span className="font-medium">{completionProgress}%</span>
          </div>
          <Progress value={completionProgress} className="h-2" />
          <p className="text-xs text-muted-foreground mt-1">
            {progress.answered_questions} of {progress.total_questions} questions answered
          </p>
        </div>

        {/* Certificate Progress */}
        <div className={cn(
          "p-3 rounded-lg border",
          hasCertificate ? "bg-green-500/10 border-green-500/30" : "bg-muted/30"
        )}>
          <div className="flex items-center gap-2 mb-2">
            <Award className={cn("w-5 h-5", hasCertificate ? "text-green-500" : "text-muted-foreground")} />
            <span className="font-medium">{level} Certificate Progress</span>
          </div>
          
          {hasCertificate ? (
            <p className="text-sm text-green-600">
              ✅ Congratulations! You've earned your {level} certificate!
            </p>
          ) : (
            <>
              <div className="flex items-center justify-between text-sm mb-1">
                <span className="text-muted-foreground">
                  Correct answers needed (90%)
                </span>
                <span className="font-medium">
                  {progress.correct_count}/{progress.certificate_target}
                </span>
              </div>
              <Progress 
                value={Math.min(certProgress, 100)} 
                className="h-2"
              />
              <p className="text-xs text-muted-foreground mt-2">
                {progress.correct_needed_for_cert > 0 ? (
                  <>
                    You need <span className="font-medium text-foreground">{progress.correct_needed_for_cert} more correct answers</span> to earn the certificate
                    {progress.remaining_questions > 0 && (
                      <> ({progress.remaining_questions} questions remaining)</>
                    )}
                  </>
                ) : progress.remaining_questions > 0 ? (
                  <>Complete the remaining {progress.remaining_questions} questions to claim your certificate</>
                ) : (
                  <span className="text-green-600">Certificate requirements met! 🎉</span>
                )}
              </p>
            </>
          )}
        </div>
      </CardContent>
    </Card>
  );
};
