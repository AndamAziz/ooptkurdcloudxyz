import { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { CheckCircle2, XCircle, Award, Calendar, BookOpen, Search } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { CEFRBadge } from '@/components/CEFRBadge';
import { LoadingSpinner } from '@/components/shared/LoadingSpinner';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import { format } from 'date-fns';
import type { CEFRLevel } from '@/lib/constants';

const maskName = (name: string | null): string => {
  if (!name) return 'Anonymous';
  if (name.length <= 3) return name[0] + '***';
  const firstPart = name.slice(0, 3);
  const restLength = name.length - 3;
  return firstPart + '*'.repeat(Math.min(restLength, 5));
};

const VerifyCertificate = () => {
  const { certificateNumber } = useParams<{ certificateNumber?: string }>();
  const { getCertificateByNumber } = useCertificates();
  const [certificate, setCertificate] = useState<Certificate | null>(null);
  const [loading, setLoading] = useState(false);
  const [searched, setSearched] = useState(false);
  const [searchInput, setSearchInput] = useState(certificateNumber || '');

  useEffect(() => {
    if (certificateNumber) {
      handleSearch(certificateNumber);
    }
  }, [certificateNumber]);

  const handleSearch = async (number: string) => {
    if (!number.trim()) return;
    
    setLoading(true);
    setSearched(true);
    const result = await getCertificateByNumber(number.trim());
    setCertificate(result);
    setLoading(false);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleSearch(searchInput);
  };

  const isValid = certificate && certificate.status !== 'revoked';
  const studentName = certificate?.profiles?.display_name || certificate?.profiles?.username || null;

  return (
    <div className="min-h-screen bg-gradient-to-br from-primary/5 via-background to-accent/5 flex items-center justify-center px-4 py-8">
      <div className="w-full max-w-lg">
        {/* Logo */}
        <div className="flex justify-center mb-6">
          <Link to="/" className="flex items-center gap-2">
            <div className="flex items-center justify-center w-10 h-10 rounded-lg bg-primary">
              <BookOpen className="w-6 h-6 text-primary-foreground" />
            </div>
            <span className="font-serif text-xl font-bold">CEFR Practice</span>
          </Link>
        </div>

        <Card className="shadow-lg">
          <CardHeader className="text-center">
            <CardTitle className="flex flex-col items-center gap-3">
              <Award className="w-10 h-10 text-primary" />
              <span>Certificate Verification</span>
            </CardTitle>
          </CardHeader>

          <CardContent className="space-y-6">
            {/* Search Form */}
            <form onSubmit={handleSubmit} className="flex gap-2">
              <Input
                placeholder="Enter certificate number"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
                className="min-h-[44px]"
              />
              <Button type="submit" disabled={loading} className="min-h-[44px]">
                <Search className="w-4 h-4" />
              </Button>
            </form>

            {/* Loading */}
            {loading && (
              <div className="flex justify-center py-8">
                <LoadingSpinner />
              </div>
            )}

            {/* Results */}
            {!loading && searched && (
              <div className="animate-fade-in">
                {isValid ? (
                  <div className="text-center space-y-4">
                    <div className="w-16 h-16 mx-auto rounded-full bg-green-100 flex items-center justify-center">
                      <CheckCircle2 className="w-10 h-10 text-green-600" />
                    </div>
                    
                    <div>
                      <h3 className="text-xl font-semibold text-green-600">Valid Certificate</h3>
                      <p className="text-sm text-muted-foreground">
                        This certificate has been verified
                      </p>
                    </div>

                    <div className="p-4 rounded-lg bg-muted space-y-3 text-left">
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">Certificate Holder</span>
                        <span className="font-medium">{maskName(studentName)}</span>
                      </div>
                      
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">Level Achieved</span>
                        <CEFRBadge level={certificate.cefr_level as CEFRLevel} size="sm" />
                      </div>
                      
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">Score</span>
                        <span className="font-medium text-green-600">{certificate.score_percentage}%</span>
                      </div>
                      
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">Date Issued</span>
                        <span className="font-medium flex items-center gap-1">
                          <Calendar className="w-4 h-4" />
                          {format(new Date(certificate.issued_at), 'MMM d, yyyy')}
                        </span>
                      </div>
                      
                      <div className="flex justify-between items-center">
                        <span className="text-sm text-muted-foreground">Certificate No.</span>
                        <span className="font-mono text-xs">{certificate.certificate_number}</span>
                      </div>

                      {certificate.verified_by_admin && (
                        <div className="flex justify-between items-center pt-2 border-t">
                          <span className="text-sm text-muted-foreground">Admin Verified</span>
                          <span className="flex items-center gap-1 text-green-600 text-sm font-medium">
                            <CheckCircle2 className="w-4 h-4" />
                            Yes
                          </span>
                        </div>
                      )}
                    </div>

                    <p className="text-xs text-muted-foreground">
                      Verified on {format(new Date(), 'MMMM d, yyyy \'at\' h:mm a')}
                    </p>
                  </div>
                ) : (
                  <div className="text-center space-y-4">
                    <div className="w-16 h-16 mx-auto rounded-full bg-red-100 flex items-center justify-center">
                      <XCircle className="w-10 h-10 text-red-600" />
                    </div>
                    
                    <div>
                      <h3 className="text-xl font-semibold text-red-600">
                        {certificate?.status === 'revoked' ? 'Certificate Revoked' : 'Invalid Certificate'}
                      </h3>
                      <p className="text-sm text-muted-foreground">
                        {certificate?.status === 'revoked' 
                          ? 'This certificate has been revoked and is no longer valid.'
                          : 'No certificate found with this number.'}
                      </p>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* Initial state */}
            {!loading && !searched && (
              <div className="text-center py-4">
                <p className="text-sm text-muted-foreground">
                  Enter a certificate number to verify its authenticity
                </p>
              </div>
            )}
          </CardContent>
        </Card>

        <p className="text-center text-xs text-muted-foreground mt-4">
          © {new Date().getFullYear()} CEFR Practice
        </p>
      </div>
    </div>
  );
};

export default VerifyCertificate;
