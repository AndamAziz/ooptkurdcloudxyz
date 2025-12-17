import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { 
  ArrowLeft, 
  Activity, 
  Search, 
  UserX, 
  UserCheck, 
  KeyRound,
  Trash2,
  Filter
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import { Header } from '@/components/Header';
import { useToast } from '@/hooks/use-toast';
import { useSessionRefresh } from '@/hooks/useSessionRefresh';
import { supabase } from '@/integrations/supabase/client';
import { formatDistanceToNow, format } from 'date-fns';

interface AdminLog {
  id: string;
  admin_id: string;
  action_type: string;
  target_user_id: string | null;
  details: Record<string, any> | null;
  created_at: string;
  admin_profile?: {
    display_name: string | null;
    username: string | null;
  };
  target_profile?: {
    display_name: string | null;
    username: string | null;
  };
}

const AdminActivityLog = () => {
  const navigate = useNavigate();
  const { toast } = useToast();
  const [logs, setLogs] = useState<AdminLog[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterAction, setFilterAction] = useState<string>('all');
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const pageSize = 50;

  // Auto-refresh session to prevent token expiration during long admin sessions
  useSessionRefresh({
    refreshInterval: 5 * 60 * 1000, // 5 minutes
    onSessionExpired: () => {
      toast({
        title: 'Session Expired',
        description: 'Please log in again to continue.',
        variant: 'destructive',
      });
      navigate('/auth');
    },
  });

  const fetchLogs = async (reset = false) => {
    setLoading(true);
    try {
      const currentPage = reset ? 0 : page;
      
      let query = supabase
        .from('admin_logs')
        .select('*')
        .order('created_at', { ascending: false })
        .range(currentPage * pageSize, (currentPage + 1) * pageSize - 1);

      if (filterAction !== 'all') {
        query = query.eq('action_type', filterAction);
      }

      const { data, error } = await query;
      if (error) throw error;

      // Fetch admin and target profiles
      const logsWithProfiles: AdminLog[] = [];
      
      if (data) {
        const adminIds = [...new Set(data.map(l => l.admin_id))];
        const targetIds = [...new Set(data.filter(l => l.target_user_id).map(l => l.target_user_id!))];
        
        const { data: adminProfiles } = await supabase
          .from('profiles')
          .select('user_id, display_name, username')
          .in('user_id', adminIds);
          
        const { data: targetProfiles } = targetIds.length > 0 
          ? await supabase
              .from('profiles')
              .select('user_id, display_name, username')
              .in('user_id', targetIds)
          : { data: [] };
          
        const adminMap = new Map<string, { display_name: string | null; username: string | null }>();
        adminProfiles?.forEach(p => adminMap.set(p.user_id, { display_name: p.display_name, username: p.username }));
        
        const targetMap = new Map<string, { display_name: string | null; username: string | null }>();
        targetProfiles?.forEach(p => targetMap.set(p.user_id, { display_name: p.display_name, username: p.username }));
        
        data.forEach(log => {
          logsWithProfiles.push({
            ...log,
            details: log.details as Record<string, any> | null,
            admin_profile: adminMap.get(log.admin_id),
            target_profile: log.target_user_id ? targetMap.get(log.target_user_id) : undefined,
          });
        });
      }

      if (reset) {
        setLogs(logsWithProfiles);
        setPage(0);
      } else {
        setLogs(prev => [...prev, ...logsWithProfiles]);
      }
      
      setHasMore((data?.length || 0) >= pageSize);
    } catch (error) {
      console.error('Error fetching admin logs:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs(true);
  }, [filterAction]);

  const loadMore = () => {
    setPage(prev => prev + 1);
    fetchLogs();
  };

  const getActionIcon = (actionType: string) => {
    switch (actionType) {
      case 'user_suspended':
        return <UserX className="w-4 h-4 text-destructive" />;
      case 'user_activated':
        return <UserCheck className="w-4 h-4 text-green-500" />;
      case 'password_reset':
        return <KeyRound className="w-4 h-4 text-blue-500" />;
      case 'user_deleted':
        return <Trash2 className="w-4 h-4 text-destructive" />;
      default:
        return <Activity className="w-4 h-4 text-muted-foreground" />;
    }
  };

  const getActionBadge = (actionType: string) => {
    switch (actionType) {
      case 'user_suspended':
        return <Badge variant="destructive">Suspended</Badge>;
      case 'user_activated':
        return <Badge className="bg-green-500 hover:bg-green-600">Activated</Badge>;
      case 'password_reset':
        return <Badge variant="secondary">Password Reset</Badge>;
      case 'user_deleted':
        return <Badge variant="destructive">Deleted</Badge>;
      default:
        return <Badge variant="outline">{actionType}</Badge>;
    }
  };

  const filteredLogs = logs.filter(log => {
    if (!searchQuery) return true;
    const query = searchQuery.toLowerCase();
    const adminName = log.admin_profile?.display_name || log.admin_profile?.username || '';
    const targetName = log.target_profile?.display_name || log.target_profile?.username || '';
    const targetEmail = log.details?.target_email || '';
    const reason = log.details?.reason || '';
    
    return (
      adminName.toLowerCase().includes(query) ||
      targetName.toLowerCase().includes(query) ||
      targetEmail.toLowerCase().includes(query) ||
      reason.toLowerCase().includes(query) ||
      log.action_type.toLowerCase().includes(query)
    );
  });

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container mx-auto px-4 py-6 max-w-6xl">
        <div className="flex items-center gap-4 mb-6">
          <Link to="/admin">
            <Button variant="ghost" size="icon">
              <ArrowLeft className="w-5 h-5" />
            </Button>
          </Link>
          <div>
            <h1 className="text-2xl font-bold flex items-center gap-2">
              <Activity className="w-6 h-6" />
              Admin Activity Log
            </h1>
            <p className="text-muted-foreground text-sm">
              هەموو کردارەکانی ئەدمین (suspend، activate، password reset)
            </p>
          </div>
        </div>

        <Card>
          <CardHeader className="pb-4">
            <div className="flex flex-col md:flex-row gap-4">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                <Input
                  placeholder="گەڕان بەدوای ناو، ئیمەیڵ، هۆکار..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="pl-9"
                />
              </div>
              <Select value={filterAction} onValueChange={setFilterAction}>
                <SelectTrigger className="w-full md:w-[200px]">
                  <Filter className="w-4 h-4 mr-2" />
                  <SelectValue placeholder="Filter by action" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">هەموو کردارەکان</SelectItem>
                  <SelectItem value="user_suspended">Suspended</SelectItem>
                  <SelectItem value="user_activated">Activated</SelectItem>
                  <SelectItem value="password_reset">Password Reset</SelectItem>
                  <SelectItem value="user_deleted">Deleted</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </CardHeader>
          <CardContent>
            {loading && logs.length === 0 ? (
              <div className="space-y-3">
                {[1, 2, 3, 4, 5].map(i => (
                  <Skeleton key={i} className="h-16 w-full" />
                ))}
              </div>
            ) : filteredLogs.length === 0 ? (
              <div className="text-center py-12 text-muted-foreground">
                <Activity className="w-12 h-12 mx-auto mb-4 opacity-50" />
                <p>هیچ کرداریک نەدۆزرایەوە</p>
              </div>
            ) : (
              <>
                <div className="overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead>کردار</TableHead>
                        <TableHead>ئەدمین</TableHead>
                        <TableHead>خوێندکار</TableHead>
                        <TableHead>هۆکار / وردەکاری</TableHead>
                        <TableHead>کات</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredLogs.map((log) => (
                        <TableRow key={log.id}>
                          <TableCell>
                            <div className="flex items-center gap-2">
                              {getActionIcon(log.action_type)}
                              {getActionBadge(log.action_type)}
                            </div>
                          </TableCell>
                          <TableCell>
                            <span className="font-medium">
                              {log.admin_profile?.display_name || log.admin_profile?.username || 'Unknown'}
                            </span>
                          </TableCell>
                          <TableCell>
                            {log.target_user_id ? (
                              <div>
                                <p className="font-medium">
                                  {log.details?.target_name || log.target_profile?.display_name || log.target_profile?.username || 'Unknown'}
                                </p>
                                {log.details?.target_email && (
                                  <p className="text-xs text-muted-foreground">
                                    {log.details.target_email}
                                  </p>
                                )}
                              </div>
                            ) : (
                              <span className="text-muted-foreground">-</span>
                            )}
                          </TableCell>
                          <TableCell>
                            {log.details?.reason ? (
                              <span className="text-sm">{log.details.reason}</span>
                            ) : (
                              <span className="text-muted-foreground text-sm">-</span>
                            )}
                          </TableCell>
                          <TableCell>
                            <div>
                              <p className="text-sm">
                                {formatDistanceToNow(new Date(log.created_at), { addSuffix: true })}
                              </p>
                              <p className="text-xs text-muted-foreground">
                                {format(new Date(log.created_at), 'yyyy-MM-dd HH:mm')}
                              </p>
                            </div>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
                
                {hasMore && (
                  <div className="flex justify-center mt-4">
                    <Button 
                      variant="outline" 
                      onClick={loadMore}
                      disabled={loading}
                    >
                      {loading ? 'Loading...' : 'Load More'}
                    </Button>
                  </div>
                )}
              </>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
};

export default AdminActivityLog;
