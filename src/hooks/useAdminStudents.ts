import { useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { subDays } from 'date-fns';
import { type CEFRLevel } from '@/lib/constants';

export interface StudentData {
  id: string;
  user_id: string;
  display_name: string | null;
  username: string | null;
  current_cefr: CEFRLevel;
  xp: number;
  streak: number;
  longest_streak: number;
  last_active_at: string | null;
  last_practice_date: string | null;
  account_status: string;
  created_at: string;
  total_practice_time_minutes: number;
  totalAttempts?: number;
  correctAttempts?: number;
  accuracy?: number;
}

interface UseAdminStudentsOptions {
  page?: number;
  pageSize?: number;
  searchQuery?: string;
  filterLevel?: string;
  filterStatus?: string;
  sortBy?: string;
  sortOrder?: 'asc' | 'desc';
}

export const useAdminStudents = () => {
  const [students, setStudents] = useState<StudentData[]>([]);
  const [totalCount, setTotalCount] = useState(0);
  const [loading, setLoading] = useState(false);

  const getActivityStatus = (lastActive: string | null): string => {
    if (!lastActive) return 'at_risk';
    const daysSince = Math.floor(
      (Date.now() - new Date(lastActive).getTime()) / (1000 * 60 * 60 * 24)
    );
    if (daysSince <= 3) return 'active';
    if (daysSince <= 7) return 'inactive';
    return 'at_risk';
  };

  const fetchStudents = useCallback(async (options: UseAdminStudentsOptions = {}) => {
    const {
      page = 1,
      pageSize = 20,
      searchQuery = '',
      filterLevel = 'all',
      filterStatus = 'all',
      sortBy = 'created_at',
      sortOrder = 'desc',
    } = options;

    setLoading(true);
    try {
      let query = supabase
        .from('profiles')
        .select('*', { count: 'exact' });

      // Apply filters
      if (searchQuery) {
        query = query.or(`display_name.ilike.%${searchQuery}%,username.ilike.%${searchQuery}%`);
      }

      if (filterLevel !== 'all') {
        query = query.eq('current_cefr', filterLevel as CEFRLevel);
      }

      if (filterStatus === 'suspended') {
        query = query.eq('account_status', 'suspended');
      } else if (filterStatus === 'active') {
        query = query.gte('last_active_at', subDays(new Date(), 3).toISOString());
      } else if (filterStatus === 'inactive') {
        const threeDaysAgo = subDays(new Date(), 3).toISOString();
        const sevenDaysAgo = subDays(new Date(), 7).toISOString();
        query = query.lt('last_active_at', threeDaysAgo).gte('last_active_at', sevenDaysAgo);
      } else if (filterStatus === 'at_risk') {
        query = query.lt('last_active_at', subDays(new Date(), 7).toISOString());
      }

      // Apply sorting
      query = query.order(sortBy, { ascending: sortOrder === 'asc' });

      // Apply pagination
      const from = (page - 1) * pageSize;
      const to = from + pageSize - 1;
      query = query.range(from, to);

      const { data: profilesData, count, error } = await query;

      if (error) throw error;

      // Fetch attempt stats for these students
      const userIds = profilesData?.map(p => p.user_id) || [];
      
      let attemptStats: Record<string, { total: number; correct: number }> = {};
      
      if (userIds.length > 0) {
        const { data: attempts } = await supabase
          .from('attempts')
          .select('user_id, is_correct')
          .in('user_id', userIds);

        if (attempts) {
          attempts.forEach(a => {
            if (!attemptStats[a.user_id]) {
              attemptStats[a.user_id] = { total: 0, correct: 0 };
            }
            attemptStats[a.user_id].total++;
            if (a.is_correct) attemptStats[a.user_id].correct++;
          });
        }
      }

      const enrichedStudents: StudentData[] = (profilesData || []).map(p => ({
        id: p.id,
        user_id: p.user_id,
        display_name: p.display_name,
        username: p.username,
        current_cefr: (p.current_cefr || 'A1') as CEFRLevel,
        xp: p.xp || 0,
        streak: p.streak || 0,
        longest_streak: p.longest_streak || 0,
        last_active_at: p.last_active_at,
        last_practice_date: p.last_practice_date,
        account_status: p.account_status || 'active',
        created_at: p.created_at,
        total_practice_time_minutes: p.total_practice_time_minutes || 0,
        totalAttempts: attemptStats[p.user_id]?.total || 0,
        correctAttempts: attemptStats[p.user_id]?.correct || 0,
        accuracy: attemptStats[p.user_id]?.total 
          ? Math.round((attemptStats[p.user_id].correct / attemptStats[p.user_id].total) * 100)
          : 0,
      }));

      setStudents(enrichedStudents);
      setTotalCount(count || 0);
    } catch (error) {
      console.error('Error fetching students:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  const updateStudentStatus = useCallback(async (userId: string, status: string) => {
    const { error } = await supabase
      .from('profiles')
      .update({ account_status: status })
      .eq('user_id', userId);

    if (error) throw error;
  }, []);

  const logAdminAction = useCallback(async (
    adminId: string,
    actionType: string,
    targetUserId?: string,
    details?: Record<string, any>
  ) => {
    await supabase.from('admin_logs').insert({
      admin_id: adminId,
      action_type: actionType,
      target_user_id: targetUserId,
      details: details || {},
    });
  }, []);

  return {
    students,
    totalCount,
    loading,
    fetchStudents,
    updateStudentStatus,
    logAdminAction,
    getActivityStatus,
  };
};
