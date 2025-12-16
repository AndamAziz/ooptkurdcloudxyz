import { useEffect, useState } from 'react';
import { 
  Activity, 
  CheckCircle2, 
  UserPlus, 
  Award, 
  TrendingUp,
  BookOpen,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { supabase } from '@/integrations/supabase/client';
import { formatDistanceToNow } from 'date-fns';

interface ActivityItem {
  id: string;
  type: 'practice' | 'registration' | 'certificate' | 'levelup';
  message: string;
  timestamp: string;
  details?: Record<string, any>;
}

export const AdminActivityFeed = () => {
  const [activities, setActivities] = useState<ActivityItem[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchRecentActivity = async () => {
    setLoading(true);
    try {
      const activityList: ActivityItem[] = [];

      // Fetch recent practice sessions
      const { data: sessions } = await supabase
        .from('practice_sessions')
        .select('id, user_id, accuracy, correct_count, total_questions, ended_at, profiles!inner(display_name, username)')
        .not('ended_at', 'is', null)
        .order('ended_at', { ascending: false })
        .limit(10);

      if (sessions) {
        sessions.forEach((s: any) => {
          const name = s.profiles?.display_name || s.profiles?.username || 'A student';
          activityList.push({
            id: `session-${s.id}`,
            type: 'practice',
            message: `${name} completed practice (${s.correct_count}/${s.total_questions})`,
            timestamp: s.ended_at,
            details: { accuracy: s.accuracy },
          });
        });
      }

      // Fetch recent registrations
      const { data: newUsers } = await supabase
        .from('profiles')
        .select('id, display_name, username, created_at')
        .order('created_at', { ascending: false })
        .limit(5);

      if (newUsers) {
        newUsers.forEach(u => {
          activityList.push({
            id: `user-${u.id}`,
            type: 'registration',
            message: `${u.display_name || u.username || 'New student'} registered`,
            timestamp: u.created_at,
          });
        });
      }

      // Fetch recent certificates
      const { data: certs } = await supabase
        .from('certificates')
        .select('id, cefr_level, certificate_type, issued_at, profiles!inner(display_name, username)')
        .order('issued_at', { ascending: false })
        .limit(5);

      if (certs) {
        certs.forEach((c: any) => {
          const name = c.profiles?.display_name || c.profiles?.username || 'A student';
          const certType = c.certificate_type === 'excellence' ? 'Excellence' : c.cefr_level;
          activityList.push({
            id: `cert-${c.id}`,
            type: 'certificate',
            message: `${name} earned ${certType} certificate`,
            timestamp: c.issued_at,
          });
        });
      }

      // Sort by timestamp
      activityList.sort((a, b) => 
        new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
      );

      setActivities(activityList.slice(0, 15));
    } catch (error) {
      console.error('Error fetching activity:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecentActivity();

    // Refresh every 30 seconds
    const interval = setInterval(fetchRecentActivity, 30000);
    return () => clearInterval(interval);
  }, []);

  const getActivityIcon = (type: string) => {
    switch (type) {
      case 'practice':
        return <BookOpen className="w-4 h-4 text-blue-500" />;
      case 'registration':
        return <UserPlus className="w-4 h-4 text-green-500" />;
      case 'certificate':
        return <Award className="w-4 h-4 text-yellow-500" />;
      case 'levelup':
        return <TrendingUp className="w-4 h-4 text-purple-500" />;
      default:
        return <Activity className="w-4 h-4 text-muted-foreground" />;
    }
  };

  if (loading) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="text-base flex items-center gap-2">
            <Activity className="w-4 h-4" />
            Live Activity Feed
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
              <div key={i} className="flex items-start gap-3">
                <Skeleton className="w-8 h-8 rounded-full" />
                <div className="flex-1">
                  <Skeleton className="h-4 w-full" />
                  <Skeleton className="h-3 w-20 mt-1" />
                </div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader className="p-3 md:p-4 pb-2">
        <CardTitle className="text-xs md:text-base flex items-center gap-1 md:gap-2">
          <Activity className="w-3 h-3 md:w-4 md:h-4" />
          Activity
          <Badge variant="outline" className="ml-1 md:ml-2 text-[9px] md:text-xs px-1 py-0">
            <span className="w-1.5 h-1.5 rounded-full bg-green-500 animate-pulse mr-0.5" />
            Live
          </Badge>
        </CardTitle>
      </CardHeader>
      <CardContent className="p-2 md:p-4 pt-0">
        {activities.length === 0 ? (
          <p className="text-center text-muted-foreground text-xs py-4">No recent activity</p>
        ) : (
          <div className="space-y-2 max-h-[300px] md:max-h-[400px] overflow-y-auto pr-1">
            {activities.map((activity) => (
              <div
                key={activity.id}
                className="flex items-start gap-2 p-1.5 md:p-2 rounded-lg hover:bg-muted/50 transition-colors"
              >
                <div className="p-1.5 md:p-2 rounded-full bg-muted flex-shrink-0">
                  {getActivityIcon(activity.type)}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[11px] md:text-sm truncate">{activity.message}</p>
                  <p className="text-[9px] md:text-xs text-muted-foreground">
                    {formatDistanceToNow(new Date(activity.timestamp), { addSuffix: true })}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
};
