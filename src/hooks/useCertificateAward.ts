import { useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import { LEVEL_THRESHOLDS, calculateLevel, type CEFRLevel } from '@/lib/constants';

interface AwardResult {
  awarded: boolean;
  certificate: {
    id: string;
    certificate_number: string;
    cefr_level: CEFRLevel;
    certificate_type: string;
    score_percentage: number;
  } | null;
}

export const useCertificateAward = (userId: string | undefined) => {
  const checkAndAwardCertificate = useCallback(async (
    oldXP: number,
    newXP: number,
    accuracy: number,
    totalQuestions: number,
    totalCorrect: number
  ): Promise<AwardResult> => {
    if (!userId) return { awarded: false, certificate: null };

    const oldLevel = calculateLevel(oldXP);
    const newLevel = calculateLevel(newXP);

    // Check if user leveled up
    if (oldLevel === newLevel) {
      return { awarded: false, certificate: null };
    }

    // User leveled up! Check if they already have a certificate for this level
    const { data: existingCert } = await supabase
      .from('certificates')
      .select('id')
      .eq('user_id', userId)
      .eq('cefr_level', newLevel)
      .eq('certificate_type', 'level_completion')
      .maybeSingle();

    if (existingCert) {
      return { awarded: false, certificate: null };
    }

    // Calculate study hours from practice sessions
    const { data: sessions } = await supabase
      .from('practice_sessions')
      .select('started_at, ended_at')
      .eq('user_id', userId)
      .not('ended_at', 'is', null);

    let studyHours = 0;
    if (sessions) {
      sessions.forEach(session => {
        if (session.ended_at && session.started_at) {
          const start = new Date(session.started_at).getTime();
          const end = new Date(session.ended_at).getTime();
          studyHours += (end - start) / (1000 * 60 * 60);
        }
      });
    }

    // Determine certificate type
    let certificateType: 'level_completion' | 'excellence' | 'course_completion' = 'level_completion';
    
    // Excellence award for 90%+ accuracy
    if (accuracy >= 90) {
      // Check if they already have an excellence cert for this level
      const { data: existingExcellence } = await supabase
        .from('certificates')
        .select('id')
        .eq('user_id', userId)
        .eq('cefr_level', newLevel)
        .eq('certificate_type', 'excellence')
        .maybeSingle();

      if (!existingExcellence) {
        certificateType = 'excellence';
      }
    }

    // Course completion for reaching C2
    if (newLevel === 'C2') {
      // Check all levels completed
      const allLevels: CEFRLevel[] = ['A1', 'A2', 'B1', 'B2', 'C1', 'C2'];
      const { data: certs } = await supabase
        .from('certificates')
        .select('cefr_level')
        .eq('user_id', userId)
        .in('certificate_type', ['level_completion', 'excellence']);

      const completedLevels = new Set(certs?.map(c => c.cefr_level) || []);
      completedLevels.add(newLevel); // Include current level

      const hasAllLevels = allLevels.every(level => completedLevels.has(level));
      
      if (hasAllLevels) {
        // Check if they already have course completion
        const { data: existingCourse } = await supabase
          .from('certificates')
          .select('id')
          .eq('user_id', userId)
          .eq('certificate_type', 'course_completion')
          .maybeSingle();

        if (!existingCourse) {
          certificateType = 'course_completion';
        }
      }
    }

    // Get total stats
    const { data: allAttempts } = await supabase
      .from('attempts')
      .select('is_correct')
      .eq('user_id', userId);

    const totalAttempts = allAttempts?.length || totalQuestions;
    const totalCorrectAttempts = allAttempts?.filter(a => a.is_correct).length || totalCorrect;
    const overallAccuracy = totalAttempts > 0 
      ? Math.round((totalCorrectAttempts / totalAttempts) * 100) 
      : accuracy;

    // Create the certificate
    const { data: newCert, error } = await supabase
      .from('certificates')
      .insert({
        user_id: userId,
        certificate_type: certificateType,
        cefr_level: newLevel,
        score_percentage: overallAccuracy,
        total_questions_answered: totalAttempts,
        total_correct: totalCorrectAttempts,
        study_hours: Math.round(studyHours * 10) / 10,
        status: 'earned',
      })
      .select('id, certificate_number, cefr_level, certificate_type, score_percentage')
      .single();

    if (error) {
      console.error('Error creating certificate:', error);
      return { awarded: false, certificate: null };
    }

    return {
      awarded: true,
      certificate: newCert as AwardResult['certificate'],
    };
  }, [userId]);

  return { checkAndAwardCertificate };
};
