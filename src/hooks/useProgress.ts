import { useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';

import type { CEFRLevel } from '@/lib/constants';

export interface PracticeSession {
  id: string;
  cefr: CEFRLevel;
  mode: string;
  started_at: string;
  ended_at: string | null;
  total_questions: number;
  correct_count: number;
  accuracy: number;
  xp_earned: number;
  summary_text: string | null;
}

export interface UserStats {
  totalAttempts: number;
  totalCorrect: number;
  accuracy: number;
}

export interface LevelStats {
  level: CEFRLevel;
  attempts: number;
  correct: number;
  accuracy: number;
}

export const useProgress = (userId: string | undefined) => {
  const [sessions, setSessions] = useState<PracticeSession[]>([]);
  const [stats, setStats] = useState<UserStats>({ totalAttempts: 0, totalCorrect: 0, accuracy: 0 });
  const [levelStats, setLevelStats] = useState<LevelStats[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchRecentSessions = useCallback(async (limit: number = 5) => {
    if (!userId) return;
    
    setLoading(true);
    try {
      const { data } = await supabase
        .from('practice_sessions')
        .select('*')
        .eq('user_id', userId)
        .order('started_at', { ascending: false })
        .limit(limit);

      if (data) {
        setSessions(data as PracticeSession[]);
      }
    } catch (err) {
      console.error('Error fetching sessions:', err);
    } finally {
      setLoading(false);
    }
  }, [userId]);

  const fetchStats = useCallback(async () => {
    if (!userId) return;

    try {
      const { data } = await supabase
        .from('attempts')
        .select('is_correct')
        .eq('user_id', userId);

      if (data) {
        const total = data.length;
        const correct = data.filter(a => a.is_correct).length;
        setStats({
          totalAttempts: total,
          totalCorrect: correct,
          accuracy: total > 0 ? Math.round((correct / total) * 100) : 0,
        });
      }
    } catch (err) {
      console.error('Error fetching stats:', err);
    }
  }, [userId]);

  const fetchLevelStats = useCallback(async () => {
    if (!userId) return;

    try {
      const { data: sessionsData } = await supabase
        .from('practice_sessions')
        .select('cefr, total_questions, correct_count')
        .eq('user_id', userId)
        .not('ended_at', 'is', null);

      if (sessionsData) {
        const levels: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
        const statsPerLevel = levels.map(level => {
          const levelSessions = sessionsData.filter(s => s.cefr === level);
          const attempts = levelSessions.reduce((sum, s) => sum + (s.total_questions || 0), 0);
          const correct = levelSessions.reduce((sum, s) => sum + (s.correct_count || 0), 0);
          return {
            level,
            attempts,
            correct,
            accuracy: attempts > 0 ? Math.round((correct / attempts) * 100) : 0,
          };
        });
        setLevelStats(statsPerLevel);
        // Note: Don't overwrite stats here - fetchStats() uses attempts table for accurate overall stats
      }
    } catch (err) {
      console.error('Error fetching level stats:', err);
    }
  }, [userId]);

  const createSession = useCallback(async (
    cefr: CEFRLevel,
    mode: string
  ): Promise<string | null> => {
    if (!userId) return null;

    try {
      const { data, error } = await supabase
        .from('practice_sessions')
        .insert({
          user_id: userId,
          cefr,
          mode,
          total_questions: 0,
          correct_count: 0,
        })
        .select()
        .single();

      if (error) throw error;
      return data?.id || null;
    } catch (err) {
      console.error('Error creating session:', err);
      return null;
    }
  }, [userId]);

  const endSession = useCallback(async (
    sessionId: string,
    totalQuestions: number,
    correctCount: number,
    xpEarned: number,
    summary: string
  ) => {
    if (!userId) return;
    
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;

    try {
      // Update session
      await supabase
        .from('practice_sessions')
        .update({
          ended_at: new Date().toISOString(),
          total_questions: totalQuestions,
          correct_count: correctCount,
          accuracy,
          xp_earned: xpEarned,
          summary_text: summary,
        })
        .eq('id', sessionId);
      // Note: Streak is now handled by database trigger (update_streak_on_practice)
    } catch (err) {
      console.error('Error ending session:', err);
    }
  }, [userId]);

  const recordAttempt = useCallback(async (
    questionId: number,
    chosenAnswer: string,
    isCorrect: boolean,
    timeSpent: number
  ) => {
    if (!userId) return;

    try {
      await supabase.from('attempts').insert({
        user_id: userId,
        question_id: questionId,
        chosen_answer: chosenAnswer,
        is_correct: isCorrect,
        time_spent_sec: timeSpent,
      });
    } catch (err) {
      console.error('Error recording attempt:', err);
    }
  }, [userId]);

  const updateUserXP = useCallback(async (currentXP: number, xpEarned: number) => {
    if (!userId) return;

    try {
      await supabase
        .from('profiles')
        .update({ xp: currentXP + xpEarned })
        .eq('user_id', userId);
    } catch (err) {
      console.error('Error updating XP:', err);
    }
  }, [userId]);

  return {
    sessions,
    stats,
    levelStats,
    loading,
    fetchRecentSessions,
    fetchStats,
    fetchLevelStats,
    createSession,
    endSession,
    recordAttempt,
    updateUserXP,
  };
};
