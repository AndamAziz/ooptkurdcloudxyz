import { useEffect, useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  ArrowLeft,
  Mail,
  Ban,
  Award,
  Download,
  Trash2,
  CheckCircle2,
  Clock,
  Target,
  Flame,
  TrendingUp,
  Calendar,
  Printer,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { Skeleton } from '@/components/ui/skeleton';
import { Separator } from '@/components/ui/separator';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useToast } from '@/hooks/use-toast';
import { useAuth } from '@/contexts/AuthContext';
import { supabase } from '@/integrations/supabase/client';
import { CEFR_LEVELS, LEVEL_THRESHOLDS, type CEFRLevel, getNextLevel } from '@/lib/constants';
import { format, formatDistanceToNow } from 'date-fns';

interface StudentProfile {
  id: string;
  user_id: string;
  display_name: string | null;
  username: string | null;
  current_cefr: CEFRLevel;
  xp: number;
  streak: number;
  longest_streak: number;
  last_active_at: string | null;
  account_status: string;
  created_at: string;
  total_practice_time_minutes: number;
}

interface PracticeSession {
  id: string;
  mode: string;
  total_questions: number;
  correct_count: number;
  accuracy: number;
  xp_earned: number;
  started_at: string;
  ended_at: string | null;
  cefr: CEFRLevel;
}

interface Certificate {
  id: string;
  certificate_type: string;
  cefr_level: CEFRLevel;
  score_percentage: number;
  issued_at: string;
  verified_by_admin: boolean;
  certificate_number: string;
}

interface AttemptStats {
  total: number;
  correct: number;
  accuracy: number;
}

