import { QRCodeSVG } from 'qrcode.react';
import { Award, CheckCircle2, Sparkles } from 'lucide-react';
import { format } from 'date-fns';
import type { Certificate } from '@/hooks/useCertificates';

interface CertificateTemplateProps {
  certificate: Certificate;
  studentName: string;
  verificationUrl: string;
}

const getLevelTheme = (level: string) => {
  switch (level) {
    case 'A1':
      return {
        primaryColor: '#8B5A2B',
        secondaryColor: '#D4A574',
        borderColor: '#CD7F32',
        bgGradient: 'linear-gradient(180deg, #FFFBF5 0%, #FDF5E8 50%, #F5E6D3 100%)',
        patternColor: 'rgba(205, 127, 50, 0.08)',
        levelTitle: 'Beginner',
        icon: '🌱',
        sealColor: '#CD7F32',
      };
    case 'A2':
      return {
        primaryColor: '#7B4B3A',
        secondaryColor: '#C9896D',
        borderColor: '#D2691E',
        bgGradient: 'linear-gradient(180deg, #FFFCF5 0%, #FFF3E6 50%, #FFE4CC 100%)',
        patternColor: 'rgba(210, 105, 30, 0.08)',
        levelTitle: 'Elementary',
        icon: '🌿',
        sealColor: '#D2691E',
      };
    case 'B1':
      return {
        primaryColor: '#4A5568',
        secondaryColor: '#A0AEC0',
        borderColor: '#718096',
        bgGradient: 'linear-gradient(180deg, #FAFBFC 0%, #F0F4F8 50%, #E2E8F0 100%)',
        patternColor: 'rgba(113, 128, 150, 0.08)',
        levelTitle: 'Intermediate',
        icon: '🥈',
        sealColor: '#A0AEC0',
      };
    case 'B2':
      return {
        primaryColor: '#2C5282',
        secondaryColor: '#4299E1',
        borderColor: '#3182CE',
        bgGradient: 'linear-gradient(180deg, #F7FAFC 0%, #EBF4FF 50%, #DBEAFE 100%)',
        patternColor: 'rgba(49, 130, 206, 0.08)',
        levelTitle: 'Upper Intermediate',
        icon: '💎',
        sealColor: '#3182CE',
      };
    case 'C1':
      return {
        primaryColor: '#744210',
        secondaryColor: '#D69E2E',
        borderColor: '#D4AF37',
        bgGradient: 'linear-gradient(180deg, #FFFDF5 0%, #FEFCE8 50%, #FEF3C7 100%)',
        patternColor: 'rgba(212, 175, 55, 0.1)',
        levelTitle: 'Advanced',
        icon: '🥇',
        sealColor: '#D4AF37',
      };
    case 'C2':
      return {
        primaryColor: '#553C9A',
        secondaryColor: '#9F7AEA',
        borderColor: '#805AD5',
        bgGradient: 'linear-gradient(180deg, #FAF5FF 0%, #F3E8FF 50%, #E9D8FD 100%)',
        patternColor: 'rgba(128, 90, 213, 0.08)',
        levelTitle: 'Mastery',
        icon: '👑',
        sealColor: '#805AD5',
      };
    default:
      return {
        primaryColor: '#744210',
        secondaryColor: '#D69E2E',
        borderColor: '#D4AF37',
        bgGradient: 'linear-gradient(180deg, #FFFDF5 0%, #FEFCE8 50%, #FEF3C7 100%)',
        patternColor: 'rgba(212, 175, 55, 0.1)',
        levelTitle: '',
        icon: '🎓',
        sealColor: '#D4AF37',
      };
  }
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

export const CertificateTemplate = ({
  certificate,
  studentName,
  verificationUrl,
}: CertificateTemplateProps) => {
  const theme = getLevelTheme(certificate.cefr_level);
  const isVerified = certificate.verified_by_admin;

  return (
    <div
      id="certificate-template"
      style={{
        width: '297mm',
        height: '210mm',
        background: theme.bgGradient,
        position: 'relative',
        fontFamily: "'Times New Roman', 'Georgia', serif",
        overflow: 'hidden',
        boxSizing: 'border-box',
        padding: '0',
        margin: '0',
      }}
    >
      {/* Background Pattern */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          backgroundImage: `
            radial-gradient(circle at 20% 20%, ${theme.patternColor} 1px, transparent 1px),
            radial-gradient(circle at 80% 80%, ${theme.patternColor} 1px, transparent 1px)
          `,
          backgroundSize: '30px 30px',
        }}
      />

      {/* Watermark for authenticity */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          pointerEvents: 'none',
          overflow: 'hidden',
        }}
      >
        <div
          style={{
            position: 'absolute',
            width: '200%',
            height: '200%',
            display: 'flex',
            flexWrap: 'wrap',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '80px',
            transform: 'rotate(-30deg)',
            opacity: 0.04,
          }}
        >
          {Array.from({ length: 20 }).map((_, i) => (
            <div
              key={i}
              style={{
                fontSize: '28px',
                fontWeight: 700,
                color: theme.primaryColor,
                letterSpacing: '8px',
                fontFamily: "'Arial', sans-serif",
                textTransform: 'uppercase',
                whiteSpace: 'nowrap',
              }}
            >
              CEFR CERTIFIED • AUTHENTIC •
            </div>
          ))}
        </div>
      </div>

      {/* Center Watermark Seal */}
      <div
        style={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          width: '180px',
          height: '180px',
          borderRadius: '50%',
          border: `3px solid ${theme.borderColor}`,
          opacity: 0.08,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          pointerEvents: 'none',
        }}
      >
        <div
          style={{
            width: '150px',
            height: '150px',
            borderRadius: '50%',
            border: `2px solid ${theme.borderColor}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexDirection: 'column',
          }}
        >
          <span style={{ fontSize: '14px', fontWeight: 700, color: theme.primaryColor, letterSpacing: '2px' }}>
            CERTIFIED
          </span>
          <span style={{ fontSize: '32px', fontWeight: 700, color: theme.primaryColor }}>
            {certificate.cefr_level}
          </span>
          <span style={{ fontSize: '10px', color: theme.primaryColor, letterSpacing: '1px' }}>
            AUTHENTIC
          </span>
        </div>
      </div>

      {/* Outer Decorative Border */}
      <div
        style={{
          position: 'absolute',
          inset: '6mm',
          border: `4px double ${theme.borderColor}`,
          borderRadius: '2px',
        }}
      />
      {/* Inner Decorative Border */}
      <div
        style={{
          position: 'absolute',
          inset: '10mm',
          border: `2px solid ${theme.borderColor}`,
          borderRadius: '2px',
        }}
      />

      {/* Ornamental Corner Decorations */}
      {['top-left', 'top-right', 'bottom-left', 'bottom-right'].map((corner) => (
        <div
          key={corner}
          style={{
            position: 'absolute',
            width: '50px',
            height: '50px',
            ...(corner.includes('top') ? { top: '12mm' } : { bottom: '12mm' }),
            ...(corner.includes('left') ? { left: '12mm' } : { right: '12mm' }),
          }}
        >
          <svg width="50" height="50" viewBox="0 0 50 50" style={{
            transform: `
              ${corner.includes('right') ? 'scaleX(-1)' : ''} 
              ${corner.includes('bottom') ? 'scaleY(-1)' : ''}
            `.trim()
          }}>
            <path
              d="M5,5 L5,45 M5,5 L45,5 M5,15 Q15,15 15,5 M5,25 Q25,25 25,5 M5,35 Q35,35 35,5"
              stroke={theme.borderColor}
              strokeWidth="1.5"
              fill="none"
            />
          </svg>
        </div>
      ))}

      {/* Content Container */}
      <div
        style={{
          position: 'absolute',
          inset: '18mm',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'space-between',
          textAlign: 'center',
        }}
      >
        {/* Header Section */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '4px' }}>
          {/* Logo/Seal Area */}
          <div
            style={{
              width: '70px',
              height: '70px',
              borderRadius: '50%',
              background: `linear-gradient(145deg, ${theme.sealColor}20, ${theme.sealColor}40)`,
              border: `3px solid ${theme.sealColor}`,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: `0 4px 15px ${theme.sealColor}30`,
            }}
          >
            <span style={{ fontSize: '36px' }}>{theme.icon}</span>
          </div>
          
          {/* Level Badge */}
          <div
            style={{
              padding: '6px 20px',
              background: `linear-gradient(135deg, ${theme.sealColor}, ${theme.primaryColor})`,
              borderRadius: '25px',
              color: 'white',
              fontSize: '11px',
              fontWeight: 700,
              letterSpacing: '2px',
              fontFamily: "'Arial', sans-serif",
              textTransform: 'uppercase',
              boxShadow: `0 2px 8px ${theme.sealColor}50`,
            }}
          >
            CEFR Level {certificate.cefr_level} • {theme.levelTitle}
          </div>
        </div>

        {/* Main Title */}
        <div style={{ marginTop: '-5px' }}>
          <h1
            style={{
              fontSize: '44px',
              fontWeight: 400,
              color: theme.primaryColor,
              margin: 0,
              letterSpacing: '3px',
              textTransform: 'uppercase',
              fontFamily: "'Times New Roman', 'Georgia', serif",
            }}
          >
            {getCertificateTitle(certificate.certificate_type)}
          </h1>
          <div
            style={{
              width: '200px',
              height: '2px',
              background: `linear-gradient(90deg, transparent, ${theme.borderColor}, transparent)`,
              margin: '10px auto',
            }}
          />
        </div>

        {/* Recipient Section */}
        <div style={{ width: '100%' }}>
          <p
            style={{
              fontSize: '14px',
              color: '#666',
              marginBottom: '8px',
              fontFamily: "'Georgia', serif",
              fontStyle: 'italic',
            }}
          >
            This is to certify that
          </p>
          <h2
            style={{
              fontSize: '38px',
              fontWeight: 400,
              color: theme.primaryColor,
              margin: '0',
              fontFamily: "'Georgia', serif",
              letterSpacing: '1px',
            }}
          >
            {studentName}
          </h2>
          <div
            style={{
              width: '300px',
              height: '1px',
              background: theme.borderColor,
              margin: '8px auto 16px',
            }}
          />
          <p
            style={{
              fontSize: '15px',
              color: '#444',
              lineHeight: '1.6',
              maxWidth: '500px',
              margin: '0 auto',
              fontFamily: "'Georgia', serif",
            }}
          >
            has successfully demonstrated proficiency in English at the
            <br />
            <strong style={{ color: theme.primaryColor, fontSize: '16px' }}>
              CEFR {certificate.cefr_level} ({theme.levelTitle})
            </strong>
            {' '}level
            <br />
            with a score of{' '}
            <strong style={{ color: '#2E7D32', fontSize: '18px' }}>{certificate.score_percentage}%</strong>
          </p>
        </div>

        {/* Achievement Stats */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            gap: '50px',
            padding: '12px 30px',
            background: `linear-gradient(135deg, ${theme.patternColor}, transparent)`,
            borderRadius: '8px',
            border: `1px solid ${theme.borderColor}30`,
          }}
        >
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '22px', fontWeight: 700, color: theme.primaryColor }}>
              {certificate.total_questions_answered}
            </div>
            <div style={{ fontSize: '10px', color: '#666', textTransform: 'uppercase', letterSpacing: '1px', fontFamily: "'Arial', sans-serif" }}>
              Questions
            </div>
          </div>
          <div style={{ width: '1px', background: theme.borderColor, opacity: 0.3 }} />
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '22px', fontWeight: 700, color: theme.primaryColor }}>
              {certificate.total_correct}
            </div>
            <div style={{ fontSize: '10px', color: '#666', textTransform: 'uppercase', letterSpacing: '1px', fontFamily: "'Arial', sans-serif" }}>
              Correct
            </div>
          </div>
          <div style={{ width: '1px', background: theme.borderColor, opacity: 0.3 }} />
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '22px', fontWeight: 700, color: theme.primaryColor }}>
              {certificate.study_hours.toFixed(1)}h
            </div>
            <div style={{ fontSize: '10px', color: '#666', textTransform: 'uppercase', letterSpacing: '1px', fontFamily: "'Arial', sans-serif" }}>
              Study Time
            </div>
          </div>
        </div>

        {/* Footer Section */}
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-end',
            width: '100%',
            paddingTop: '10px',
          }}
        >
          {/* Left: Date and Certificate Info */}
          <div style={{ textAlign: 'left' }}>
            <div style={{ marginBottom: '12px' }}>
              <p style={{ fontSize: '10px', color: '#888', margin: '0 0 4px 0', fontFamily: "'Arial', sans-serif", textTransform: 'uppercase', letterSpacing: '1px' }}>
                Date Issued
              </p>
              <p style={{ fontSize: '14px', color: theme.primaryColor, margin: 0, fontWeight: 600 }}>
                {format(new Date(certificate.issued_at), 'MMMM d, yyyy')}
              </p>
            </div>
            <div>
              <p style={{ fontSize: '10px', color: '#888', margin: '0 0 4px 0', fontFamily: "'Arial', sans-serif", textTransform: 'uppercase', letterSpacing: '1px' }}>
                Certificate No.
              </p>
              <p style={{ fontSize: '12px', color: '#555', margin: 0, fontFamily: "'Courier New', monospace" }}>
                {certificate.certificate_number}
              </p>
            </div>
          </div>

          {/* Center: QR Code */}
          <div style={{ textAlign: 'center' }}>
            <div
              style={{
                padding: '8px',
                background: 'white',
                borderRadius: '8px',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                border: `1px solid ${theme.borderColor}30`,
              }}
            >
              <QRCodeSVG value={verificationUrl} size={55} level="M" />
            </div>
            <p style={{ fontSize: '9px', color: '#888', marginTop: '4px', fontFamily: "'Arial', sans-serif" }}>
              Scan to Verify
            </p>
            <p style={{ fontSize: '8px', color: '#666', marginTop: '2px', fontFamily: "'Arial', sans-serif", fontWeight: 500 }}>
              {new URL(verificationUrl).host}
            </p>
          </div>

          {/* Right: Signature and Verification */}
          <div style={{ textAlign: 'right' }}>
            <div style={{ marginBottom: '8px' }}>
              <div
                style={{
                  width: '140px',
                  height: '40px',
                  borderBottom: `2px solid ${theme.borderColor}`,
                  marginLeft: 'auto',
                }}
              />
              <p style={{ fontSize: '10px', color: '#888', margin: '4px 0 0 0', fontFamily: "'Arial', sans-serif", textTransform: 'uppercase', letterSpacing: '1px' }}>
                Authorized Signature
              </p>
            </div>
            {isVerified && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'flex-end',
                  gap: '4px',
                  color: '#2E7D32',
                  marginTop: '8px',
                }}
              >
                <CheckCircle2 style={{ width: '14px', height: '14px' }} />
                <span style={{ fontSize: '10px', fontWeight: 700, letterSpacing: '1px', fontFamily: "'Arial', sans-serif" }}>
                  OFFICIALLY VERIFIED
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Excellence/Course Completion Ribbon */}
      {(certificate.certificate_type === 'excellence' || certificate.certificate_type === 'course_completion') && (
        <div
          style={{
            position: 'absolute',
            top: '20mm',
            right: '-25mm',
            width: '120px',
            background: `linear-gradient(135deg, ${theme.sealColor}, ${theme.primaryColor})`,
            color: 'white',
            textAlign: 'center',
            padding: '6px 0',
            fontSize: '9px',
            fontWeight: 700,
            letterSpacing: '1px',
            transform: 'rotate(45deg)',
            boxShadow: '0 2px 10px rgba(0,0,0,0.2)',
            fontFamily: "'Arial', sans-serif",
          }}
        >
          {certificate.certificate_type === 'excellence' ? '★ EXCELLENCE ★' : '★ MASTERY ★'}
        </div>
      )}
    </div>
  );
};
