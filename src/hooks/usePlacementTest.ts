import { useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { CEFRLevel } from '@/lib/constants';

export interface PlacementQuestion {
  id: number;
  cefr: CEFRLevel;
  prompt: string | null;
  options_a: string | null;
  options_b: string | null;
  options_c: string | null;
  options_d: string | null;
  difficulty: number | null;
}

export interface PlacementAnswer {
  questionId: number;
  cefr: CEFRLevel;
  chosenAnswer: string;
  isCorrect: boolean;
}

export interface LevelScore {
  correct: number;
  total: number;
  percentage: number;
}

export interface PlacementResult {
  recommendedLevel: CEFRLevel;
  levelScores: Record<CEFRLevel, LevelScore>;
  totalCorrect: number;
  totalQuestions: number;
  overallPercentage: number;
}

const LEVEL_TOTALS: Record<CEFRLevel, number> = {
  A1: 8,
  A2: 9,
  B1: 10,
  B2: 10,
  C1: 8,
  C2: 5,
};

export const usePlacementTest = () => {
  const [questions, setQuestions] = useState<PlacementQuestion[]>([]);
  const [answers, setAnswers] = useState<PlacementAnswer[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchPlacementQuestions = useCallback(async (userId: string): Promise<PlacementQuestion[]> => {
    setLoading(true);
    setError(null);

    try {
      const { data, error: fetchError } = await supabase.rpc('get_placement_questions', {
        p_user_id: userId,
      });

      if (fetchError) throw fetchError;

      const fetchedQuestions = (data || []) as PlacementQuestion[];
      // Shuffle the questions so levels are mixed
      const shuffled = [...fetchedQuestions].sort(() => Math.random() - 0.5);
      setQuestions(shuffled);
      return shuffled;
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch placement questions');
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  const checkAnswer = useCallback(async (
    questionId: number,
    chosenAnswer: string
  ): Promise<{ isCorrect: boolean; correctAnswer: string } | null> => {
    try {
      const { data, error } = await supabase.rpc('check_answer', {
        p_question_id: questionId,
        p_chosen_answer: chosenAnswer,
      });

      if (error) throw error;
      const result = data?.[0];
      return result ? { isCorrect: result.is_correct, correctAnswer: result.correct_answer } : null;
    } catch (err) {
      console.error('Error checking answer:', err);
      return null;
    }
  }, []);

  const recordAnswer = useCallback((answer: PlacementAnswer) => {
    setAnswers(prev => [...prev, answer]);
  }, []);

  const calculateResults = useCallback((): PlacementResult => {
    const levelScores: Record<CEFRLevel, LevelScore> = {
      A1: { correct: 0, total: LEVEL_TOTALS.A1, percentage: 0 },
      A2: { correct: 0, total: LEVEL_TOTALS.A2, percentage: 0 },
      B1: { correct: 0, total: LEVEL_TOTALS.B1, percentage: 0 },
      B2: { correct: 0, total: LEVEL_TOTALS.B2, percentage: 0 },
      C1: { correct: 0, total: LEVEL_TOTALS.C1, percentage: 0 },
      C2: { correct: 0, total: LEVEL_TOTALS.C2, percentage: 0 },
    };

    // Count correct answers per level
    answers.forEach(answer => {
      if (answer.isCorrect) {
        levelScores[answer.cefr].correct++;
      }
    });

    // Calculate percentages
    let totalCorrect = 0;
    (Object.keys(levelScores) as CEFRLevel[]).forEach(level => {
      const score = levelScores[level];
      score.percentage = score.total > 0 ? Math.round((score.correct / score.total) * 100) : 0;
      totalCorrect += score.correct;
    });

    // Determine recommended level (highest level with 70%+ where all lower levels also passed)
    const levels: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
    let recommendedLevel: CEFRLevel = 'A1';

    for (const level of levels) {
      if (levelScores[level].percentage >= 70) {
        recommendedLevel = level;
      } else {
        break;
      }
    }

    const totalQuestions = 50;
    const overallPercentage = Math.round((totalCorrect / totalQuestions) * 100);

    return {
      recommendedLevel,
      levelScores,
      totalCorrect,
      totalQuestions,
      overallPercentage,
    };
  }, [answers]);

  const recordAttempt = useCallback(async (
    userId: string,
    questionId: number,
    chosenAnswer: string,
    isCorrect: boolean
  ): Promise<void> => {
    try {
      await supabase.from('attempts').insert({
        user_id: userId,
        question_id: questionId,
        chosen_answer: chosenAnswer,
        is_correct: isCorrect,
        time_spent_sec: 0,
      });
    } catch (err) {
      console.error('Error recording attempt:', err);
    }
  }, []);

  const completePlacementTest = useCallback(async (
    userId: string,
    result: PlacementResult
  ): Promise<boolean> => {
    try {
      const levelScoresJson = Object.entries(result.levelScores).reduce((acc, [level, score]) => {
        acc[level] = score.percentage;
        return acc;
      }, {} as Record<string, number>);

      const { error } = await supabase.rpc('complete_placement_test', {
        p_user_id: userId,
        p_correct_answers: result.totalCorrect,
        p_level_scores: levelScoresJson,
        p_recommended_level: result.recommendedLevel,
      });

      if (error) throw error;
      return true;
    } catch (err) {
      console.error('Error completing placement test:', err);
      return false;
    }
  }, []);

  const skipPlacementTest = useCallback(async (userId: string): Promise<boolean> => {
    try {
      const { error } = await supabase.rpc('skip_placement_test', {
        p_user_id: userId,
      });

      if (error) throw error;
      return true;
    } catch (err) {
      console.error('Error skipping placement test:', err);
      return false;
    }
  }, []);

  const resetTest = useCallback(() => {
    setQuestions([]);
    setAnswers([]);
    setError(null);
  }, []);

  return {
    questions,
    answers,
    loading,
    error,
    fetchPlacementQuestions,
    checkAnswer,
    recordAnswer,
    recordAttempt,
    calculateResults,
    completePlacementTest,
    skipPlacementTest,
    resetTest,
  };
};
