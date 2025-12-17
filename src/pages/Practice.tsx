import { useEffect, useState, useRef } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/Header';
import { PracticeSetup } from '@/components/practice/PracticeSetup';
import { PracticeResults } from '@/components/practice/PracticeResults';
import { PracticeHeader } from '@/components/practice/PracticeHeader';
import { QuestionCard } from '@/components/practice/QuestionCard';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { FloatingBackButton } from '@/components/shared/FloatingBackButton';
import { CertificateEarnedModal } from '@/components/certificates/CertificateEarnedModal';
import { useAuth } from '@/contexts/AuthContext';
import { useQuestions, type AnswerResult } from '@/hooks/useQuestions';
import { useProgress } from '@/hooks/useProgress';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import { useToast } from '@/hooks/use-toast';
import { supabase } from '@/integrations/supabase/client';
import { 
  PRACTICE_MODES, 
  TIMED_DURATION_SECONDS, 
  XP_REWARDS,
  QUESTIONS_PER_SESSION,
  CEFR_LEVELS,
  type CEFRLevel 
} from '@/lib/constants';

type PracticeState = 'setup' | 'practicing' | 'feedback' | 'results';

const Practice = () => {
  const [searchParams] = useSearchParams();
  const { user, profile, refreshProfile } = useAuth();
  const { toast } = useToast();
  
  const { 
    questions, 
    loading: questionsLoading, 
    fetchRandomQuestions,
    fetchPlacementQuestions,
    checkAnswer,
    checkRateLimit 
  } = useQuestions();
  
  const { 
    createSession, 
    endSession, 
    recordAttempt
  } = useProgress(user?.id);

  const { certificates, fetchUserCertificates } = useCertificates(user?.id);
  
  const { 
    getLevelProgress, 
    resetLevelProgress, 
    fetchProgress 
  } = useQuestionProgress(user?.id);

  // Store certificates count before session to detect new ones
  const certificatesBeforeSession = useRef<string[]>([]);

  // Snapshot XP/Streak before session (needed to compute +1/70XP streak reliably across devices)
  const profileBeforeSession = useRef<{ xp: number; streak: number; streakXpBank: number } | null>(null);
  const [streakEarnedThisSession, setStreakEarnedThisSession] = useState(0);
  const [streakXpBankAfterSession, setStreakXpBankAfterSession] = useState(0);

  const [state, setState] = useState<PracticeState>('setup');
  const initialMode = searchParams.get('mode') || PRACTICE_MODES.FIFTY_QUESTIONS;
  const [mode, setMode] = useState(initialMode);
  const isPlacementMode = mode === 'placement';
  const [selectedLevel, setSelectedLevel] = useState<CEFRLevel>(
    (searchParams.get('level') as CEFRLevel) || (profile?.current_cefr as CEFRLevel) || 'A1'
  );
  
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswer, setSelectedAnswer] = useState<string | null>(null);
  const [isCorrect, setIsCorrect] = useState<boolean | null>(null);
  const [answerResult, setAnswerResult] = useState<AnswerResult | null>(null);
  const [answers, setAnswers] = useState<{ questionId: number; answer: string; correct: boolean }[]>([]);
  
  const [timeLeft, setTimeLeft] = useState(TIMED_DURATION_SECONDS);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [questionStartTime, setQuestionStartTime] = useState<number>(0);

  // Certificate modal state
  const [earnedCertificate, setEarnedCertificate] = useState<Certificate | null>(null);
  const [showCertificateModal, setShowCertificateModal] = useState(false);
  const [isResettingProgress, setIsResettingProgress] = useState(false);

  // Timer for timed mode
  useEffect(() => {
    if (state === 'practicing' && mode === PRACTICE_MODES.TIMED && timeLeft > 0) {
      const timer = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            handleEndSession();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [state, mode, timeLeft]);

  const startSession = async () => {
    if (!user) return;

    // Re-check status from backend right before starting (admin might suspend while user is logged in)
    const { data: statusData, error: statusError } = await supabase
      .from('profiles')
      .select('account_status, suspend_reason, xp, streak, streak_xp_bank')
      .eq('user_id', user.id)
      .maybeSingle();

    if (statusError) {
      toast({
        title: 'Unable to start session',
        description: 'Please try again in a moment.',
        variant: 'destructive',
      });
      return;
    }

    if (statusData?.account_status === 'suspended') {
      toast({
        title: 'Account Suspended',
        description: statusData.suspend_reason
          ? `Reason: ${statusData.suspend_reason}`
          : 'Your account is suspended and you cannot practice right now.',
        variant: 'destructive',
      });
      return;
    }

    // Snapshot XP/Streak before this session (works even if AuthContext profile hasn't loaded yet)
    profileBeforeSession.current = {
      xp: statusData?.xp ?? 0,
      streak: statusData?.streak ?? 0,
      streakXpBank: (statusData as any)?.streak_xp_bank ?? 0,
    };
    setStreakEarnedThisSession(0);

    // Store current certificates before session starts
    await fetchUserCertificates();
    certificatesBeforeSession.current = certificates.map(c => c.id);

    // Check rate limit
    const withinLimit = await checkRateLimit(user.id);
    if (!withinLimit) {
      toast({
        title: 'Rate limit reached',
        description: 'Too many attempts. Please wait a few minutes.',
        variant: 'destructive',
      });
      return;
    }

    let fetchedQuestions;

    if (isPlacementMode) {
      // For placement test, fetch all available questions across all levels (currently 230)
      fetchedQuestions = await fetchPlacementQuestions(user.id, 50);
    } else {
      // Fetch fresh questions - returns the questions directly
      const limit = mode === PRACTICE_MODES.FIFTY_QUESTIONS ? QUESTIONS_PER_SESSION : 50;
      fetchedQuestions = await fetchRandomQuestions(user.id, selectedLevel, limit);
    }

    // Check if questions were fetched using the returned value
    if (fetchedQuestions.length === 0) {
      toast({
        title: 'No new questions',
        description: isPlacementMode
          ? 'No questions available for placement test.'
          : `All questions in ${selectedLevel} have been completed. Reset your progress to try again.`,
        variant: 'destructive',
      });
      await fetchProgress();
      return;
    }

    const newSessionId = await createSession(isPlacementMode ? 'A1' : selectedLevel, mode);
    if (!newSessionId) {
      toast({
        title: 'Unable to start session',
        description: 'Your account may be restricted. Please contact support if this keeps happening.',
        variant: 'destructive',
      });
      return;
    }

    setSessionId(newSessionId);
    setState('practicing');
    setQuestionStartTime(Date.now());
  };

  const handleAnswer = async (answer: string) => {
    if (selectedAnswer || !user) return;

    const question = questions[currentIndex];
    const timeSpent = Math.round((Date.now() - questionStartTime) / 1000);

    setSelectedAnswer(answer);

    // Validate answer server-side
    const result = await checkAnswer(question.id, answer);
    const correct = result?.is_correct ?? false;

    setIsCorrect(correct);
    setAnswerResult(result);

    // Record attempt
    const recorded = await recordAttempt(question.id, answer, correct, timeSpent);

    if (recorded) {
      setAnswers((prev) => [...prev, { questionId: question.id, answer, correct }]);
    }

    setState('feedback');
  };

  const nextQuestion = () => {
    const maxQuestions = mode === PRACTICE_MODES.FIFTY_QUESTIONS ? QUESTIONS_PER_SESSION : questions.length;
    
    if (currentIndex >= questions.length - 1 || currentIndex >= maxQuestions - 1) {
      handleEndSession();
    } else {
      setCurrentIndex(currentIndex + 1);
      setSelectedAnswer(null);
      setIsCorrect(null);
      setAnswerResult(null);
      setState('practicing');
      setQuestionStartTime(Date.now());
    }
  };

  const handleEndSession = async () => {
    const totalQuestions = answers.length;
    const correctCount = answers.filter(a => a.correct).length;
    const wrongCount = totalQuestions - correctCount;
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;
    // XP: +10 per correct, -5 per wrong, capped at 0
    const xpEarned = Math.max(0, (correctCount * XP_REWARDS.CORRECT) + (wrongCount * XP_REWARDS.WRONG));
    const summary = `Completed ${totalQuestions} questions at ${selectedLevel} with ${accuracy}% accuracy.`;

    if (sessionId) {
      await endSession(sessionId, totalQuestions, correctCount, xpEarned, summary);
    }

    const before = profileBeforeSession.current;

    // XP/Streak are awarded immediately by the backend on every answered question.
    // Here we only fetch the latest values so results/dashboard stay in sync across devices.
    if (user) {
      const { data: afterProfile } = await supabase
        .from('profiles')
        .select('xp, streak, streak_xp_bank')
        .eq('user_id', user.id)
        .maybeSingle();

      const streakGained =
        before && typeof afterProfile?.streak === 'number'
          ? afterProfile.streak - before.streak
          : 0;

      setStreakEarnedThisSession(streakGained > 0 ? streakGained : 0);
      setStreakXpBankAfterSession((afterProfile as any)?.streak_xp_bank ?? 0);

      await refreshProfile();
    }

    // Wait a moment for the database trigger to potentially create a certificate
    await new Promise(resolve => setTimeout(resolve, 500));
    
    // Fetch certificates again to check for new ones
    await fetchUserCertificates();
    
    // Check if a new certificate was awarded for the current level
    const newCert = certificates.find(c => 
      !certificatesBeforeSession.current.includes(c.id) && 
      c.cefr_level === selectedLevel &&
      c.status === 'earned'
    );

    if (newCert) {
      setEarnedCertificate(newCert);
      setShowCertificateModal(true);
      toast({
        title: '🎉 Certificate Earned!',
        description: `Congratulations! You earned your ${selectedLevel} certificate!`,
      });
    }

    setState('results');
  };

  const handlePracticeAgain = () => {
    setAnswers([]);
    setCurrentIndex(0);
    setSelectedAnswer(null);
    setIsCorrect(null);
    setAnswerResult(null);
    setTimeLeft(TIMED_DURATION_SECONDS);
    setSessionId(null);
    setStreakEarnedThisSession(0);
    setStreakXpBankAfterSession(0);
    profileBeforeSession.current = null;
    setState('setup');
    // Refresh progress after practice
    fetchProgress();
    fetchUserCertificates();
  };

  const handleResetProgress = async () => {
    setIsResettingProgress(true);
    const success = await resetLevelProgress(selectedLevel);
    setIsResettingProgress(false);
    if (success) {
      toast({
        title: 'Progress Reset',
        description: `Your ${selectedLevel} progress has been reset. You can now practice all questions again.`,
      });
    }
  };

  const levelProgress = getLevelProgress(selectedLevel);

  const studentName = profile?.display_name || profile?.username || 'Student';
  const currentQuestion = questions[currentIndex];
  const maxQuestions = mode === PRACTICE_MODES.FIFTY_QUESTIONS ? QUESTIONS_PER_SESSION : questions.length;
  const progress = ((currentIndex + 1) / maxQuestions) * 100;

  if (state === 'setup') {
    return (
      <div className="min-h-screen bg-background overflow-x-hidden">
        <Header />
        <main className="container max-w-2xl py-4 md:py-12 px-4">
          {/* Back Button */}
          <Link to="/dashboard">
            <Button variant="ghost" size="sm" className="mb-4 -ml-2 h-10">
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Dashboard
            </Button>
          </Link>

          <PracticeSetup
            selectedLevel={selectedLevel}
            mode={mode}
            loading={questionsLoading}
            levelProgress={levelProgress}
            onLevelChange={setSelectedLevel}
            onModeChange={setMode}
            onStart={startSession}
            onResetProgress={handleResetProgress}
            isResetting={isResettingProgress}
            isPlacementMode={isPlacementMode}
          />
        </main>

        <FloatingBackButton />
      </div>
    );
  }

  if (state === 'results') {
    const totalQuestions = answers.length;
    const correctCount = answers.filter(a => a.correct).length;
    const wrongCount = totalQuestions - correctCount;
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;
    // XP: +10 per correct, -5 per wrong, capped at 0
    const xpEarned = Math.max(0, (correctCount * XP_REWARDS.CORRECT) + (wrongCount * XP_REWARDS.WRONG));

    const streakEarned = streakEarnedThisSession;

    // Check if level is now complete (all questions answered)
    const currentLevelProgress = getLevelProgress(selectedLevel);
    const isLevelComplete = !isPlacementMode && currentLevelProgress?.remaining_questions === 0;

    // Calculate level breakdown for placement mode
    const levelBreakdown = isPlacementMode ? CEFR_LEVELS.map(level => {
      const levelQuestions = questions.filter(q => q.cefr === level);
      const levelAnswers = answers.filter((a, idx) => questions[idx]?.cefr === level);
      const correct = levelAnswers.filter(a => a.correct).length;
      const total = levelQuestions.length;
      return {
        level,
        correct,
        total,
        accuracy: total > 0 ? Math.round((correct / total) * 100) : 0,
      };
    }).filter(item => item.total > 0) : undefined;

    // Calculate recommended level for placement mode
    const recommendedLevel = isPlacementMode && levelBreakdown ? (() => {
      // Find highest level with >= 60% accuracy
      const passingLevels = levelBreakdown.filter(item => item.accuracy >= 60);
      if (passingLevels.length === 0) return 'A1' as CEFRLevel;
      
      // Get the highest passing level
      const levelOrder: CEFRLevel[] = ['C2', 'C1', 'B2', 'B1', 'A2', 'A1'];
      for (const level of levelOrder) {
        if (passingLevels.some(p => p.level === level)) {
          return level;
        }
      }
      return 'A1' as CEFRLevel;
    })() : undefined;

    return (
      <div className="min-h-screen bg-background overflow-x-hidden">
        <Header />
        <main className="container max-w-2xl py-6 md:py-12 px-4">
          <PracticeResults
            totalQuestions={totalQuestions}
            correctCount={correctCount}
            xpEarned={xpEarned}
            streakEarned={streakEarned}
            streakXpBank={streakXpBankAfterSession}
            onPracticeAgain={handlePracticeAgain}
            isPlacementMode={isPlacementMode}
            recommendedLevel={recommendedLevel}
            levelBreakdown={levelBreakdown}
            isLevelComplete={isLevelComplete}
            currentLevel={selectedLevel}
          />
        </main>

        {/* Certificate Earned Modal */}
        <CertificateEarnedModal
          certificate={earnedCertificate}
          studentName={studentName}
          open={showCertificateModal}
          onClose={() => setShowCertificateModal(false)}
        />
      </div>
    );
  }

  if (questionsLoading || !currentQuestion) {
    return <LoadingSpinner fullScreen />;
  }

  const isLastQuestion = currentIndex >= maxQuestions - 1 || currentIndex >= questions.length - 1;

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />
      <main className="container max-w-3xl py-4 md:py-8 px-4">
        <PracticeHeader
          level={selectedLevel}
          currentIndex={currentIndex}
          totalQuestions={maxQuestions}
          progress={progress}
          showTimer={mode === PRACTICE_MODES.TIMED}
          timeLeft={timeLeft}
          isPlacementMode={isPlacementMode}
          currentQuestionLevel={currentQuestion?.cefr}
        />

        <QuestionCard
          question={currentQuestion}
          selectedAnswer={selectedAnswer}
          answerResult={answerResult}
          isCorrect={isCorrect}
          onAnswer={handleAnswer}
          onNext={nextQuestion}
          isLastQuestion={isLastQuestion}
        />
      </main>
    </div>
  );
};

export default Practice;
