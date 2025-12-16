import { useEffect, useState } from 'react';
import { Award, FileText } from 'lucide-react';
import { Header } from '@/components/Header';
import { CertificateCard } from '@/components/certificates/CertificateCard';
import { CertificatePreviewModal } from '@/components/certificates/CertificatePreviewModal';
import { CertificateTemplate } from '@/components/certificates/CertificateTemplate';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { useAuth } from '@/contexts/AuthContext';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

const Certificates = () => {
  const { user, profile } = useAuth();
  const { certificates, loading, fetchUserCertificates } = useCertificates(user?.id);
  const [selectedCertificate, setSelectedCertificate] = useState<Certificate | null>(null);
  const [previewOpen, setPreviewOpen] = useState(false);

  const studentName = profile?.display_name || profile?.username || 'Student';

  useEffect(() => {
    if (user) {
      fetchUserCertificates();
    }
  }, [user, fetchUserCertificates]);

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

  const earnedCertificates = certificates.filter(c => c.status === 'earned');
  const verifiedCount = certificates.filter(c => c.verified_by_admin).length;

  if (loading) {
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
              Your achievements and credentials
            </p>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-2 md:grid-cols-3 gap-4 mb-6 md:mb-8">
            <div className="p-4 rounded-lg bg-muted text-center">
              <p className="text-2xl md:text-3xl font-bold text-primary">{earnedCertificates.length}</p>
              <p className="text-xs md:text-sm text-muted-foreground">Total Certificates</p>
            </div>
            <div className="p-4 rounded-lg bg-muted text-center">
              <p className="text-2xl md:text-3xl font-bold text-green-500">{verifiedCount}</p>
              <p className="text-xs md:text-sm text-muted-foreground">Verified</p>
            </div>
            <div className="hidden md:block p-4 rounded-lg bg-muted text-center">
              <p className="text-2xl md:text-3xl font-bold">
                {certificates.filter(c => c.certificate_type === 'excellence').length}
              </p>
              <p className="text-xs md:text-sm text-muted-foreground">Excellence Awards</p>
            </div>
          </div>

          {/* Certificates List */}
          {certificates.length === 0 ? (
            <div className="text-center py-12">
              <FileText className="w-16 h-16 mx-auto mb-4 text-muted-foreground/50" />
              <h3 className="font-semibold text-lg mb-2">No certificates yet</h3>
              <p className="text-muted-foreground text-sm max-w-md mx-auto">
                Complete levels and practice sessions to earn certificates. 
                Certificates are awarded when you reach XP thresholds or achieve excellence scores.
              </p>
            </div>
          ) : (
            <div className="space-y-4">
              {certificates.map((certificate) => (
                <CertificateCard
                  key={certificate.id}
                  certificate={certificate}
                  onView={() => handleView(certificate)}
                  onDownload={() => handleDownload(certificate)}
                />
              ))}
            </div>
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
