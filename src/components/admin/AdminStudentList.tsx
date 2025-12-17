import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Search,
  Eye,
  BarChart3,
  Download,
  ChevronLeft,
  ChevronRight,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Ban,
  Settings,
  MoreHorizontal,
  UserCheck,
  Loader2,
  X,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
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
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Skeleton } from '@/components/ui/skeleton';
import { CEFRBadge } from '@/components/CEFRBadge';
import { StudentActionsDialog } from './StudentActionsDialog';
import { useAdminStudents, type StudentData } from '@/hooks/useAdminStudents';
import { useToast } from '@/hooks/use-toast';
import { supabase } from '@/integrations/supabase/client';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';
import { formatDistanceToNow } from 'date-fns';

export const AdminStudentList = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const { students, totalCount, loading, fetchStudents, getActivityStatus } = useAdminStudents();
  
  const [page, setPage] = useState(1);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterLevel, setFilterLevel] = useState('all');
  const [filterStatus, setFilterStatus] = useState('all');
  const [sortBy, setSortBy] = useState('created_at');
  const [selectedStudent, setSelectedStudent] = useState<StudentData | null>(null);
  const [actionsDialogOpen, setActionsDialogOpen] = useState(false);
  const [suspendingUserId, setSuspendingUserId] = useState<string | null>(null);
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  const [bulkLoading, setBulkLoading] = useState(false);
  const pageSize = 20;

  useEffect(() => {
    fetchStudents({
      page,
      pageSize,
      searchQuery,
      filterLevel,
      filterStatus,
      sortBy,
      sortOrder: 'desc',
    });
  }, [fetchStudents, page, searchQuery, filterLevel, filterStatus, sortBy]);

  // Clear selection when filters change
  useEffect(() => {
    setSelectedIds(new Set());
  }, [page, searchQuery, filterLevel, filterStatus, sortBy]);

  const totalPages = Math.ceil(totalCount / pageSize);

  const toggleSelectAll = () => {
    if (selectedIds.size === students.length) {
      setSelectedIds(new Set());
    } else {
      setSelectedIds(new Set(students.map(s => s.user_id)));
    }
  };

  const toggleSelect = (userId: string) => {
    const newSelected = new Set(selectedIds);
    if (newSelected.has(userId)) {
      newSelected.delete(userId);
    } else {
      newSelected.add(userId);
    }
    setSelectedIds(newSelected);
  };

  const isInvalidTokenError = (err: any) => {
    const msg = String(err?.message || '').toLowerCase();
    return msg.includes('invalid token') || msg.includes('session_not_found') || msg.includes('401') || msg.includes('unauthorized');
  };

  const handleAuthExpired = async () => {
    toast({
      title: 'Session Expired',
      description: 'Please log in again to continue.',
      variant: 'destructive',
    });

    await supabase.auth.signOut();
    navigate('/auth', { replace: true });
  };

  const handleBulkAction = async (action: 'suspend' | 'activate') => {
    if (selectedIds.size === 0) return;

    setBulkLoading(true);
    const actionType = action === 'suspend' ? 'suspend_user' : 'activate_user';
    let successCount = 0;
    let failCount = 0;

    for (const userId of selectedIds) {
      try {
        const { error } = await supabase.functions.invoke('admin-user-management', {
          body: { action: actionType, userId },
        });

        if (error) {
          if (isInvalidTokenError(error)) {
            setBulkLoading(false);
            await handleAuthExpired();
            return;
          }
          failCount++;
        } else {
          successCount++;
        }
      } catch (err: any) {
        if (isInvalidTokenError(err)) {
          setBulkLoading(false);
          await handleAuthExpired();
          return;
        }
        failCount++;
      }
    }

    toast({
      title: 'Bulk Action Complete',
      description: `${successCount} ${action === 'suspend' ? 'suspended' : 'activated'} successfully${failCount > 0 ? `, ${failCount} failed` : ''}.`,
      variant: failCount > 0 ? 'destructive' : 'default',
    });

    setSelectedIds(new Set());
    setBulkLoading(false);
    handleActionsSuccess();
  };

  const getStatusBadge = (student: StudentData) => {
    if (student.account_status === 'suspended') {
      return (
        <TooltipProvider>
          <Tooltip>
            <TooltipTrigger asChild>
              <Badge variant="destructive" className="gap-1 cursor-help">
                <Ban className="w-3 h-3" />
                Suspended
              </Badge>
            </TooltipTrigger>
            {student.suspend_reason && (
              <TooltipContent className="max-w-xs">
                <p className="font-medium">Reason:</p>
                <p className="text-sm">{student.suspend_reason}</p>
              </TooltipContent>
            )}
          </Tooltip>
        </TooltipProvider>
      );
    }
    
    const status = getActivityStatus(student.last_active_at);
    switch (status) {
      case 'active':
        return (
          <Badge className="gap-1 bg-green-500 hover:bg-green-600">
            <CheckCircle2 className="w-3 h-3" />
            Active
          </Badge>
        );
      case 'inactive':
        return (
          <Badge variant="secondary" className="gap-1 text-yellow-600">
            <AlertTriangle className="w-3 h-3" />
            Inactive
          </Badge>
        );
      case 'at_risk':
        return (
          <Badge variant="outline" className="gap-1 text-red-500 border-red-300">
            <XCircle className="w-3 h-3" />
            At Risk
          </Badge>
        );
      default:
        return null;
    }
  };

  const handleExport = () => {
    const csvContent = [
      ['Name', 'Level', 'XP', 'Accuracy', 'Attempts', 'Last Active', 'Status'].join(','),
      ...students.map(s => [
        s.display_name || s.username || 'Unknown',
        s.current_cefr,
        s.xp,
        `${s.accuracy}%`,
        s.totalAttempts,
        s.last_active_at ? new Date(s.last_active_at).toLocaleDateString() : 'Never',
        s.account_status,
      ].join(','))
    ].join('\n');

    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `students-export-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleManageStudent = (student: StudentData) => {
    setSelectedStudent(student);
    setActionsDialogOpen(true);
  };

  const handleToggleSuspend = async (student: StudentData) => {
    setSuspendingUserId(student.user_id);
    const action = student.account_status === 'suspended' ? 'activate_user' : 'suspend_user';
    const actionLabel = student.account_status === 'suspended' ? 'activated' : 'suspended';

    try {
      const { error } = await supabase.functions.invoke('admin-user-management', {
        body: {
          action,
          userId: student.user_id,
        },
      });

      if (error) {
        if (isInvalidTokenError(error)) {
          await handleAuthExpired();
          return;
        }
        throw error;
      }

      toast({
        title: 'Status Updated',
        description: `Student has been ${actionLabel} successfully.`,
      });

      handleActionsSuccess();
    } catch (error: any) {
      console.error('Error toggling suspend:', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to update student status.',
        variant: 'destructive',
      });
    } finally {
      setSuspendingUserId(null);
    }
  };

  const handleActionsSuccess = () => {
    fetchStudents({
      page,
      pageSize,
      searchQuery,
      filterLevel,
      filterStatus,
      sortBy,
      sortOrder: 'desc',
    });
  };

  return (
    <>
    <Card>
      <CardHeader>
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
          <div>
            <CardTitle>Student Management</CardTitle>
            <CardDescription>{totalCount} total students</CardDescription>
          </div>
          <Button variant="outline" onClick={handleExport}>
            <Download className="w-4 h-4 mr-2" />
            Export CSV
          </Button>
        </div>
      </CardHeader>
      <CardContent>
        {/* Filters */}
        <div className="flex flex-wrap gap-3 mb-6">
          <div className="flex-1 min-w-[200px] relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
            <Input
              placeholder="Search by name..."
              className="pl-10"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setPage(1);
              }}
            />
          </div>
          <Select value={filterLevel} onValueChange={(v) => { setFilterLevel(v); setPage(1); }}>
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
          <Select value={filterStatus} onValueChange={(v) => { setFilterStatus(v); setPage(1); }}>
            <SelectTrigger className="w-32">
              <SelectValue placeholder="Status" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">All Status</SelectItem>
              <SelectItem value="active">Active</SelectItem>
              <SelectItem value="inactive">Inactive</SelectItem>
              <SelectItem value="at_risk">At Risk</SelectItem>
              <SelectItem value="suspended">Suspended</SelectItem>
            </SelectContent>
          </Select>
          <Select value={sortBy} onValueChange={(v) => { setSortBy(v); setPage(1); }}>
            <SelectTrigger className="w-36">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="created_at">Join Date</SelectItem>
              <SelectItem value="xp">XP</SelectItem>
              <SelectItem value="last_active_at">Last Active</SelectItem>
              <SelectItem value="streak">Streak</SelectItem>
            </SelectContent>
          </Select>
        </div>

        {/* Bulk Actions Bar */}
        {selectedIds.size > 0 && (
          <div className="mb-4 p-3 bg-muted rounded-lg flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <Badge variant="secondary" className="text-sm">
                {selectedIds.size} selected
              </Badge>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setSelectedIds(new Set())}
                className="h-7 px-2"
              >
                <X className="w-3 h-3 mr-1" />
                Clear
              </Button>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleBulkAction('activate')}
                disabled={bulkLoading}
                className="text-green-600 border-green-300 hover:bg-green-50"
              >
                {bulkLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <UserCheck className="w-4 h-4 mr-2" />}
                Activate All
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => handleBulkAction('suspend')}
                disabled={bulkLoading}
                className="text-orange-600 border-orange-300 hover:bg-orange-50"
              >
                {bulkLoading ? <Loader2 className="w-4 h-4 mr-2 animate-spin" /> : <Ban className="w-4 h-4 mr-2" />}
                Suspend All
              </Button>
            </div>
          </div>
        )}

        {/* Table */}
        <div className="rounded-md border overflow-x-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox
                    checked={students.length > 0 && selectedIds.size === students.length}
                    onCheckedChange={toggleSelectAll}
                    aria-label="Select all"
                    disabled={loading || students.length === 0}
                  />
                </TableHead>
                <TableHead>#</TableHead>
                <TableHead>Name</TableHead>
                <TableHead>Level</TableHead>
                <TableHead>XP</TableHead>
                <TableHead>Accuracy</TableHead>
                <TableHead>Attempts</TableHead>
                <TableHead>Last Active</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-24">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                Array.from({ length: 5 }).map((_, i) => (
                  <TableRow key={i}>
                    <TableCell><Skeleton className="h-4 w-4" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-4" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-32" /></TableCell>
                    <TableCell><Skeleton className="h-6 w-12" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-16" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-12" /></TableCell>
                    <TableCell><Skeleton className="h-4 w-24" /></TableCell>
                    <TableCell><Skeleton className="h-6 w-16" /></TableCell>
                    <TableCell><Skeleton className="h-8 w-16" /></TableCell>
                  </TableRow>
                ))
              ) : students.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={10} className="text-center py-8 text-muted-foreground">
                    No students found
                  </TableCell>
                </TableRow>
              ) : (
                students.map((student, index) => (
                  <TableRow 
                    key={student.id}
                    className={`${student.account_status === 'suspended' ? 'opacity-60' : ''} ${selectedIds.has(student.user_id) ? 'bg-muted/50' : ''}`}
                  >
                    <TableCell>
                      <Checkbox
                        checked={selectedIds.has(student.user_id)}
                        onCheckedChange={() => toggleSelect(student.user_id)}
                        aria-label={`Select ${student.display_name || student.username}`}
                      />
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {(page - 1) * pageSize + index + 1}
                    </TableCell>
                    <TableCell>
                      <div>
                        <p className="font-medium">
                          {student.display_name || student.username || 'Unknown'}
                        </p>
                        <p className="text-xs text-muted-foreground">
                          {student.user_id.slice(0, 8)}...
                        </p>
                      </div>
                    </TableCell>
                    <TableCell>
                      <CEFRBadge level={student.current_cefr} size="sm" />
                    </TableCell>
                    <TableCell className="font-semibold">
                      {student.xp.toLocaleString()}
                    </TableCell>
                    <TableCell>
                      <span className={
                        student.accuracy >= 70 ? 'text-green-600' :
                        student.accuracy >= 50 ? 'text-yellow-600' : 'text-red-500'
                      }>
                        {student.accuracy}%
                      </span>
                    </TableCell>
                    <TableCell>{student.totalAttempts}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {student.last_active_at
                        ? formatDistanceToNow(new Date(student.last_active_at), { addSuffix: true })
                        : 'Never'
                      }
                    </TableCell>
                    <TableCell>{getStatusBadge(student)}</TableCell>
                    <TableCell>
                      {/* Desktop Actions */}
                      <TooltipProvider>
                        <div className="hidden sm:flex gap-1">
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleToggleSuspend(student)}
                                disabled={suspendingUserId === student.user_id}
                                className={student.account_status === 'suspended' 
                                  ? 'text-green-600 hover:text-green-700 hover:bg-green-50' 
                                  : 'text-orange-600 hover:text-orange-700 hover:bg-orange-50'
                                }
                              >
                                {suspendingUserId === student.user_id ? (
                                  <Loader2 className="w-4 h-4 animate-spin" />
                                ) : student.account_status === 'suspended' ? (
                                  <UserCheck className="w-4 h-4" />
                                ) : (
                                  <Ban className="w-4 h-4" />
                                )}
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>
                              {student.account_status === 'suspended' ? 'Activate' : 'Suspend'}
                            </TooltipContent>
                          </Tooltip>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => handleManageStudent(student)}
                              >
                                <Settings className="w-4 h-4" />
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>Manage Student</TooltipContent>
                          </Tooltip>
                          <Tooltip>
                            <TooltipTrigger asChild>
                              <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => navigate(`/admin/students/${student.user_id}`)}
                              >
                                <Eye className="w-4 h-4" />
                              </Button>
                            </TooltipTrigger>
                            <TooltipContent>View Profile</TooltipContent>
                          </Tooltip>
                        </div>
                      </TooltipProvider>

                      {/* Mobile Actions */}
                      <div className="sm:hidden">
                        <DropdownMenu>
                          <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon">
                              <MoreHorizontal className="w-4 h-4" />
                            </Button>
                          </DropdownMenuTrigger>
                          <DropdownMenuContent align="end" className="bg-popover">
                            <DropdownMenuItem 
                              onClick={() => handleToggleSuspend(student)}
                              disabled={suspendingUserId === student.user_id}
                              className={student.account_status === 'suspended' 
                                ? 'text-green-600' 
                                : 'text-orange-600'
                              }
                            >
                              {suspendingUserId === student.user_id ? (
                                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                              ) : student.account_status === 'suspended' ? (
                                <UserCheck className="w-4 h-4 mr-2" />
                              ) : (
                                <Ban className="w-4 h-4 mr-2" />
                              )}
                              {student.account_status === 'suspended' ? 'Activate' : 'Suspend'}
                            </DropdownMenuItem>
                            <DropdownMenuSeparator />
                            <DropdownMenuItem onClick={() => handleManageStudent(student)}>
                              <Settings className="w-4 h-4 mr-2" />
                              Manage
                            </DropdownMenuItem>
                            <DropdownMenuItem onClick={() => navigate(`/admin/students/${student.user_id}`)}>
                              <Eye className="w-4 h-4 mr-2" />
                              View Profile
                            </DropdownMenuItem>
                          </DropdownMenuContent>
                        </DropdownMenu>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="flex items-center justify-between mt-4">
            <p className="text-sm text-muted-foreground">
              Showing {(page - 1) * pageSize + 1} - {Math.min(page * pageSize, totalCount)} of {totalCount}
            </p>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.max(1, p - 1))}
                disabled={page === 1}
              >
                <ChevronLeft className="w-4 h-4" />
                Previous
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(p => Math.min(totalPages, p + 1))}
                disabled={page === totalPages}
              >
                Next
                <ChevronRight className="w-4 h-4" />
              </Button>
            </div>
          </div>
        )}
      </CardContent>
    </Card>

    {/* Student Actions Dialog */}
    <StudentActionsDialog
      student={selectedStudent}
      open={actionsDialogOpen}
      onClose={() => {
        setActionsDialogOpen(false);
        setSelectedStudent(null);
      }}
      onSuccess={handleActionsSuccess}
    />
    </>
  );
};
