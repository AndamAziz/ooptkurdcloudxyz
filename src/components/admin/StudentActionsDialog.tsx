import { useState, useEffect } from 'react';
import { User, KeyRound, Trash2, Save, Loader2, AlertTriangle, Ban } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/hooks/use-toast';
import { supabase } from '@/integrations/supabase/client';
import type { StudentData } from '@/hooks/useAdminStudents';

interface StudentActionsDialogProps {
  student: StudentData | null;
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export const StudentActionsDialog = ({
  student,
  open,
  onClose,
  onSuccess,
}: StudentActionsDialogProps) => {
  const { toast } = useToast();
  const [activeTab, setActiveTab] = useState('edit');
  const [displayName, setDisplayName] = useState(student?.display_name || '');
  const [username, setUsername] = useState(student?.username || '');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [suspendReason, setSuspendReason] = useState('');
  const [loading, setLoading] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  // Reset form when student changes
  useEffect(() => {
    if (student) {
      setDisplayName(student.display_name || '');
      setUsername(student.username || '');
      setNewPassword('');
      setConfirmPassword('');
      setSuspendReason('');
    }
  }, [student]);

  const handleUpdateProfile = async () => {
    if (!student) return;
    
    setLoading(true);
    try {
      const { error } = await supabase
        .from('profiles')
        .update({
          display_name: displayName.trim() || null,
          username: username.trim() || null,
        })
        .eq('user_id', student.user_id);

      if (error) throw error;

      toast({
        title: 'Profile Updated',
        description: 'Student profile has been updated successfully.',
      });
      onSuccess();
      onClose();
    } catch (error) {
      console.error('Error updating profile:', error);
      toast({
        title: 'Error',
        description: 'Failed to update student profile.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleResetPassword = async () => {
    if (!student) return;
    
    if (newPassword.length < 6) {
      toast({
        title: 'Invalid Password',
        description: 'Password must be at least 6 characters.',
        variant: 'destructive',
      });
      return;
    }

    if (newPassword !== confirmPassword) {
      toast({
        title: 'Passwords Do Not Match',
        description: 'Please make sure both passwords match.',
        variant: 'destructive',
      });
      return;
    }

    setLoading(true);
    try {
      const { data, error } = await supabase.functions.invoke('admin-user-management', {
        body: {
          action: 'reset_password',
          userId: student.user_id,
          newPassword,
        },
      });

      if (error) {
        const errorMessage = error.message || 'Failed to reset password';
        if (errorMessage.includes('401') || errorMessage.includes('token') || errorMessage.includes('unauthorized')) {
          throw new Error('Session expired. Please log in again.');
        }
        throw error;
      }

      toast({
        title: 'Password Reset',
        description: 'Student password has been reset successfully.',
      });
      setNewPassword('');
      setConfirmPassword('');
      onSuccess();
    } catch (error: any) {
      console.error('Error resetting password:', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to reset password. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteStudent = async () => {
    if (!student) return;

    setLoading(true);
    try {
      const { data, error } = await supabase.functions.invoke('admin-user-management', {
        body: {
          action: 'delete_user',
          userId: student.user_id,
        },
      });

      if (error) {
        const errorMessage = error.message || 'Failed to delete user';
        if (errorMessage.includes('401') || errorMessage.includes('token') || errorMessage.includes('unauthorized')) {
          throw new Error('Session expired. Please log in again.');
        }
        throw error;
      }

      toast({
        title: 'Student Deleted',
        description: 'Student account has been permanently deleted.',
      });
      setShowDeleteConfirm(false);
      onSuccess();
      onClose();
    } catch (error: any) {
      console.error('Error deleting student:', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to delete student account.',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  if (!student) return null;

  return (
    <>
      <Dialog open={open} onOpenChange={(isOpen) => !isOpen && onClose()}>
        <DialogContent className="sm:max-w-md max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <User className="w-5 h-5" />
              Manage Student
            </DialogTitle>
            <DialogDescription>
              {student.display_name || student.username || 'Unknown Student'}
            </DialogDescription>
          </DialogHeader>

          <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
            <TabsList className="grid w-full grid-cols-4">
              <TabsTrigger value="edit" className="text-xs sm:text-sm">
                <User className="w-3 h-3 sm:w-4 sm:h-4 mr-1" />
                <span className="hidden sm:inline">Edit</span>
              </TabsTrigger>
              <TabsTrigger value="password" className="text-xs sm:text-sm">
                <KeyRound className="w-3 h-3 sm:w-4 sm:h-4 mr-1" />
                <span className="hidden sm:inline">Password</span>
              </TabsTrigger>
              <TabsTrigger value="suspend" className="text-xs sm:text-sm text-orange-600">
                <Ban className="w-3 h-3 sm:w-4 sm:h-4 mr-1" />
                <span className="hidden sm:inline">Suspend</span>
              </TabsTrigger>
              <TabsTrigger value="delete" className="text-xs sm:text-sm text-destructive">
                <Trash2 className="w-3 h-3 sm:w-4 sm:h-4 mr-1" />
                <span className="hidden sm:inline">Delete</span>
              </TabsTrigger>
            </TabsList>

            {/* Edit Profile Tab */}
            <TabsContent value="edit" className="space-y-4 mt-4">
              <div className="space-y-2">
                <Label htmlFor="displayName">Display Name</Label>
                <Input
                  id="displayName"
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  placeholder="Enter display name"
                  className="h-11"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="username">Username</Label>
                <Input
                  id="username"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter username"
                  className="h-11"
                />
              </div>
              <Button 
                onClick={handleUpdateProfile} 
                disabled={loading}
                className="w-full h-11"
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : (
                  <Save className="w-4 h-4 mr-2" />
                )}
                Save Changes
              </Button>
            </TabsContent>

            {/* Reset Password Tab */}
            <TabsContent value="password" className="space-y-4 mt-4">
              <div className="space-y-2">
                <Label htmlFor="newPassword">New Password</Label>
                <Input
                  id="newPassword"
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  placeholder="Enter new password"
                  className="h-11"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmPassword">Confirm Password</Label>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Confirm new password"
                  className="h-11"
                />
              </div>
              <Button 
                onClick={handleResetPassword} 
                disabled={loading || !newPassword || !confirmPassword}
                className="w-full h-11"
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : (
                  <KeyRound className="w-4 h-4 mr-2" />
                )}
                Reset Password
              </Button>
            </TabsContent>

            {/* Suspend Tab */}
            <TabsContent value="suspend" className="space-y-4 mt-4">
              {student.account_status === 'suspended' ? (
                <div className="p-4 rounded-lg bg-orange-500/10 border border-orange-500/20">
                  <div className="flex items-start gap-3">
                    <Ban className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                    <div>
                      <h4 className="font-semibold text-orange-600">Account Suspended</h4>
                      <p className="text-sm text-muted-foreground mt-1">
                        This student account is currently suspended.
                      </p>
                    </div>
                  </div>
                </div>
              ) : (
                <>
                  <div className="p-4 rounded-lg bg-orange-500/10 border border-orange-500/20">
                    <div className="flex items-start gap-3">
                      <Ban className="w-5 h-5 text-orange-500 flex-shrink-0 mt-0.5" />
                      <div>
                        <h4 className="font-semibold text-orange-600">Suspend Account</h4>
                        <p className="text-sm text-muted-foreground mt-1">
                          Suspending this account will prevent the student from logging in.
                        </p>
                      </div>
                    </div>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="suspendReason">Reason for Suspension</Label>
                    <Textarea
                      id="suspendReason"
                      value={suspendReason}
                      onChange={(e) => setSuspendReason(e.target.value)}
                      placeholder="Enter reason for suspension (optional)"
                      className="min-h-[100px] resize-none"
                    />
                  </div>
                </>
              )}
              <Button 
                variant={student.account_status === 'suspended' ? 'default' : 'destructive'}
                onClick={async () => {
                  if (!student) return;
                  setLoading(true);
                  try {
                    const action = student.account_status === 'suspended' ? 'activate_user' : 'suspend_user';
                    const { error } = await supabase.functions.invoke('admin-user-management', {
                      body: {
                        action,
                        userId: student.user_id,
                        reason: suspendReason.trim() || undefined,
                      },
                    });

                    if (error) {
                      const errorMessage = error.message || 'Failed to update status';
                      if (errorMessage.includes('401') || errorMessage.includes('token') || errorMessage.includes('unauthorized')) {
                        throw new Error('Session expired. Please log in again.');
                      }
                      throw error;
                    }

                    toast({
                      title: student.account_status === 'suspended' ? 'Account Activated' : 'Account Suspended',
                      description: student.account_status === 'suspended' 
                        ? 'Student account has been activated.'
                        : 'Student account has been suspended.',
                    });
                    setSuspendReason('');
                    onSuccess();
                    onClose();
                  } catch (error: any) {
                    console.error('Error updating status:', error);
                    toast({
                      title: 'Error',
                      description: error.message || 'Failed to update account status.',
                      variant: 'destructive',
                    });
                  } finally {
                    setLoading(false);
                  }
                }}
                disabled={loading}
                className={`w-full h-11 ${student.account_status === 'suspended' ? 'bg-green-600 hover:bg-green-700' : 'bg-orange-600 hover:bg-orange-700'}`}
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : (
                  <Ban className="w-4 h-4 mr-2" />
                )}
                {student.account_status === 'suspended' ? 'Activate Account' : 'Suspend Account'}
              </Button>
            </TabsContent>

            {/* Delete Tab */}
            <TabsContent value="delete" className="space-y-4 mt-4">
              <div className="p-4 rounded-lg bg-destructive/10 border border-destructive/20">
                <div className="flex items-start gap-3">
                  <AlertTriangle className="w-5 h-5 text-destructive flex-shrink-0 mt-0.5" />
                  <div>
                    <h4 className="font-semibold text-destructive">Danger Zone</h4>
                    <p className="text-sm text-muted-foreground mt-1">
                      Deleting this student will permanently remove:
                    </p>
                    <ul className="text-sm text-muted-foreground mt-2 space-y-1 list-disc list-inside">
                      <li>Their profile and account</li>
                      <li>All practice attempts</li>
                      <li>Certificates earned</li>
                      <li>Progress data</li>
                    </ul>
                    <p className="text-sm font-medium text-destructive mt-2">
                      This action cannot be undone.
                    </p>
                  </div>
                </div>
              </div>
              <Button 
                variant="destructive"
                onClick={() => setShowDeleteConfirm(true)}
                disabled={loading}
                className="w-full h-11"
              >
                <Trash2 className="w-4 h-4 mr-2" />
                Delete Student Account
              </Button>
            </TabsContent>
          </Tabs>

          <DialogFooter className="mt-4">
            <Button variant="outline" onClick={onClose} className="w-full sm:w-auto">
              Cancel
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={showDeleteConfirm} onOpenChange={setShowDeleteConfirm}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle className="flex items-center gap-2 text-destructive">
              <AlertTriangle className="w-5 h-5" />
              Confirm Deletion
            </AlertDialogTitle>
            <AlertDialogDescription>
              Are you sure you want to permanently delete{' '}
              <span className="font-semibold">
                {student.display_name || student.username || 'this student'}
              </span>
              ? This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={loading}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDeleteStudent}
              disabled={loading}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {loading ? (
                <Loader2 className="w-4 h-4 mr-2 animate-spin" />
              ) : null}
              Delete Permanently
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
};
