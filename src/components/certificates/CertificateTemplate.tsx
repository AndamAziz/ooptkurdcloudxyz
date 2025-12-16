import { QRCodeSVG } from 'qrcode.react';
import { Award, CheckCircle2 } from 'lucide-react';
import type { CEFRLevel } from '@/lib/constants';
import { format } from 'date-fns';

export type CertificateType = 'level_completion' | 'course_completion' | 'excellence';
export type CertificateStatus = 'earned' | 'pending' | 'revoked';

export interface CertificateData {
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
}

interface CertificateTemplateProps {
  certificate: CertificateData;
  studentName: string;
  verificationUrl: string;
}

const getLevelTheme = (level: CEFRLevel) => {
  if (['A1', 'A2'].includes(level)) {
    return {
      borderColor: '#CD7F32',
      accentColor: '#8B4513',
      bgGradient: 'linear-gradient(135deg, #FDF5E6 0%, #F5DEB3 100%)',
      ribbonColor: '#CD7F32',
    };
  }
  if (['B1', 'B2'].includes(level)) {
    return {
      borderColor: '#C0C0C0',
      accentColor: '#708090',
      bgGradient: 'linear-gradient(135deg, #F8F8FF 0%, #E8E8E8 100%)',
      ribbonColor: '#A8A8A8',
    };
  }
  return {
    borderColor: '#D4AF37',
    accentColor: '#B8860B',
    bgGradient: 'linear-gradient(135deg, #FFFAF0 0%, #FDF8E8 100%)',
    ribbonColor: '#D4AF37',
  };
};

const getCertificateTitle = (type: string) => {
  switch (type) {
    case 'level_completion':
      return 'Certificate of Achievement';
    case 'course_completion':
      return 'Certificate of Mastery';
    case 'excellence':
      return 'Certificate of Excellence';
    default:
      return 'Certificate';
  }
};

const getCertificateSubtitle = (type: string, level: CEFRLevel) => {
  switch (type) {
    case 'level_completion':
      return `CEFR Level ${level} English Proficiency`;
    case 'course_completion':
      return 'Complete CEFR English Mastery (A1-C2)';
    case 'excellence':
      return `Excellence in English Learning - Level ${level}`;
    default:
      return '';
  }
};

