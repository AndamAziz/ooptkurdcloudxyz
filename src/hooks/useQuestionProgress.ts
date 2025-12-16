import { useState, useCallback, useEffect } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { CEFRLevel } from '@/lib/constants';

export interface LevelProgress {
  cefr: CEFRLevel;
  total_questions: number;
  answered_questions: number;
  remaining_questions: number;
  correct_count: number;
  accuracy: number;
}

export const useQuestionProgress = (userId?: string) => {
  const [progress, setProgress] = useState<LevelProgress[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchProgress = useCallback(async () => {
    if (!userId) return;

    setLoading(true);
    setError(null);

    try {
      const { data, error: fetchError } = await supabase.rpc('get_question_progress', {
        p_user_id: userId,
      });

      if (fetchError) throw fetchError;
      setProgress((data || []) as LevelProgress[]);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch progress');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const resetLevelProgress = useCallback(async (cefr: CEFRLevel): Promise<boolean> => {
    if (!userId) return false;

    try {
      const { error } = await supabase.rpc('reset_level_progress', {
        p_user_id: userId,
        p_cefr: cefr,
      });

      if (error) throw error;
      
      // Refresh progress after reset
      await fetchProgress();
      return true;
    } catch (err) {
      console.error('Failed to reset progress:', err);
      return false;
    }
  }, [userId, fetchProgress]);

  const getLevelProgress = useCallback((cefr: CEFRLevel): LevelProgress | undefined => {
    return progress.find(p => p.cefr === cefr);
  }, [progress]);

  // Auto-fetch on mount
  useEffect(() => {
    if (userId) {
      fetchProgress();
    }
  }, [userId, fetchProgress]);

  return {
    progress,
    loading,
    error,
    fetchProgress,
    resetLevelProgress,
    getLevelProgress,
  };
};
