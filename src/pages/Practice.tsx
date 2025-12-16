import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { Header } from '@/components/Header';
import { PracticeSetup } from '@/components/practice/PracticeSetup';
import { PracticeResults } from '@/components/practice/PracticeResults';
import { PracticeHeader } from '@/components/practice/PracticeHeader';
import { QuestionCard } from '@/components/practice/QuestionCard';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { CertificateEarnedModal } from '@/components/certificates/CertificateEarnedModal';
import { useAuth } from '@/contexts/AuthContext';
import { useQuestions, type AnswerResult } from '@/hooks/useQuestions';
import { useProgress } from '@/hooks/useProgress';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import { useCertificateAward } from '@/hooks/useCertificateAward';
import { useToast } from '@/hooks/use-toast';
import type { Certificate } from '@/hooks/useCertificates';
import { 
  PRACTICE_MODES, 
  TIMED_DURATION_SECONDS, 
  XP_REWARDS,
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
    checkAnswer,
    checkRateLimit 
  } = useQuestions();
  
  const { 
    createSession, 
    endSession, 
    recordAttempt, 
    updateUserXP 
  } = useProgress(user?.id);

  const { checkAndAwardCertificate } = useCertificateAward(user?.id);
  
  const { 
    getLevelProgress, 
    resetLevelProgress, 
    fetchProgress 
  } = useQuestionProgress(user?.id);

  const [state, setState] = useState<PracticeState>('setup');
  const [mode, setMode] = useState(searchParams.get('mode') || PRACTICE_MODES.TEN_QUESTIONS);
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

    // Check if there are remaining questions
    const currentLevelProgress = getLevelProgress(selectedLevel);
    if (currentLevelProgress && currentLevelProgress.remaining_questions === 0) {
      toast({
        title: 'No questions available',
        description: `You've completed all questions in ${selectedLevel}. Reset your progress or try another level.`,
        variant: 'destructive',
      });
      return;
    }

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

    const limit = mode === PRACTICE_MODES.TEN_QUESTIONS ? 10 : 50;
    await fetchRandomQuestions(user.id, selectedLevel, limit);
    
    // Check if questions were fetched
    if (questions.length === 0) {
      toast({
        title: 'No new questions',
        description: `All questions in ${selectedLevel} have been completed. Reset your progress to try again.`,
        variant: 'destructive',
      });
      await fetchProgress();
      return;
    }
    
    const newSessionId = await createSession(selectedLevel, mode);
    if (newSessionId) {
      setSessionId(newSessionId);
    }
    
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
    setAnswers([...answers, { questionId: question.id, answer, correct }]);

    // Record attempt
    await recordAttempt(question.id, answer, correct, timeSpent);

    setState('feedback');
  };

  const nextQuestion = () => {
    const maxQuestions = mode === PRACTICE_MODES.TEN_QUESTIONS ? 10 : questions.length;
    
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
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;
    const xpEarned = (correctCount * XP_REWARDS.CORRECT) + ((totalQuestions - correctCount) * XP_REWARDS.INCORRECT);
    const summary = `Completed ${totalQuestions} questions at ${selectedLevel} with ${accuracy}% accuracy.`;

    if (sessionId) {
      await endSession(sessionId, totalQuestions, correctCount, xpEarned, summary);
    }

    const oldXP = profile?.xp || 0;
    const newXP = oldXP + xpEarned;

    if (profile) {
      await updateUserXP(profile.xp, xpEarned);
      await refreshProfile();
    }

    // Check if user earned a certificate
    const awardResult = await checkAndAwardCertificate(
      oldXP,
      newXP,
      accuracy,
      totalQuestions,
      correctCount
    );

    if (awardResult.awarded && awardResult.certificate) {
      const now = new Date().toISOString();
      // Cast the certificate to the full Certificate type
      const fullCertificate: Certificate = {
        id: awardResult.certificate.id,
        user_id: user?.id || '',
        certificate_type: awardResult.certificate.certificate_type as 'level_completion' | 'course_completion' | 'excellence',
        cefr_level: awardResult.certificate.cefr_level,
        issued_at: now,
        certificate_number: awardResult.certificate.certificate_number,
        score_percentage: awardResult.certificate.score_percentage,
        total_questions_answered: totalQuestions,
        total_correct: correctCount,
        study_hours: 0,
        status: 'earned',
        pdf_url: null,
        verified_by_admin: false,
        admin_notes: null,
        created_at: now,
        updated_at: now,
      };
      setEarnedCertificate(fullCertificate);
      setShowCertificateModal(true);
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
    setState('setup');
    // Refresh progress after practice
    fetchProgress();
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
  const maxQuestions = mode === PRACTICE_MODES.TEN_QUESTIONS ? 10 : questions.length;
  const progress = ((currentIndex + 1) / maxQuestions) * 100;

  if (state === 'setup') {
    return (
      <div className="min-h-screen bg-background overflow-x-hidden">
        <Header />
        <main className="container max-w-2xl py-6 md:py-12 px-4">
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
          />
        </main>
      </div>
    );
  }

  if (state === 'results') {
    const totalQuestions = answers.length;
    const correctCount = answers.filter(a => a.correct).length;
    const xpEarned = (correctCount * XP_REWARDS.CORRECT) + ((totalQuestions - correctCount) * XP_REWARDS.INCORRECT);

    return (
      <div className="min-h-screen bg-background overflow-x-hidden">
        <Header />
        <main className="container max-w-2xl py-6 md:py-12 px-4">
          <PracticeResults
            totalQuestions={totalQuestions}
            correctCount={correctCount}
            xpEarned={xpEarned}
            onPracticeAgain={handlePracticeAgain}
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
