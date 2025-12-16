import { useState, useEffect, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { subDays, startOfDay, endOfDay, format } from 'date-fns';

interface AdminStats {
  totalStudents: number;
  activeToday: number;
  totalAttempts: number;
  totalCertificates: number;
  newThisWeek: number;
  activeDiff: number;
  attemptsToday: number;
  certsThisWeek: number;
}

interface LevelDistribution {
  level: string;
  count: number;
}

interface DailyData {
  date: string;
  count: number;
}

interface AccuracyByLevel {
  level: string;
  accuracy: number;
}

export const useAdminStats = () => {
  const [stats, setStats] = useState<AdminStats>({
    totalStudents: 0,
    activeToday: 0,
    totalAttempts: 0,
    totalCertificates: 0,
    newThisWeek: 0,
    activeDiff: 0,
    attemptsToday: 0,
    certsThisWeek: 0,
  });
  const [levelDistribution, setLevelDistribution] = useState<LevelDistribution[]>([]);
  const [registrationTrend, setRegistrationTrend] = useState<DailyData[]>([]);
  const [dailyActiveUsers, setDailyActiveUsers] = useState<DailyData[]>([]);
  const [accuracyByLevel, setAccuracyByLevel] = useState<AccuracyByLevel[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchStats = useCallback(async () => {
    setLoading(true);
    try {
      const today = new Date();
      const todayStart = startOfDay(today).toISOString();
      const todayEnd = endOfDay(today).toISOString();
      const weekAgo = subDays(today, 7).toISOString();
      const yesterdayStart = startOfDay(subDays(today, 1)).toISOString();
      const yesterdayEnd = endOfDay(subDays(today, 1)).toISOString();

      // Parallel queries
      const [
        { count: totalStudents },
        { count: activeToday },
        { count: totalAttempts },
        { count: totalCertificates },
        { count: newThisWeek },
        { count: activeYesterday },
        { count: attemptsToday },
        { count: certsThisWeek },
      ] = await Promise.all([
        supabase.from('profiles').select('*', { count: 'exact', head: true }),
        supabase.from('profiles').select('*', { count: 'exact', head: true }).gte('last_active_at', todayStart).lte('last_active_at', todayEnd),
        supabase.from('attempts').select('*', { count: 'exact', head: true }),
        supabase.from('certificates').select('*', { count: 'exact', head: true }),
        supabase.from('profiles').select('*', { count: 'exact', head: true }).gte('created_at', weekAgo),
        supabase.from('profiles').select('*', { count: 'exact', head: true }).gte('last_active_at', yesterdayStart).lte('last_active_at', yesterdayEnd),
        supabase.from('attempts').select('*', { count: 'exact', head: true }).gte('created_at', todayStart),
        supabase.from('certificates').select('*', { count: 'exact', head: true }).gte('created_at', weekAgo),
      ]);

      setStats({
        totalStudents: totalStudents || 0,
        activeToday: activeToday || 0,
        totalAttempts: totalAttempts || 0,
        totalCertificates: totalCertificates || 0,
        newThisWeek: newThisWeek || 0,
        activeDiff: (activeToday || 0) - (activeYesterday || 0),
        attemptsToday: attemptsToday || 0,
        certsThisWeek: certsThisWeek || 0,
      });

      // Level distribution
      const { data: profiles } = await supabase
        .from('profiles')
        .select('current_cefr');

      if (profiles) {
        const distribution: Record<string, number> = {};
        profiles.forEach(p => {
          const level = p.current_cefr || 'A1';
          distribution[level] = (distribution[level] || 0) + 1;
        });
        setLevelDistribution(
          ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'].map(level => ({
            level,
            count: distribution[level] || 0,
          }))
        );
      }

      // Registration trend (last 30 days)
      const thirtyDaysAgo = subDays(today, 30);
      const { data: registrations } = await supabase
        .from('profiles')
        .select('created_at')
        .gte('created_at', thirtyDaysAgo.toISOString())
        .order('created_at', { ascending: true });

      if (registrations) {
        const dailyCounts: Record<string, number> = {};
        for (let i = 0; i < 30; i++) {
          const date = format(subDays(today, 29 - i), 'MMM d');
          dailyCounts[date] = 0;
        }
        registrations.forEach(r => {
          const date = format(new Date(r.created_at), 'MMM d');
          if (dailyCounts[date] !== undefined) {
            dailyCounts[date]++;
          }
        });
        setRegistrationTrend(
          Object.entries(dailyCounts).map(([date, count]) => ({ date, count }))
        );
      }

      // Daily active users (last 14 days)
      const { data: sessions } = await supabase
        .from('practice_sessions')
        .select('user_id, started_at')
        .gte('started_at', subDays(today, 14).toISOString());

      if (sessions) {
        const dailyUsers: Record<string, Set<string>> = {};
        for (let i = 0; i < 14; i++) {
          const date = format(subDays(today, 13 - i), 'MMM d');
          dailyUsers[date] = new Set();
        }
        sessions.forEach(s => {
          const date = format(new Date(s.started_at), 'MMM d');
          if (dailyUsers[date]) {
            dailyUsers[date].add(s.user_id);
          }
        });
        setDailyActiveUsers(
          Object.entries(dailyUsers).map(([date, users]) => ({
            date,
            count: users.size,
          }))
        );
      }

      // Accuracy by level
      const { data: attempts } = await supabase
        .from('attempts')
        .select('is_correct, question_id, questions(cefr)')
        .limit(5000);

      if (attempts) {
        const levelStats: Record<string, { correct: number; total: number }> = {};
        attempts.forEach((a: any) => {
          const level = a.questions?.cefr;
          if (level) {
            if (!levelStats[level]) {
              levelStats[level] = { correct: 0, total: 0 };
            }
            levelStats[level].total++;
            if (a.is_correct) levelStats[level].correct++;
          }
        });
        setAccuracyByLevel(
          ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'].map(level => ({
            level,
            accuracy: levelStats[level]
              ? Math.round((levelStats[level].correct / levelStats[level].total) * 100)
              : 0,
          }))
        );
      }
    } catch (error) {
      console.error('Error fetching admin stats:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  return {
    stats,
    levelDistribution,
    registrationTrend,
    dailyActiveUsers,
    accuracyByLevel,
    loading,
    refresh: fetchStats,
  };
};
