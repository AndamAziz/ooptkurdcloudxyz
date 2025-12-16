import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Award, ArrowRight, Download } from 'lucide-react';
import confetti from 'canvas-confetti';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import type { Certificate } from '@/hooks/useCertificates';
import type { CEFRLevel } from '@/lib/constants';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';
import { CertificateTemplate } from './CertificateTemplate';

interface CertificateEarnedModalProps {
  certificate: Certificate | null;
  studentName: string;
  open: boolean;
  onClose: () => void;
}

export const CertificateEarnedModal = ({
  certificate,
  studentName,
  open,
  onClose,
}: CertificateEarnedModalProps) => {
  const navigate = useNavigate();

  useEffect(() => {
    if (open && certificate) {
      const duration = 3000;
      const end = Date.now() + duration;
      const colors = ['#D4AF37', '#1E3A5F', '#2E7D32', '#FFD700'];

      (function frame() {
        confetti({
          particleCount: 3,
          angle: 60,
          spread: 55,
          origin: { x: 0 },
          colors,
        });
        confetti({
          particleCount: 3,
          angle: 120,
          spread: 55,
          origin: { x: 1 },
          colors,
        });

        if (Date.now() < end) {
          requestAnimationFrame(frame);
        }
      })();
    }
  }, [open, certificate]);

  if (!certificate) return null;

  const verificationUrl = `${window.location.origin}/verify/${certificate.certificate_number}`;

  const handleDownloadPDF = async () => {
    const element = document.getElementById('certificate-earned-template');
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
  };

  const handleViewCertificates = () => {
    onClose();
    navigate('/certificates');
  };

  const handleContinue = () => {
    onClose();
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="text-center">
            <div className="flex flex-col items-center gap-4">
              <div className="w-20 h-20 rounded-full bg-gradient-to-br from-yellow-400 to-amber-500 flex items-center justify-center animate-pulse">
                <Award className="w-10 h-10 text-white" />
              </div>
              <span className="text-2xl md:text-3xl font-serif">
                Congratulations! 🎉
              </span>
            </div>
          </DialogTitle>
        </DialogHeader>

        <div className="text-center space-y-4 py-4">
          <p className="text-muted-foreground">
            You've earned a new certificate!
          </p>

          <div className="p-4 rounded-lg bg-muted">
            <div className="flex items-center justify-center gap-3 mb-2">
              <CEFRBadge level={certificate.cefr_level as CEFRLevel} size="lg" />
              <span className="font-semibold text-lg">Level Complete</span>
            </div>
            <p className="text-sm text-muted-foreground">
              Score: {certificate.score_percentage}% • {certificate.total_correct}/{certificate.total_questions_answered} correct
            </p>
          </div>

          <p className="text-sm text-muted-foreground">
            Certificate No: {certificate.certificate_number}
          </p>
        </div>

        <div className="flex flex-col sm:flex-row gap-3">
          <Button
            variant="outline"
            className="flex-1 min-h-[48px]"
            onClick={handleDownloadPDF}
          >
            <Download className="w-4 h-4 mr-2" />
            Download
          </Button>
          <Button
            variant="outline"
            className="flex-1 min-h-[48px]"
            onClick={handleViewCertificates}
          >
            View All
          </Button>
          <Button
            className="flex-1 min-h-[48px]"
            onClick={handleContinue}
          >
            Continue
            <ArrowRight className="w-4 h-4 ml-2" />
          </Button>
        </div>

        {/* Hidden certificate for PDF generation */}
        <div className="fixed -left-[9999px] top-0">
          <div id="certificate-earned-template">
            <CertificateTemplate
              certificate={certificate}
              studentName={studentName}
              verificationUrl={verificationUrl}
            />
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};
