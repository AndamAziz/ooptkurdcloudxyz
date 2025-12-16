import { Download, Printer } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { CertificateTemplate } from './CertificateTemplate';
import type { Certificate } from '@/hooks/useCertificates';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

interface CertificatePreviewModalProps {
  certificate: Certificate | null;
  studentName: string;
  open: boolean;
  onClose: () => void;
}

export const CertificatePreviewModal = ({
  certificate,
  studentName,
  open,
  onClose,
}: CertificatePreviewModalProps) => {
  if (!certificate) return null;

  const verificationUrl = `${window.location.origin}/verify/${certificate.certificate_number}`;

  const handleDownloadPDF = async () => {
    const element = document.getElementById('certificate-template');
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

  const handlePrint = () => {
    window.print();
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-[95vw] max-h-[95vh] overflow-auto p-4 md:p-6">
        <DialogHeader>
          <DialogTitle className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <span>Certificate Preview</span>
            <div className="flex items-center gap-2">
              <Button size="sm" variant="outline" onClick={handlePrint}>
                <Printer className="w-4 h-4 mr-2" />
                Print
              </Button>
              <Button size="sm" onClick={handleDownloadPDF}>
                <Download className="w-4 h-4 mr-2" />
                Download PDF
              </Button>
            </div>
          </DialogTitle>
        </DialogHeader>

        <div className="mt-4 overflow-auto">
          <div className="transform scale-[0.35] sm:scale-[0.4] md:scale-[0.5] lg:scale-[0.6] origin-top-left inline-block">
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
