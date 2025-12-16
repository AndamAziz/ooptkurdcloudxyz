import { useEffect, useState } from 'react';
import { Award, FileText, Target, Lock, CheckCircle2, Trophy, Crown, Medal, RotateCcw } from 'lucide-react';
import { Link } from 'react-router-dom';
import { Header } from '@/components/Header';
import { CertificateCard } from '@/components/certificates/CertificateCard';
import { CertificatePreviewModal } from '@/components/certificates/CertificatePreviewModal';
import { CertificateTemplate } from '@/components/certificates/CertificateTemplate';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Progress } from '@/components/ui/progress';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useAuth } from '@/contexts/AuthContext';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import { useQuestionProgress, type LevelProgress } from '@/hooks/useQuestionProgress';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

const REQUIRED_ACCURACY = 90;

const getLevelTheme = (level: CEFRLevel) => {
  switch (level) {
    case 'A1':
      return { color: 'text-amber-700 dark:text-amber-400', bg: 'bg-amber-100 dark:bg-amber-900/30', border: 'border-amber-300 dark:border-amber-700', icon: Medal, label: 'Beginner', gradient: 'from-amber-500 to-orange-600' };
    case 'A2':
      return { color: 'text-orange-700 dark:text-orange-400', bg: 'bg-orange-100 dark:bg-orange-900/30', border: 'border-orange-300 dark:border-orange-700', icon: Medal, label: 'Elementary', gradient: 'from-orange-500 to-red-500' };
    case 'B1':
      return { color: 'text-slate-600 dark:text-slate-300', bg: 'bg-slate-100 dark:bg-slate-800/50', border: 'border-slate-300 dark:border-slate-600', icon: Trophy, label: 'Intermediate', gradient: 'from-slate-400 to-slate-600' };
    case 'B2':
      return { color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-100 dark:bg-blue-900/30', border: 'border-blue-300 dark:border-blue-700', icon: Trophy, label: 'Upper Intermediate', gradient: 'from-blue-500 to-indigo-600' };
    case 'C1':
      return { color: 'text-yellow-600 dark:text-yellow-400', bg: 'bg-yellow-100 dark:bg-yellow-900/30', border: 'border-yellow-300 dark:border-yellow-700', icon: Award, label: 'Advanced', gradient: 'from-yellow-400 to-amber-500' };
    case 'C2':
      return { color: 'text-purple-600 dark:text-purple-400', bg: 'bg-purple-100 dark:bg-purple-900/30', border: 'border-purple-300 dark:border-purple-700', icon: Crown, label: 'Mastery', gradient: 'from-purple-500 to-pink-600' };
  }
};

const Certificates = () => {
  const { user, profile } = useAuth();
  const { certificates, loading, fetchUserCertificates } = useCertificates(user?.id);
  const { progress, loading: progressLoading, fetchProgress } = useQuestionProgress(user?.id);
  const [selectedCertificate, setSelectedCertificate] = useState<Certificate | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const studentName = profile?.display_name || profile?.username || 'Student';

  useEffect(() => {
    if (user) {
      fetchUserCertificates();
      fetchProgress();
    }
  }, [user, fetchUserCertificates, fetchProgress]);

  const handleView = (certificate: Certificate) => {
    setSelectedCertificate(certificate);
    setPreviewOpen(true);
  };

  const handleDownload = async (certificate: Certificate) => {
    setSelectedCertificate(certificate);
    
    setTimeout(async () => {
      const element = document.getElementById('certificate-download-template');
      if (!element) return;

      try {
        const canvas = await html2canvas(element, {
          scale: 2,
          useCORS: true,
          allowTaint: true,
          backgroundColor: '#FDF8E8',
        });

        const imgData = canvas.toDataURL('image/png');
        const pdf = new jsPDF({
          orientation: 'landscape',
          unit: 'mm',
          format: 'a4',
        });

        const pdfWidth = pdf.internal.pageSize.getWidth();
        const pdfHeight = pdf.internal.pageSize.getHeight();

        pdf.addImage(imgData, 'PNG', 0, 0, pdfWidth, pdfHeight);
        pdf.save(`certificate-${certificate.certificate_number}.pdf`);
      } catch (error) {
        console.error('Error generating PDF:', error);
      }
    }, 100);
  };

  const earnedCertificates = certificates.filter(c => c.status === 'earned' && c.certificate_type === 'level_completion');
  const verifiedCount = certificates.filter(c => c.verified_by_admin).length;

  const getCertificateForLevel = (level: CEFRLevel) => 
    earnedCertificates.find(c => c.cefr_level === level);
  
  const getProgressForLevel = (level: CEFRLevel) => 
    progress.find(p => p.cefr === level);

  const isLevelLocked = (level: CEFRLevel, index: number): boolean => {
    if (index === 0) return false;
    const prevLevel = CEFR_LEVELS[index - 1];
    return !earnedCertificates.some(c => c.cefr_level === prevLevel);
  };

  if (loading || progressLoading) {
    return <LoadingSpinner fullScreen />;
  }

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />

      <main className="container py-4 md:py-8 px-4">
        <div className="max-w-4xl mx-auto">
          {/* Header */}
          <div className="text-center mb-6 md:mb-8">
            <div className="inline-flex items-center justify-center w-12 h-12 md:w-16 md:h-16 rounded-full bg-primary/10 mb-3 md:mb-4">
              <Award className="w-6 h-6 md:w-8 md:h-8 text-primary" />
            </div>
            <h1 className="font-serif text-2xl md:text-3xl font-bold mb-2">My Certificates</h1>
            <p className="text-sm md:text-base text-muted-foreground">
              Complete all questions in each level with 90%+ accuracy to earn certificates
            </p>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-4 mb-6 md:mb-8">
            <div className="p-4 rounded-lg bg-muted text-center">
              <p className="text-2xl md:text-3xl font-bold text-primary">{earnedCertificates.length}/6</p>
              <p className="text-xs md:text-sm text-muted-foreground">Certificates</p>
            </div>
            <div className="p-4 rounded-lg bg-muted text-center">
              <p className="text-2xl md:text-3xl font-bold text-green-500">{verifiedCount}</p>
              <p className="text-xs md:text-sm text-muted-foreground">Verified</p>
            </div>
            <div className="p-4 rounded-lg bg-muted text-center flex flex-col items-center justify-center">
              <div className="flex items-center gap-1">
                <Target className="w-4 h-4 text-muted-foreground" />
                <p className="text-lg md:text-xl font-bold">{REQUIRED_ACCURACY}%</p>
              </div>
              <p className="text-xs md:text-sm text-muted-foreground">Required Accuracy</p>
            </div>
          </div>

          {/* Certificate Progress Grid */}
          <div className="grid gap-4 mb-8">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <Trophy className="w-5 h-5 text-primary" />
              Certificate Progress
            </h2>
            
            <div className="grid gap-3">
              {CEFR_LEVELS.map((level, index) => {
                const theme = getLevelTheme(level);
                const Icon = theme.icon;
                const cert = getCertificateForLevel(level);
                const levelProgress = getProgressForLevel(level);
                const isLocked = isLevelLocked(level, index);
                
                const isCompleted = levelProgress?.remaining_questions === 0 && (levelProgress?.total_questions || 0) > 0;
                const currentAccuracy = levelProgress?.accuracy || 0;
                const completionPercent = levelProgress ? 
                  Math.round((levelProgress.answered_questions / (levelProgress.total_questions || 1)) * 100) : 0;
                const needsMoreAccuracy = isCompleted && currentAccuracy < REQUIRED_ACCURACY;

                return (
                  <Card key={level} className={`${isLocked ? 'opacity-60' : ''}`}>
                    <CardContent className="p-4">
                      <div className="flex items-start gap-4">
                        {/* Level Badge */}
                        <div className={`w-14 h-14 rounded-xl ${theme.bg} ${theme.border} border-2 flex flex-col items-center justify-center flex-shrink-0`}>
                          {cert ? (
                            <CheckCircle2 className="w-6 h-6 text-green-600" />
                          ) : isLocked ? (
                            <Lock className="w-5 h-5 text-muted-foreground" />
                          ) : (
                            <Icon className={`w-6 h-6 ${theme.color}`} />
                          )}
                        </div>

                        {/* Content */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <CEFRBadge level={level} size="sm" />
                            <span className={`text-sm font-medium ${theme.color}`}>{theme.label}</span>
                            {cert && (
                              <span className="text-xs bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-400 px-2 py-0.5 rounded-full">
                                Earned!
                              </span>
                            )}
                            {needsMoreAccuracy && (
                              <span className="text-xs bg-amber-100 dark:bg-amber-900/50 text-amber-700 dark:text-amber-400 px-2 py-0.5 rounded-full flex items-center gap-1">
                                <RotateCcw className="w-3 h-3" />
                                Retry
                              </span>
                            )}
                          </div>

                          {isLocked ? (
                            <p className="text-sm text-muted-foreground">Complete {CEFR_LEVELS[index - 1]} first</p>
                          ) : cert ? (
                            <div className="space-y-2">
                              <p className="text-sm text-muted-foreground">
                                Score: <span className="font-semibold text-green-600">{Math.round(cert.score_percentage)}%</span> • 
                                {cert.total_correct}/{cert.total_questions_answered} correct
                              </p>
                              <div className="flex gap-2">
                                <Button size="sm" variant="outline" onClick={() => handleView(cert)}>
                                  View
                                </Button>
                                <Button size="sm" variant="ghost" onClick={() => handleDownload(cert)}>
                                  Download PDF
                                </Button>
                              </div>
                            </div>
                          ) : (
                            <div className="space-y-2">
                              <div className="flex justify-between text-xs text-muted-foreground">
                                <span>{levelProgress?.answered_questions || 0}/{levelProgress?.total_questions || 0} questions</span>
                                <span>{currentAccuracy.toFixed(1)}% accuracy</span>
                              </div>
                              <Progress value={completionPercent} className="h-2" />
                              
                              {needsMoreAccuracy ? (
                                <p className="text-xs text-amber-600 dark:text-amber-400">
                                  You need {REQUIRED_ACCURACY}% accuracy. Retry wrong questions to improve!
                                </p>
                              ) : levelProgress?.remaining_questions ? (
                                <p className="text-xs text-muted-foreground">
                                  {levelProgress.remaining_questions} questions remaining
                                </p>
                              ) : null}
                              
                              <Link to="/practice">
                                <Button size="sm" variant="default" className={`bg-gradient-to-r ${theme.gradient} text-white`}>
                                  {needsMoreAccuracy ? 'Retry Questions' : 'Practice Now'}
                                </Button>
                              </Link>
                            </div>
                          )}
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                );
              })}
            </div>
          </div>

          {/* Earned Certificates List */}
          {earnedCertificates.length > 0 && (
            <>
              <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
                <Award className="w-5 h-5 text-primary" />
                Your Certificates
              </h2>
              <div className="space-y-4">
                {earnedCertificates.map((certificate) => (
                  <CertificateCard
                    key={certificate.id}
                    certificate={certificate}
                    onView={() => handleView(certificate)}
                    onDownload={() => handleDownload(certificate)}
                  />
                ))}
              </div>
            </>
          )}
        </div>
      </main>

      {/* Preview Modal */}
      <CertificatePreviewModal
        certificate={selectedCertificate}
        studentName={studentName}
        open={previewOpen}
        onClose={() => setPreviewOpen(false)}
      />

      {/* Hidden Certificate for PDF Generation */}
      {selectedCertificate && (
        <div className="fixed -left-[9999px] top-0">
          <div id="certificate-download-template">
            <CertificateTemplate
              certificate={selectedCertificate}
              studentName={studentName}
              verificationUrl={`${window.location.origin}/verify/${selectedCertificate.certificate_number}`}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default Certificates;
