import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Award, ArrowRight, CheckCircle2 } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useCertificates } from '@/hooks/useCertificates';
import { format } from 'date-fns';
import type { CEFRLevel } from '@/lib/constants';

interface CertificatesCardProps {
  userId?: string;
}

export const CertificatesCard = ({ userId }: CertificatesCardProps) => {
  const { certificates, fetchUserCertificates } = useCertificates(userId);

  useEffect(() => {
    if (userId) {
      fetchUserCertificates();
    }
  }, [userId, fetchUserCertificates]);

  const earnedCertificates = certificates.filter(c => c.status === 'earned');
  const latestCertificate = earnedCertificates[0];

  return (
    <Card>
      <CardHeader className="p-4 md:p-6 pb-2">
        <CardTitle className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-lg md:text-xl">
            <Award className="w-5 h-5" />
            Certificates
          </div>
          <Link to="/certificates">
            <Button variant="ghost" size="sm" className="text-primary">
              View All
              <ArrowRight className="w-4 h-4 ml-1" />
            </Button>
          </Link>
        </CardTitle>
      </CardHeader>
      <CardContent className="p-4 md:p-6 pt-2">
        {earnedCertificates.length === 0 ? (
          <div className="text-center py-4">
            <Award className="w-10 h-10 mx-auto mb-2 text-muted-foreground/50" />
            <p className="text-sm text-muted-foreground">No certificates yet</p>
            <p className="text-xs text-muted-foreground">Complete levels to earn certificates</p>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex items-center justify-between p-3 rounded-lg bg-muted/50">
              <div className="flex items-center gap-2">
                <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center">
                  <Award className="w-5 h-5 text-primary" />
                </div>
                <div>
                  <p className="font-semibold text-2xl">{earnedCertificates.length}</p>
                  <p className="text-xs text-muted-foreground">Total Earned</p>
                </div>
              </div>
              <div className="text-right">
                <p className="font-semibold text-green-500 flex items-center gap-1">
                  <CheckCircle2 className="w-4 h-4" />
                  {certificates.filter(c => c.verified_by_admin).length}
                </p>
                <p className="text-xs text-muted-foreground">Verified</p>
              </div>
            </div>

            {latestCertificate && (
              <div className="p-3 rounded-lg border">
                <p className="text-xs text-muted-foreground mb-2">Latest Certificate</p>
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <CEFRBadge level={latestCertificate.cefr_level as CEFRLevel} size="sm" />
                    <span className="text-sm font-medium">
                      {latestCertificate.score_percentage}% Score
                    </span>
                  </div>
                  <span className="text-xs text-muted-foreground">
                    {format(new Date(latestCertificate.issued_at), 'MMM d')}
                  </span>
                </div>
              </div>
            )}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
