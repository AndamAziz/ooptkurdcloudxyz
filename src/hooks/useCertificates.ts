import { useState, useCallback } from 'react';
import { supabase } from '@/integrations/supabase/client';
import type { CEFRLevel } from '@/lib/constants';

export type CertificateType = 'level_completion' | 'course_completion' | 'excellence';
export type CertificateStatus = 'earned' | 'pending' | 'revoked';

export interface Certificate {
  id: string;
  user_id: string;
  certificate_type: CertificateType;
  cefr_level: CEFRLevel;
  issued_at: string;
  certificate_number: string;
  score_percentage: number;
  total_questions_answered: number;
  total_correct: number;
  study_hours: number;
  status: CertificateStatus;
  pdf_url: string | null;
  verified_by_admin: boolean;
  admin_notes: string | null;
  created_at: string;
  updated_at: string;
  profiles?: {
    display_name: string | null;
    username: string | null;
  };
}

export const useCertificates = (userId?: string) => {
  const [certificates, setCertificates] = useState<Certificate[]>([]);
  const [loading, setLoading] = useState(false);

  const fetchUserCertificates = useCallback(async () => {
    if (!userId) return;
    setLoading(true);
    
    const { data, error } = await supabase
      .from('certificates')
      .select('*')
      .eq('user_id', userId)
      .order('issued_at', { ascending: false });
    
    if (!error && data) {
      setCertificates(data as unknown as Certificate[]);
    }
    setLoading(false);
  }, [userId]);

  const fetchAllCertificates = useCallback(async () => {
    setLoading(true);
    
    const { data, error } = await supabase
      .from('certificates')
      .select('*')
      .order('issued_at', { ascending: false });
    
    if (!error && data) {
      // Fetch profiles separately
      const userIds = [...new Set(data.map(c => c.user_id))];
      const { data: profiles } = await supabase
        .from('profiles')
        .select('user_id, display_name, username')
        .in('user_id', userIds);

      const profileMap = new Map(profiles?.map(p => [p.user_id, p]));
      
      const certificatesWithProfiles = data.map(cert => ({
        ...cert,
        profiles: profileMap.get(cert.user_id) || null
      }));
      
      setCertificates(certificatesWithProfiles as unknown as Certificate[]);
    }
    setLoading(false);
  }, []);

  const createCertificate = useCallback(async (
    certificateType: CertificateType,
    cefrLevel: CEFRLevel,
    scorePercentage: number,
    totalQuestions: number,
    totalCorrect: number,
    studyHours: number
  ) => {
    if (!userId) return null;

    const { data, error } = await supabase
      .from('certificates')
      .insert({
        user_id: userId,
        certificate_type: certificateType,
        cefr_level: cefrLevel,
        score_percentage: scorePercentage,
        total_questions_answered: totalQuestions,
        total_correct: totalCorrect,
        study_hours: studyHours,
        status: 'earned',
      })
      .select()
      .single();

    if (error) {
      console.error('Error creating certificate:', error);
      return null;
    }

    return data as unknown as Certificate;
  }, [userId]);

  const checkExistingCertificate = useCallback(async (
    cefrLevel: CEFRLevel,
    certificateType: CertificateType
  ) => {
    if (!userId) return null;

    const { data } = await supabase
      .from('certificates')
      .select('id')
      .eq('user_id', userId)
      .eq('cefr_level', cefrLevel)
      .eq('certificate_type', certificateType)
      .maybeSingle();

    return data;
  }, [userId]);

  const verifyCertificate = useCallback(async (certificateId: string, adminNotes?: string) => {
    const { error } = await supabase
      .from('certificates')
      .update({ 
        verified_by_admin: true,
        admin_notes: adminNotes || null
      })
      .eq('id', certificateId);

    return !error;
  }, []);

  const revokeCertificate = useCallback(async (certificateId: string, reason: string) => {
    const { error } = await supabase
      .from('certificates')
      .update({ 
        status: 'revoked',
        admin_notes: reason
      })
      .eq('id', certificateId);

    return !error;
  }, []);

  const getCertificateByNumber = useCallback(async (certificateNumber: string) => {
    const { data, error } = await supabase
      .from('certificates')
      .select('*')
      .eq('certificate_number', certificateNumber)
      .maybeSingle();

    if (error || !data) return null;
    
    // Fetch profile separately
    const { data: profile } = await supabase
      .from('profiles')
      .select('display_name, username')
      .eq('user_id', data.user_id)
      .maybeSingle();

    return { ...data, profiles: profile } as unknown as Certificate;
  }, []);

  return {
    certificates,
    loading,
    fetchUserCertificates,
    fetchAllCertificates,
    createCertificate,
    checkExistingCertificate,
    verifyCertificate,
    revokeCertificate,
    getCertificateByNumber,
  };
};
