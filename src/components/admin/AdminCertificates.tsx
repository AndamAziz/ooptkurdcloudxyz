import { useEffect, useState, useRef } from 'react';
import { 
  Search, 
  CheckCircle2, 
  XCircle, 
  Printer, 
  Eye,
  Award,
  Clock,
  AlertTriangle,
  Filter,
  ChevronDown
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { CEFRBadge } from '@/components/CEFRBadge';
import { CertificateTemplate } from '@/components/certificates/CertificateTemplate';
import { useToast } from '@/hooks/use-toast';
import { useCertificates, type Certificate } from '@/hooks/useCertificates';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import { format } from 'date-fns';
import html2canvas from 'html2canvas';
import { jsPDF } from 'jspdf';

export const AdminCertificates = () => {
  const { toast } = useToast();
  const { 
    certificates, 
    loading, 
    fetchAllCertificates, 
    verifyCertificate, 
    revokeCertificate 
  } = useCertificates();
  
  const [searchQuery, setSearchQuery] = useState('');
  const [filterLevel, setFilterLevel] = useState<string>('all');
  const [filterType, setFilterType] = useState<string>('all');
  const [filterStatus, setFilterStatus] = useState<string>('all');
  const [selectedCerts, setSelectedCerts] = useState<Set<string>>(new Set());
  const [verifyDialogOpen, setVerifyDialogOpen] = useState(false);
  const [revokeDialogOpen, setRevokeDialogOpen] = useState(false);
  const [selectedCertificate, setSelectedCertificate] = useState<Certificate | null>(null);
  const [adminNotes, setAdminNotes] = useState('');
  const [revokeReason, setRevokeReason] = useState('');
  const [previewCertificate, setPreviewCertificate] = useState<Certificate | null>(null);
  const printRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchAllCertificates();
  }, [fetchAllCertificates]);

  const filteredCertificates = certificates.filter(cert => {
    const matchesSearch = 
      cert.certificate_number.toLowerCase().includes(searchQuery.toLowerCase()) ||
      cert.profiles?.display_name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
      cert.profiles?.username?.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesLevel = filterLevel === 'all' || cert.cefr_level === filterLevel;
    const matchesType = filterType === 'all' || cert.certificate_type === filterType;
    const matchesStatus = 
      filterStatus === 'all' || 
      (filterStatus === 'verified' && cert.verified_by_admin) ||
      (filterStatus === 'pending' && !cert.verified_by_admin && cert.status !== 'revoked') ||
      (filterStatus === 'revoked' && cert.status === 'revoked');
    return matchesSearch && matchesLevel && matchesType && matchesStatus;
  });

  const handleSelectAll = (checked: boolean) => {
    if (checked) {
      setSelectedCerts(new Set(filteredCertificates.map(c => c.id)));
    } else {
      setSelectedCerts(new Set());
    }
  };

  const handleSelectCert = (certId: string, checked: boolean) => {
    const newSelected = new Set(selectedCerts);
    if (checked) {
      newSelected.add(certId);
    } else {
      newSelected.delete(certId);
    }
    setSelectedCerts(newSelected);
  };

  const handleVerify = async () => {
    if (!selectedCertificate) return;
    
    const success = await verifyCertificate(selectedCertificate.id, adminNotes);
    if (success) {
      toast({ title: 'Certificate verified successfully' });
      fetchAllCertificates();
    } else {
      toast({ title: 'Failed to verify certificate', variant: 'destructive' });
    }
    setVerifyDialogOpen(false);
    setAdminNotes('');
    setSelectedCertificate(null);
  };

  const handleRevoke = async () => {
    if (!selectedCertificate || !revokeReason.trim()) return;
    
    const success = await revokeCertificate(selectedCertificate.id, revokeReason);
    if (success) {
      toast({ title: 'Certificate revoked' });
      fetchAllCertificates();
    } else {
      toast({ title: 'Failed to revoke certificate', variant: 'destructive' });
    }
    setRevokeDialogOpen(false);
    setRevokeReason('');
    setSelectedCertificate(null);
  };

  const generatePDF = async (cert: Certificate) => {
    setPreviewCertificate(cert);
    
    // Wait for render
    await new Promise(resolve => setTimeout(resolve, 200));
    
    const element = document.getElementById('admin-print-certificate');
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
      pdf.save(`certificate-${cert.certificate_number}.pdf`);
    } catch (error) {
      console.error('Error generating PDF:', error);
      toast({ title: 'Failed to generate PDF', variant: 'destructive' });
    } finally {
      setPreviewCertificate(null);
    }
  };

  const handleBulkPrint = async () => {
    const selectedList = filteredCertificates.filter(c => selectedCerts.has(c.id));
    if (selectedList.length === 0) {
      toast({ title: 'No certificates selected', variant: 'destructive' });
      return;
    }

    toast({ title: `Generating ${selectedList.length} PDFs...` });

    for (const cert of selectedList) {
      await generatePDF(cert);
      await new Promise(resolve => setTimeout(resolve, 500)); // Delay between PDFs
    }

    toast({ title: `Generated ${selectedList.length} certificates` });
    setSelectedCerts(new Set());
  };

  const getStatusBadge = (cert: Certificate) => {
    if (cert.status === 'revoked') {
      return (
        <Badge variant="destructive" className="gap-1">
          <AlertTriangle className="w-3 h-3" />
          Revoked
        </Badge>
      );
    }
    if (cert.verified_by_admin) {
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

  const getCertTypeLabel = (type: string) => {
    switch (type) {
      case 'level_completion': return 'Level';
      case 'course_completion': return 'Course';
      case 'excellence': return 'Excellence';
      default: return type;
    }
  };

  const stats = {
    total: certificates.length,
    verified: certificates.filter(c => c.verified_by_admin).length,
    pending: certificates.filter(c => !c.verified_by_admin && c.status !== 'revoked').length,
    revoked: certificates.filter(c => c.status === 'revoked').length,
  };

  return (
    <div className="space-y-6">
      {/* Stats */}
      <div className="grid gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="p-4 flex items-center gap-4">
            <Award className="w-8 h-8 text-primary" />
            <div>
              <p className="text-2xl font-bold">{stats.total}</p>
              <p className="text-sm text-muted-foreground">Total Certificates</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-4">
            <CheckCircle2 className="w-8 h-8 text-green-500" />
            <div>
              <p className="text-2xl font-bold">{stats.verified}</p>
              <p className="text-sm text-muted-foreground">Verified</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-4">
            <Clock className="w-8 h-8 text-yellow-500" />
            <div>
              <p className="text-2xl font-bold">{stats.pending}</p>
              <p className="text-sm text-muted-foreground">Pending</p>
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="p-4 flex items-center gap-4">
            <XCircle className="w-8 h-8 text-destructive" />
            <div>
              <p className="text-2xl font-bold">{stats.revoked}</p>
              <p className="text-sm text-muted-foreground">Revoked</p>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Certificates Table */}
      <Card>
        <CardHeader>
          <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div>
              <CardTitle>Certificate Management</CardTitle>
              <CardDescription>Verify, print, and manage student certificates</CardDescription>
            </div>
            {selectedCerts.size > 0 && (
              <Button onClick={handleBulkPrint}>
                <Printer className="w-4 h-4 mr-2" />
                Print Selected ({selectedCerts.size})
              </Button>
            )}
          </div>
        </CardHeader>
        <CardContent>
          {/* Filters */}
          <div className="flex flex-wrap gap-3 mb-6">
            <div className="flex-1 min-w-[200px] relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
              <Input
                placeholder="Search by name or certificate number..."
                className="pl-10"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
              />
            </div>
            <Select value={filterLevel} onValueChange={setFilterLevel}>
              <SelectTrigger className="w-28">
                <SelectValue placeholder="Level" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Levels</SelectItem>
                {CEFR_LEVELS.map(level => (
                  <SelectItem key={level} value={level}>{level}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={filterType} onValueChange={setFilterType}>
              <SelectTrigger className="w-32">
                <SelectValue placeholder="Type" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Types</SelectItem>
                <SelectItem value="level_completion">Level</SelectItem>
                <SelectItem value="course_completion">Course</SelectItem>
                <SelectItem value="excellence">Excellence</SelectItem>
              </SelectContent>
            </Select>
            <Select value={filterStatus} onValueChange={setFilterStatus}>
              <SelectTrigger className="w-32">
                <SelectValue placeholder="Status" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All Status</SelectItem>
                <SelectItem value="verified">Verified</SelectItem>
                <SelectItem value="pending">Pending</SelectItem>
                <SelectItem value="revoked">Revoked</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Table */}
          <div className="rounded-md border overflow-x-auto">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-12">
                    <Checkbox
                      checked={selectedCerts.size === filteredCertificates.length && filteredCertificates.length > 0}
                      onCheckedChange={handleSelectAll}
                    />
                  </TableHead>
                  <TableHead>Student</TableHead>
                  <TableHead>Certificate</TableHead>
                  <TableHead>Level</TableHead>
                  <TableHead>Score</TableHead>
                  <TableHead>Date</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-32">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {loading ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                      Loading certificates...
                    </TableCell>
                  </TableRow>
                ) : filteredCertificates.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={8} className="text-center py-8 text-muted-foreground">
                      No certificates found
                    </TableCell>
                  </TableRow>
                ) : (
                  filteredCertificates.map((cert) => (
                    <TableRow key={cert.id} className={cert.status === 'revoked' ? 'opacity-60' : ''}>
                      <TableCell>
                        <Checkbox
                          checked={selectedCerts.has(cert.id)}
                          onCheckedChange={(checked) => handleSelectCert(cert.id, checked as boolean)}
                          disabled={cert.status === 'revoked'}
                        />
                      </TableCell>
                      <TableCell>
                        <div>
                          <p className="font-medium">
                            {cert.profiles?.display_name || cert.profiles?.username || 'Unknown'}
                          </p>
                          <p className="text-xs text-muted-foreground">{cert.user_id.slice(0, 8)}...</p>
                        </div>
                      </TableCell>
                      <TableCell>
                        <div>
                          <p className="font-mono text-xs">{cert.certificate_number}</p>
                          <Badge variant="outline" className="mt-1">
                            {getCertTypeLabel(cert.certificate_type)}
                          </Badge>
                        </div>
                      </TableCell>
                      <TableCell>
                        <CEFRBadge level={cert.cefr_level as CEFRLevel} size="sm" />
                      </TableCell>
                      <TableCell>
                        <span className="font-semibold text-green-600">{cert.score_percentage}%</span>
                      </TableCell>
                      <TableCell className="text-sm">
                        {format(new Date(cert.issued_at), 'MMM d, yyyy')}
                      </TableCell>
                      <TableCell>{getStatusBadge(cert)}</TableCell>
                      <TableCell>
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="sm">
                              Actions
                              <ChevronDown className="w-4 h-4 ml-1" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end">
                            <DropdownMenuItem onClick={() => generatePDF(cert)}>
                              <Printer className="w-4 h-4 mr-2" />
                              Print
                            </DropdownMenuItem>
                            {!cert.verified_by_admin && cert.status !== 'revoked' && (
                              <DropdownMenuItem onClick={() => {
                                setSelectedCertificate(cert);
                                setVerifyDialogOpen(true);
                              }}>
                                <CheckCircle2 className="w-4 h-4 mr-2" />
                                Verify
                              </DropdownMenuItem>
                            )}
                            {cert.status !== 'revoked' && (
                              <DropdownMenuItem 
                                onClick={() => {
                                  setSelectedCertificate(cert);
                                  setRevokeDialogOpen(true);
                                }}
                                className="text-destructive"
                              >
                                <XCircle className="w-4 h-4 mr-2" />
                                Revoke
                              </DropdownMenuItem>
                            )}
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        </CardContent>
      </Card>

      {/* Verify Dialog */}
      <Dialog open={verifyDialogOpen} onOpenChange={setVerifyDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Verify Certificate</DialogTitle>
            <DialogDescription>
              Confirm that this certificate is valid and verified by an administrator.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div>
              <p className="text-sm text-muted-foreground">Certificate Number</p>
              <p className="font-mono">{selectedCertificate?.certificate_number}</p>
            </div>
            <div>
              <Label htmlFor="adminNotes">Admin Notes (optional)</Label>
              <Textarea
                id="adminNotes"
                value={adminNotes}
                onChange={(e) => setAdminNotes(e.target.value)}
                placeholder="Add any notes about this verification..."
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setVerifyDialogOpen(false)}>Cancel</Button>
            <Button onClick={handleVerify}>
              <CheckCircle2 className="w-4 h-4 mr-2" />
              Verify Certificate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Revoke Dialog */}
      <Dialog open={revokeDialogOpen} onOpenChange={setRevokeDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke Certificate</DialogTitle>
            <DialogDescription>
              This action cannot be undone. The certificate will be marked as revoked.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div>
              <p className="text-sm text-muted-foreground">Certificate Number</p>
              <p className="font-mono">{selectedCertificate?.certificate_number}</p>
            </div>
            <div>
              <Label htmlFor="revokeReason">Reason for revocation *</Label>
              <Textarea
                id="revokeReason"
                value={revokeReason}
                onChange={(e) => setRevokeReason(e.target.value)}
                placeholder="Explain why this certificate is being revoked..."
                required
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRevokeDialogOpen(false)}>Cancel</Button>
            <Button variant="destructive" onClick={handleRevoke} disabled={!revokeReason.trim()}>
              <XCircle className="w-4 h-4 mr-2" />
              Revoke Certificate
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Hidden certificate for PDF generation */}
      {previewCertificate && (
        <div className="fixed -left-[9999px] top-0">
          <div id="admin-print-certificate">
            <CertificateTemplate
              certificate={previewCertificate}
              studentName={previewCertificate.profiles?.display_name || previewCertificate.profiles?.username || 'Student'}
              verificationUrl={`${window.location.origin}/verify/${previewCertificate.certificate_number}`}
            />
          </div>
        </div>
      )}
    </div>
  );
};