export const CertificateTemplate = ({
  certificate,
  studentName,
  verificationUrl,
}: CertificateTemplateProps) => {
  const theme = getLevelTheme(certificate.cefr_level);
  const isVerified = certificate.verified_by_admin;
  const isExcellence = certificate.certificate_type === 'excellence';
  const isCourseCompletion = certificate.certificate_type === 'course_completion';

  return (
    <div
      id="certificate-template"
      style={{
        width: '297mm',
        height: '210mm',
        background: theme.bgGradient,
        position: 'relative',
        fontFamily: "'Inter', sans-serif",
        overflow: 'hidden',
        boxSizing: 'border-box',
      }}
    >
      {/* Decorative Border */}
      <div
        style={{
          position: 'absolute',
          inset: '8mm',
          border: `3px solid ${theme.borderColor}`,
          borderRadius: '8px',
        }}
      />
      <div
        style={{
          position: 'absolute',
          inset: '12mm',
          border: `1px solid ${theme.borderColor}`,
          borderRadius: '4px',
        }}
      />

      {/* Corner Decorations */}
      {['top-left', 'top-right', 'bottom-left', 'bottom-right'].map((corner) => (
        <div
          key={corner}
          style={{
            position: 'absolute',
            width: '40px',
            height: '40px',
            ...(corner.includes('top') ? { top: '15mm' } : { bottom: '15mm' }),
            ...(corner.includes('left') ? { left: '15mm' } : { right: '15mm' }),
            borderTop: corner.includes('top') ? `2px solid ${theme.accentColor}` : 'none',
            borderBottom: corner.includes('bottom') ? `2px solid ${theme.accentColor}` : 'none',
            borderLeft: corner.includes('left') ? `2px solid ${theme.accentColor}` : 'none',
            borderRight: corner.includes('right') ? `2px solid ${theme.accentColor}` : 'none',
          }}
        />
      ))}

      {/* Content */}
      <div
        style={{
          position: 'absolute',
          inset: '20mm',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          textAlign: 'center',
        }}
      >
        {/* Header with Logo */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '8px' }}>
          <div
            style={{
              width: '60px',
              height: '60px',
              borderRadius: '12px',
              background: '#1E3A5F',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Award style={{ width: '36px', height: '36px', color: '#D4AF37' }} />
          </div>
          <span style={{ fontSize: '14px', color: '#1E3A5F', fontWeight: 500 }}>CEFR Practice</span>
        </div>

        {/* Title */}
        <div style={{ marginTop: '-10px' }}>
          <h1
            style={{
              fontFamily: "'Georgia', serif",
              fontSize: '42px',
              fontWeight: 700,
              color: '#1E3A5F',
              margin: 0,
              letterSpacing: '2px',
            }}
          >
            {getCertificateTitle(certificate.certificate_type)}
          </h1>
          <p style={{ fontSize: '16px', color: theme.accentColor, marginTop: '8px', fontWeight: 500 }}>
            {getCertificateSubtitle(certificate.certificate_type, certificate.cefr_level)}
          </p>
        </div>

        {/* This is to certify */}
        <div>
          <p style={{ fontSize: '14px', color: '#666', marginBottom: '12px' }}>This is to certify that</p>
          <h2
            style={{
              fontFamily: "'Georgia', serif",
              fontSize: '36px',
              fontWeight: 700,
              color: '#1E3A5F',
              margin: 0,
              borderBottom: `2px solid ${theme.borderColor}`,
              paddingBottom: '8px',
              paddingLeft: '40px',
              paddingRight: '40px',
            }}
          >
            {studentName}
          </h2>
          <p style={{ fontSize: '14px', color: '#666', marginTop: '16px' }}>
            has successfully completed{' '}
            <strong style={{ color: '#1E3A5F' }}>CEFR Level {certificate.cefr_level}</strong>
          </p>
          <p style={{ fontSize: '14px', color: '#666', marginTop: '4px' }}>
            with a score of <strong style={{ color: '#2E7D32' }}>{certificate.score_percentage}%</strong>
          </p>
        </div>

        {/* Stats */}
        <div style={{ display: 'flex', gap: '40px', fontSize: '12px', color: '#666' }}>
          <div>
            <strong>{certificate.total_questions_answered}</strong> Questions Answered
          </div>
          <div>
            <strong>{certificate.total_correct}</strong> Correct Answers
          </div>
          <div>
            <strong>{certificate.study_hours.toFixed(1)}</strong> Study Hours
          </div>
        </div>

        {/* Footer */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            width: '100%',
            alignItems: 'flex-end',
          }}
        >
          {/* Date & Certificate Number */}
          <div style={{ textAlign: 'left', fontSize: '11px', color: '#666' }}>
            <p style={{ margin: '2px 0' }}>
              <strong>Date:</strong> {format(new Date(certificate.issued_at), 'MMMM d, yyyy')}
            </p>
            <p style={{ margin: '2px 0' }}>
              <strong>Certificate No:</strong> {certificate.certificate_number}
            </p>
            {isVerified && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '4px', color: '#2E7D32', marginTop: '4px' }}>
                <CheckCircle2 style={{ width: '14px', height: '14px' }} />
                <span style={{ fontSize: '10px', fontWeight: 600 }}>VERIFIED</span>
              </div>
            )}
          </div>

          {/* QR Code */}
          <div style={{ textAlign: 'center' }}>
            <QRCodeSVG value={verificationUrl} size={60} level="M" />
            <p style={{ fontSize: '9px', color: '#999', marginTop: '4px' }}>Scan to verify</p>
          </div>

          {/* Signature */}
          <div style={{ textAlign: 'right' }}>
            <div
              style={{
                width: '120px',
                borderTop: `1px solid ${theme.accentColor}`,
                paddingTop: '4px',
              }}
            >
              <p style={{ fontSize: '10px', color: '#666', margin: 0 }}>Authorized Signature</p>
            </div>
          </div>
        </div>
      </div>

      {/* Excellence Badge */}
      {(isExcellence || isCourseCompletion) && (
        <div
          style={{
            position: 'absolute',
            top: '25mm',
            right: '25mm',
            width: '50px',
            height: '50px',
            background: theme.ribbonColor,
            borderRadius: '50%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 2px 8px rgba(0,0,0,0.2)',
          }}
        >
          <Award style={{ width: '28px', height: '28px', color: 'white' }} />
        </div>
      )}
    </div>
  );
};
