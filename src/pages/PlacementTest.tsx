import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { usePlacementTest, type PlacementQuestion, type PlacementResult } from '@/hooks/usePlacementTest';
import { PlacementWelcome } from '@/components/placement/PlacementWelcome';
import { PlacementQuestion as QuestionComponent } from '@/components/placement/PlacementQuestion';
import { PlacementResults } from '@/components/placement/PlacementResults';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { useToast } from '@/hooks/use-toast';

type PlacementState = 'welcome' | 'testing' | 'results';

const PlacementTest = () => {
  const navigate = useNavigate();
  const { user, profile, refreshProfile } = useAuth();
  const { toast } = useToast();
  const {
    questions,
    loading,
    fetchPlacementQuestions,
    checkAnswer,
    recordAnswer,
    recordAttempt,
    calculateResults,
    completePlacementTest,
    skipPlacementTest,
    resetTest,
  } = usePlacementTest();

  const [state, setState] = useState<PlacementState>('welcome');
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswer, setSelectedAnswer] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<PlacementResult | null>(null);
  const [isSaving, setIsSaving] = useState(false);

  // Check if user already completed placement
  useEffect(() => {
    if (profile?.placement_completed) {
      navigate('/dashboard', { replace: true });
    }
  }, [profile, navigate]);

  const handleStartTest = useCallback(async () => {
    if (!user) return;

    const fetchedQuestions = await fetchPlacementQuestions(user.id);
    if (fetchedQuestions.length > 0) {
      setState('testing');
    } else {
      toast({
        title: 'Error',
        description: 'Failed to load placement test questions. Please try again.',
        variant: 'destructive',
      });
    }
  }, [user, fetchPlacementQuestions, toast]);

  const handleSkip = useCallback(async () => {
    if (!user) return;

    setIsSaving(true);
    const success = await skipPlacementTest(user.id);
    
    if (success) {
      await refreshProfile();
      toast({
        title: 'Welcome!',
        description: 'You\'ll start practicing from A1 level.',
      });
      navigate('/dashboard', { replace: true });
    } else {
      toast({
        title: 'Error',
        description: 'Failed to skip placement test. Please try again.',
        variant: 'destructive',
      });
    }
    setIsSaving(false);
  }, [user, skipPlacementTest, refreshProfile, navigate, toast]);

  const handleSelectAnswer = useCallback((answer: string) => {
    setSelectedAnswer(answer);
  }, []);

  const handleSubmitAnswer = useCallback(async () => {
    if (!user || !selectedAnswer || currentIndex >= questions.length) return;

    setIsSubmitting(true);
    const currentQuestion = questions[currentIndex];

    // Check the answer
    const result = await checkAnswer(currentQuestion.id, selectedAnswer);
    if (!result) {
      setIsSubmitting(false);
      return;
    }

    // Record the answer locally
    recordAnswer({
      questionId: currentQuestion.id,
      cefr: currentQuestion.cefr,
      chosenAnswer: selectedAnswer,
      isCorrect: result.isCorrect,
    });

    // Record attempt in database
    await recordAttempt(user.id, currentQuestion.id, selectedAnswer, result.isCorrect);

    // Move to next question or show results
    if (currentIndex < questions.length - 1) {
      setCurrentIndex(prev => prev + 1);
      setSelectedAnswer(null);
    } else {
      // Calculate and show results
      // Need to include this answer in calculation
      const finalResult = calculateResults();
      // Add current answer to calculation since recordAnswer is async
      const levelScores = { ...finalResult.levelScores };
      if (result.isCorrect) {
        levelScores[currentQuestion.cefr] = {
          ...levelScores[currentQuestion.cefr],
          correct: levelScores[currentQuestion.cefr].correct + 1,
          percentage: Math.round(
            ((levelScores[currentQuestion.cefr].correct + 1) / 
             levelScores[currentQuestion.cefr].total) * 100
          ),
        };
      }

      // Recalculate recommended level
      const levels = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'] as const;
      let recommendedLevel: typeof levels[number] = 'A1';
      for (const level of levels) {
        if (levelScores[level].percentage >= 70) {
          recommendedLevel = level;
        } else {
          break;
        }
      }

      const totalCorrect = Object.values(levelScores).reduce((a, b) => a + b.correct, 0);

      setResult({
        recommendedLevel,
        levelScores,
        totalCorrect,
        totalQuestions: 50,
        overallPercentage: Math.round((totalCorrect / 50) * 100),
      });
      setState('results');
    }

    setIsSubmitting(false);
  }, [user, selectedAnswer, currentIndex, questions, checkAnswer, recordAnswer, recordAttempt, calculateResults]);

  const handleContinue = useCallback(async () => {
    if (!user || !result) return;

    setIsSaving(true);
    const success = await completePlacementTest(user.id, result);

    if (success) {
      await refreshProfile();
      toast({
        title: 'Level Set!',
        description: `You've been placed at ${result.recommendedLevel} level. Good luck!`,
      });
      navigate('/dashboard', { replace: true });
    } else {
      toast({
        title: 'Error',
        description: 'Failed to save results. Please try again.',
        variant: 'destructive',
      });
    }
    setIsSaving(false);
  }, [user, result, completePlacementTest, refreshProfile, navigate, toast]);

  // Loading state
  if (!user || !profile) {
    return <LoadingSpinner />;
  }

  // Already completed - redirect handled by useEffect
  if (profile.placement_completed) {
    return <LoadingSpinner />;
  }

  // Render based on state
  if (state === 'welcome') {
    return (
      <PlacementWelcome
        onStartTest={handleStartTest}
        onSkip={handleSkip}
        isLoading={loading || isSaving}
      />
    );
  }

  if (state === 'testing') {
    if (loading || questions.length === 0) {
      return <LoadingSpinner />;
    }

    const currentQuestion = questions[currentIndex];
    return (
      <QuestionComponent
        question={currentQuestion}
        currentIndex={currentIndex}
        totalQuestions={questions.length}
        selectedAnswer={selectedAnswer}
        onSelectAnswer={handleSelectAnswer}
        onSubmit={handleSubmitAnswer}
        isSubmitting={isSubmitting}
      />
    );
  }

  if (state === 'results' && result) {
    return (
      <PlacementResults
        result={result}
        onContinue={handleContinue}
        isLoading={isSaving}
      />
    );
  }

  return <LoadingSpinner />;
};

export default PlacementTest;
