import { useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { CEFRLevel } from '@/lib/constants';

export interface Question {
  id: number;
  cefr: CEFRLevel;
  difficulty: number | null;
  q_type: string | null;
  prompt: string | null;
  options_a: string | null;
  options_b: string | null;
  options_c: string | null;
  options_d: string | null;
  tags: string | null;
}

export interface AnswerResult {
  is_correct: boolean;
  correct_answer: string;
  explanation: string | null;
}

export const useQuestions = () => {
  const [questions, setQuestions] = useState<Question[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchRandomQuestions = useCallback(async (
    userId: string,
    level: CEFRLevel,
    limit: number = 50
  ): Promise<Question[]> => {
    setLoading(true);
    setError(null);

    try {
      // Use the get_random_questions function that excludes already answered questions
      const { data, error: fetchError } = await supabase.rpc('get_random_questions', {
        p_user_id: userId,
        p_cefr: level,
        p_limit: limit,
      });

      if (fetchError) {
        console.error('RPC failed, using fallback:', fetchError);
        
        // Fallback: Get already answered question IDs
        const { data: attemptedData } = await supabase
          .from('attempts')
          .select('question_id')
          .eq('user_id', userId);
        
        const attemptedIds = attemptedData?.map(a => a.question_id) || [];
        
        // Fetch questions excluding answered ones
        let query = supabase
          .from('questions_safe')
          .select('*')
          .eq('cefr', level);
        
        if (attemptedIds.length > 0) {
          query = query.not('id', 'in', `(${attemptedIds.join(',')})`);
        }
        
        const { data: fallbackData, error: fallbackError } = await query.limit(limit);

        if (fallbackError) throw fallbackError;
        
        // Shuffle the results
        const shuffled = [...(fallbackData || [])].sort(() => Math.random() - 0.5);
        setQuestions(shuffled as Question[]);
        return shuffled as Question[];
      } else {
        const fetchedQuestions = (data || []) as Question[];
        setQuestions(fetchedQuestions);
        return fetchedQuestions;
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch questions');
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  // Seeded shuffle function - produces consistent order based on seed
  const seededShuffle = (array: Question[], seed: string): Question[] => {
    const result = [...array];
    // Simple hash function to convert string to number
    let hash = 0;
    for (let i = 0; i < seed.length; i++) {
      const char = seed.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    
    // Seeded random number generator
    const seededRandom = () => {
      hash = (hash * 1103515245 + 12345) & 0x7fffffff;
      return hash / 0x7fffffff;
    };
    
    // Fisher-Yates shuffle with seeded random
    for (let i = result.length - 1; i > 0; i--) {
      const j = Math.floor(seededRandom() * (i + 1));
      [result[i], result[j]] = [result[j], result[i]];
    }
    return result;
  };

  // Fetch ALL questions from all levels for placement test
  const fetchPlacementQuestions = useCallback(async (
    userId: string,
    _questionsPerLevel: number = 50 // Ignored - we fetch all questions
  ): Promise<Question[]> => {
    setLoading(true);
    setError(null);

    try {
      // Fetch ALL questions from questions_safe view (no limit, no exclusion)
      const { data, error: fetchError } = await supabase
        .from('questions_safe')
        .select('*')
        .order('cefr')
        .order('id');

      if (fetchError) throw fetchError;

      const allQuestions = (data || []) as Question[];
      
      // Use seeded shuffle based on user_id for consistent order
      const shuffled = seededShuffle(allQuestions, userId);
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
  ): Promise<AnswerResult | null> => {
    try {
      const { data, error } = await supabase.rpc('check_answer', {
        p_question_id: questionId,
        p_chosen_answer: chosenAnswer,
      });

      if (error) throw error;
      return data?.[0] as AnswerResult | null;
    } catch (err) {
      console.error('Error checking answer:', err);
      return null;
    }
  }, []);

  const checkRateLimit = useCallback(async (
    userId: string,
    action: string = 'attempt'
  ): Promise<boolean> => {
    try {
      const { data, error } = await supabase.rpc('check_rate_limit', {
        p_user_id: userId,
        p_action: action,
      });

      if (error) {
        console.error('Rate limit check failed:', error);
        return true; // Allow if check fails
      }
      return data as boolean;
    } catch {
      return true; // Allow if check fails
    }
  }, []);

  return {
    questions,
    loading,
    error,
    fetchRandomQuestions,
    fetchPlacementQuestions,
    checkAnswer,
    checkRateLimit,
  };
};
