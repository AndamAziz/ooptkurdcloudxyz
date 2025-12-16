import { Award, Download, Eye, CheckCircle2, Clock, AlertTriangle } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { CEFRBadge } from '@/components/CEFRBadge';
import { format } from 'date-fns';
import type { Certificate } from '@/hooks/useCertificates';
import type { CEFRLevel } from '@/lib/constants';

interface CertificateCardProps {
  certificate: Certificate;
  onView: () => void;
  onDownload: () => void;
}

const getCertificateIcon = (type: string) => {
  switch (type) {
    case 'level_completion':
      return <Award className="w-8 h-8 text-primary" />;
    case 'course_completion':
      return <Award className="w-8 h-8 text-yellow-500" />;
    case 'excellence':
      return <Award className="w-8 h-8 text-amber-500" />;
    default:
      return <Award className="w-8 h-8" />;
  }
};

const getCertificateLabel = (type: string) => {
  switch (type) {
    case 'level_completion':
      return 'Level Completion';
    case 'course_completion':
      return 'Course Completion';
    case 'excellence':
      return 'Excellence Award';
    default:
      return 'Certificate';
  }
};

const getStatusBadge = (certificate: Certificate) => {
  if (certificate.status === 'revoked') {
    return (
      <Badge variant="destructive" className="gap-1">
        <AlertTriangle className="w-3 h-3" />
        Revoked
      </Badge>
    );
  }
  if (certificate.verified_by_admin) {
    return (
      <Badge className="gap-1 bg-green-500 hover:bg-green-600">
        <CheckCircle2 className="w-3 h-3" />
        Verified
      </Badge>
    );
  }
  return (
    <Badge variant="secondary" className="gap-1">
      <Clock className="w-3 h-3" />
      Pending
    </Badge>
  );
};

export const CertificateCard = ({ certificate, onView, onDownload }: CertificateCardProps) => {
  const isRevoked = certificate.status === 'revoked';

  return (
    <Card className={`card-hover ${isRevoked ? 'opacity-60' : ''}`}>
      <CardContent className="p-4 md:p-6">
        <div className="flex flex-col sm:flex-row items-start gap-4">
          <div className="flex-shrink-0 w-14 h-14 rounded-xl bg-muted flex items-center justify-center">
            {getCertificateIcon(certificate.certificate_type)}
          </div>
          
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap mb-1">
              <h3 className="font-semibold text-sm md:text-base">
                {getCertificateLabel(certificate.certificate_type)}
              </h3>
              {getStatusBadge(certificate)}
            </div>
            
            <div className="flex items-center gap-2 mb-2">
              <CEFRBadge level={certificate.cefr_level as CEFRLevel} size="sm" />
              <span className="text-xs md:text-sm text-muted-foreground">
                {format(new Date(certificate.issued_at), 'MMM d, yyyy')}
              </span>
            </div>
            
            <div className="flex items-center gap-4 text-xs md:text-sm text-muted-foreground">
              <span>Score: <strong className="text-foreground">{certificate.score_percentage}%</strong></span>
              <span>Questions: <strong className="text-foreground">{certificate.total_questions_answered}</strong></span>
            </div>
            
            <p className="text-xs text-muted-foreground mt-1">
              Certificate No: {certificate.certificate_number}
            </p>
          </div>
          
          <div className="flex flex-row sm:flex-col gap-2 w-full sm:w-auto">
            <Button 
              size="sm" 
              variant="outline" 
              onClick={onView}
              disabled={isRevoked}
              className="min-h-[36px] flex-1 sm:flex-initial"
            >
              <Eye className="w-4 h-4 mr-1" />
              View
            </Button>
            <Button 
              size="sm" 
              onClick={onDownload}
              disabled={isRevoked}
              className="min-h-[36px] flex-1 sm:flex-initial"
            >
              <Download className="w-4 h-4 mr-1" />
              PDF
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
};