const AdminStudentProfile = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();
  const { user: adminUser } = useAuth();
  
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [sessions, setSessions] = useState<PracticeSession[]>([]);
  const [certificates, setCertificates] = useState<Certificate[]>([]);
  const [attemptStats, setAttemptStats] = useState<AttemptStats>({ total: 0, correct: 0, accuracy: 0 });
  const [loading, setLoading] = useState(true);

  const fetchStudentData = useCallback(async () => {
    if (!userId) return;
    
    setLoading(true);
    try {
      // Fetch profile
      const { data: profileData, error: profileError } = await supabase
        .from('profiles')
        .select('*')
        .eq('user_id', userId)
        .single();

      if (profileError) throw profileError;
      setProfile(profileData as StudentProfile);

      // Fetch practice sessions
      const { data: sessionsData } = await supabase
        .from('practice_sessions')
        .select('*')
        .eq('user_id', userId)
        .order('started_at', { ascending: false })
        .limit(20);

      setSessions((sessionsData as PracticeSession[]) || []);

      // Fetch certificates
      const { data: certsData } = await supabase
        .from('certificates')
        .select('*')
        .eq('user_id', userId)
        .order('issued_at', { ascending: false });

      setCertificates((certsData as Certificate[]) || []);

      // Fetch attempt stats
      const { data: attemptsData } = await supabase
        .from('attempts')
        .select('is_correct')
        .eq('user_id', userId);

      if (attemptsData) {
        const total = attemptsData.length;
        const correct = attemptsData.filter(a => a.is_correct).length;
        setAttemptStats({
          total,
          correct,
          accuracy: total > 0 ? Math.round((correct / total) * 100) : 0,
        });
      }

      // Log admin action
      if (adminUser) {
        await supabase.from('admin_logs').insert({
          admin_id: adminUser.id,
          action_type: 'view_student',
          target_user_id: userId,
          details: { student_name: profileData?.display_name || profileData?.username },
        });
      }
    } catch (error) {
      console.error('Error fetching student:', error);
      toast({ title: 'Failed to load student data', variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  }, [userId, adminUser, toast]);

  useEffect(() => {
    fetchStudentData();
  }, [fetchStudentData]);

  const handleSuspend = async () => {
    if (!profile) return;
    
    const newStatus = profile.account_status === 'suspended' ? 'active' : 'suspended';
    
    const { error } = await supabase
      .from('profiles')
      .update({ account_status: newStatus })
      .eq('user_id', profile.user_id);

    if (error) {
      toast({ title: 'Failed to update status', variant: 'destructive' });
    } else {
      toast({ title: newStatus === 'suspended' ? 'Account suspended' : 'Account reactivated' });
      setProfile({ ...profile, account_status: newStatus });
      
      if (adminUser) {
        await supabase.from('admin_logs').insert({
          admin_id: adminUser.id,
          action_type: 'suspend_user',
          target_user_id: profile.user_id,
          details: { new_status: newStatus },
        });
      }
    }
  };

  const handleExportData = async () => {
    if (!profile) return;

    const exportData = {
      profile,
      sessions,
      certificates,
      attemptStats,
      exportedAt: new Date().toISOString(),
    };

    const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `student-${profile.display_name || profile.user_id}-export.json`;
    a.click();
    URL.revokeObjectURL(url);

    if (adminUser) {
      await supabase.from('admin_logs').insert({
        admin_id: adminUser.id,
        action_type: 'export_data',
        target_user_id: profile.user_id,
      });
    }

    toast({ title: 'Data exported successfully' });
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background">
        <Header />
        <main className="container py-8">
          <Skeleton className="h-8 w-48 mb-6" />
          <div className="grid gap-6 md:grid-cols-3 mb-6">
            {[1, 2, 3].map(i => (
              <Card key={i}>
                <CardContent className="p-6">
                  <Skeleton className="h-20 w-full" />
                </CardContent>
              </Card>
            ))}
          </div>
        </main>
      </div>
    );
  }

  if (!profile) {
    return (
      <div className="min-h-screen bg-background">
        <Header />
        <main className="container py-8 text-center">
          <p className="text-muted-foreground">Student not found</p>
          <Button onClick={() => navigate('/admin')} className="mt-4">
            Back to Admin
          </Button>
        </main>
      </div>
    );
  }

  const nextLevel = getNextLevel(profile.current_cefr);
  const currentThreshold = LEVEL_THRESHOLDS[profile.current_cefr];
  const nextThreshold = nextLevel ? LEVEL_THRESHOLDS[nextLevel] : profile.xp;
  const progressToNext = nextLevel 
    ? Math.round(((profile.xp - currentThreshold) / (nextThreshold - currentThreshold)) * 100)
    : 100;

  const formatTime = (minutes: number) => {
    const hours = Math.floor(minutes / 60);
    const mins = minutes % 60;
    return hours > 0 ? `${hours}h ${mins}m` : `${mins}m`;
  };

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container py-8">
        {/* Back Button */}
        <Button
          variant="ghost"
          onClick={() => navigate('/admin')}
          className="mb-6"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Admin
        </Button>

        {/* Student Header */}
        <Card className="mb-6">
          <CardContent className="p-6">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <h1 className="text-2xl font-bold">
                    {profile.display_name || profile.username || 'Unknown Student'}
                  </h1>
                  {profile.account_status === 'suspended' && (
                    <Badge variant="destructive">Suspended</Badge>
                  )}
                </div>
                <div className="flex flex-wrap gap-4 text-sm text-muted-foreground">
                  <span className="flex items-center gap-1">
                    <Calendar className="w-4 h-4" />
                    Joined {format(new Date(profile.created_at), 'MMM d, yyyy')}
                  </span>
                  <span className="flex items-center gap-1">
                    <Clock className="w-4 h-4" />
                    Last active: {profile.last_active_at 
                      ? formatDistanceToNow(new Date(profile.last_active_at), { addSuffix: true })
                      : 'Never'
                    }
                  </span>
                </div>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button variant="outline" size="sm" onClick={handleExportData}>
                  <Download className="w-4 h-4 mr-2" />
                  Export Data
                </Button>
                <AlertDialog>
                  <AlertDialogTrigger asChild>
                    <Button 
                      variant={profile.account_status === 'suspended' ? 'default' : 'destructive'}
                      size="sm"
                    >
                      <Ban className="w-4 h-4 mr-2" />
                      {profile.account_status === 'suspended' ? 'Reactivate' : 'Suspend'}
                    </Button>
                  </AlertDialogTrigger>
                  <AlertDialogContent>
                    <AlertDialogHeader>
                      <AlertDialogTitle>
                        {profile.account_status === 'suspended' ? 'Reactivate Account?' : 'Suspend Account?'}
                      </AlertDialogTitle>
                      <AlertDialogDescription>
                        {profile.account_status === 'suspended'
                          ? 'This will restore the student\'s access to the platform.'
                          : 'This will prevent the student from accessing the platform.'
                        }
                      </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                      <AlertDialogCancel>Cancel</AlertDialogCancel>
                      <AlertDialogAction onClick={handleSuspend}>
                        {profile.account_status === 'suspended' ? 'Reactivate' : 'Suspend'}
                      </AlertDialogAction>
                    </AlertDialogFooter>
                  </AlertDialogContent>
                </AlertDialog>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Progress Overview */}
        <div className="grid gap-4 md:grid-cols-3 mb-6">
          <Card>
            <CardContent className="p-6">
              <div className="flex items-center gap-4 mb-4">
                <div className="p-3 rounded-full bg-primary/10">
                  <Target className="w-6 h-6 text-primary" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Current Level</p>
                  <CEFRBadge level={profile.current_cefr} />
                </div>
              </div>
              {nextLevel && (
                <div>
                  <div className="flex justify-between text-sm mb-1">
                    <span>Progress to {nextLevel}</span>
                    <span>{progressToNext}%</span>
                  </div>
                  <Progress value={progressToNext} className="h-2" />
                  <p className="text-xs text-muted-foreground mt-1">
                    Need {nextThreshold - profile.xp} more XP
                  </p>
                </div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-6">
              <div className="flex items-center gap-4 mb-4">
                <div className="p-3 rounded-full bg-yellow-500/10">
                  <TrendingUp className="w-6 h-6 text-yellow-500" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Total XP</p>
                  <p className="text-2xl font-bold">{profile.xp.toLocaleString()}</p>
                </div>
              </div>
              <p className="text-sm text-muted-foreground">
                Accuracy: <span className={attemptStats.accuracy >= 70 ? 'text-green-500' : 'text-yellow-500'}>
                  {attemptStats.accuracy}%
                </span>
              </p>
            </CardContent>
          </Card>

          <Card>
            <CardContent className="p-6">
              <div className="flex items-center gap-4 mb-4">
                <div className="p-3 rounded-full bg-orange-500/10">
                  <Flame className="w-6 h-6 text-orange-500" />
                </div>
                <div>
                  <p className="text-sm text-muted-foreground">Current Streak</p>
                  <p className="text-2xl font-bold">{profile.streak} days</p>
                </div>
              </div>
              <p className="text-sm text-muted-foreground">
                Longest: {profile.longest_streak} days
              </p>
            </CardContent>
          </Card>
        </div>

        {/* Detailed Stats */}
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 mb-6">
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-2xl font-bold">{attemptStats.total}</p>
              <p className="text-sm text-muted-foreground">Questions Answered</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-2xl font-bold text-green-500">{attemptStats.correct}</p>
              <p className="text-sm text-muted-foreground">Correct Answers</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-2xl font-bold">{formatTime(profile.total_practice_time_minutes)}</p>
              <p className="text-sm text-muted-foreground">Total Practice Time</p>
            </CardContent>
          </Card>
          <Card>
            <CardContent className="p-4 text-center">
              <p className="text-2xl font-bold">{certificates.length}</p>
              <p className="text-sm text-muted-foreground">Certificates Earned</p>
            </CardContent>
          </Card>
        </div>

        {/* Level Journey */}
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">Level Journey</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="flex items-center justify-between gap-2 overflow-x-auto pb-2">
              {CEFR_LEVELS.map((level, index) => {
                const isCompleted = CEFR_LEVELS.indexOf(profile.current_cefr) > index;
                const isCurrent = profile.current_cefr === level;
                const cert = certificates.find(c => c.cefr_level === level);
                
                return (
                  <div key={level} className="flex items-center">
                    <div className={`
                      flex flex-col items-center p-3 rounded-lg transition-colors
                      ${isCompleted ? 'bg-green-100 dark:bg-green-900/30' : ''}
                      ${isCurrent ? 'bg-primary/10 ring-2 ring-primary' : ''}
                    `}>
                      <CEFRBadge level={level as CEFRLevel} size="sm" />
                      <div className="mt-2 flex items-center gap-1">
                        {isCompleted && <CheckCircle2 className="w-4 h-4 text-green-500" />}
                        {cert?.verified_by_admin && <Award className="w-4 h-4 text-yellow-500" />}
                      </div>
                    </div>
                    {index < CEFR_LEVELS.length - 1 && (
                      <div className={`w-8 h-0.5 mx-1 ${isCompleted ? 'bg-green-500' : 'bg-muted'}`} />
                    )}
                  </div>
                );
              })}
            </div>
          </CardContent>
        </Card>

        {/* Practice History */}
        <Card className="mb-6">
          <CardHeader>
            <CardTitle className="text-base">Recent Practice Sessions</CardTitle>
          </CardHeader>
          <CardContent>
            {sessions.length === 0 ? (
              <p className="text-center text-muted-foreground py-8">No practice sessions yet</p>
            ) : (
              <div className="rounded-md border overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Date</TableHead>
                      <TableHead>Mode</TableHead>
                      <TableHead>Level</TableHead>
                      <TableHead>Score</TableHead>
                      <TableHead>Accuracy</TableHead>
                      <TableHead>XP Earned</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {sessions.map(session => (
                      <TableRow key={session.id}>
                        <TableCell className="text-sm">
                          {format(new Date(session.started_at), 'MMM d, yyyy h:mm a')}
                        </TableCell>
                        <TableCell>
                          <Badge variant="outline">{session.mode || '10 Questions'}</Badge>
                        </TableCell>
                        <TableCell>
                          <CEFRBadge level={session.cefr} size="sm" />
                        </TableCell>
                        <TableCell>
                          {session.correct_count}/{session.total_questions}
                        </TableCell>
                        <TableCell>
                          <span className={session.accuracy >= 70 ? 'text-green-500' : 'text-yellow-500'}>
                            {session.accuracy}%
                          </span>
                        </TableCell>
                        <TableCell className="text-primary font-medium">
                          +{session.xp_earned} XP
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>

        {/* Certificates */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Certificates</CardTitle>
          </CardHeader>
          <CardContent>
            {certificates.length === 0 ? (
              <p className="text-center text-muted-foreground py-8">No certificates earned yet</p>
            ) : (
              <div className="rounded-md border overflow-x-auto">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Type</TableHead>
                      <TableHead>Level</TableHead>
                      <TableHead>Score</TableHead>
                      <TableHead>Date</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Certificate #</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {certificates.map(cert => (
                      <TableRow key={cert.id}>
                        <TableCell>
                          <Badge variant="outline">
                            {cert.certificate_type === 'level_completion' ? 'Level' :
                             cert.certificate_type === 'excellence' ? 'Excellence' : 'Course'}
                          </Badge>
                        </TableCell>
                        <TableCell>
                          <CEFRBadge level={cert.cefr_level} size="sm" />
                        </TableCell>
                        <TableCell className="text-green-500 font-medium">
                          {cert.score_percentage}%
                        </TableCell>
                        <TableCell className="text-sm">
                          {format(new Date(cert.issued_at), 'MMM d, yyyy')}
                        </TableCell>
                        <TableCell>
                          {cert.verified_by_admin ? (
                            <Badge className="bg-green-500">
                              <CheckCircle2 className="w-3 h-3 mr-1" />
                              Verified
                            </Badge>
                          ) : (
                            <Badge variant="secondary">Pending</Badge>
                          )}
                        </TableCell>
                        <TableCell className="font-mono text-xs">
                          {cert.certificate_number}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
};

export default AdminStudentProfile;
