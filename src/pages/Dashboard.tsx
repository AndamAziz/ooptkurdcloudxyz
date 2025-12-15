import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { 
  BookOpen, 
  Target, 
  Trophy, 
  Clock, 
  TrendingUp,
  Play,
  Timer
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { XPProgressBar } from '@/components/XPProgressBar';
import { DonationButton } from '@/components/DonationButton';
import { useAuth } from '@/contexts/AuthContext';
import { supabase } from '@/integrations/supabase/client';
import { LEVEL_DESCRIPTIONS, type CEFRLevel, CEFR_LEVELS } from '@/lib/constants';
import { format } from 'date-fns';

interface PracticeSession {
  id: string;
  cefr: CEFRLevel;
  mode: string;
  started_at: string;
  total_questions: number;
  correct_count: number;
  accuracy: number;
  xp_earned: number;
  summary_text: string | null;
}

interface Stats {
  totalAttempts: number;
  totalCorrect: number;
  accuracy: number;
}

const Dashboard = () => {
  const { profile, user } = useAuth();
  const [sessions, setSessions] = useState<PracticeSession[]>([]);
  const [stats, setStats] = useState<Stats>({ totalAttempts: 0, totalCorrect: 0, accuracy: 0 });
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (user) {
      fetchData();
    }
  }, [user]);

  const fetchData = async () => {
    // Fetch recent sessions
    const { data: sessionsData } = await supabase
      .from('practice_sessions')
      .select('*')
      .eq('user_id', user!.id)
      .order('started_at', { ascending: false })
      .limit(5);

    if (sessionsData) {
      setSessions(sessionsData as PracticeSession[]);
    }

    // Fetch stats
    const { data: attemptsData } = await supabase
      .from('attempts')
      .select('is_correct')
      .eq('user_id', user!.id);

    if (attemptsData) {
      const total = attemptsData.length;
      const correct = attemptsData.filter(a => a.is_correct).length;
      setStats({
        totalAttempts: total,
        totalCorrect: correct,
        accuracy: total > 0 ? Math.round((correct / total) * 100) : 0,
      });
    }

    setLoading(false);
  };

  const currentLevel = (profile?.current_cefr || 'A1') as CEFRLevel;
  const currentXP = profile?.xp || 0;

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container py-8">
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Main Content */}
          <div className="lg:col-span-2 space-y-6">
            {/* Welcome Card */}
            <Card className="overflow-hidden">
              <div className="bg-gradient-to-r from-primary to-primary/80 p-6 text-primary-foreground">
                <div className="flex items-center justify-between">
                  <div>
                    <h1 className="font-serif text-2xl font-bold mb-1">
                      Welcome back, {profile?.display_name || profile?.username || 'Student'}!
                    </h1>
                    <p className="text-primary-foreground/80">
                      Continue your journey to English mastery
                    </p>
                  </div>
                  <CEFRBadge level={currentLevel} size="lg" />
                </div>
              </div>
              <CardContent className="p-6">
                <div className="mb-4">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-medium">Level Progress</span>
                    <span className="text-sm text-muted-foreground">{currentLevel}</span>
                  </div>
                  <XPProgressBar currentXP={currentXP} currentLevel={currentLevel} />
                </div>
                <p className="text-sm text-muted-foreground">
                  {LEVEL_DESCRIPTIONS[currentLevel]}
                </p>
              </CardContent>
            </Card>

            {/* Practice Modes */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Play className="w-5 h-5" />
                  Start Practice
                </CardTitle>
                <CardDescription>
                  Choose your practice mode and level
                </CardDescription>
              </CardHeader>
              <CardContent>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Link to="/practice?mode=10_questions">
                    <Card className="card-hover cursor-pointer border-2 hover:border-primary">
                      <CardContent className="p-6 text-center">
                        <div className="w-12 h-12 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-4">
                          <Target className="w-6 h-6 text-primary" />
                        </div>
                        <h3 className="font-semibold mb-1">10 Questions</h3>
                        <p className="text-sm text-muted-foreground">
                          Quick practice session
                        </p>
                      </CardContent>
                    </Card>
                  </Link>
                  
                  <Link to="/practice?mode=timed">
                    <Card className="card-hover cursor-pointer border-2 hover:border-primary">
                      <CardContent className="p-6 text-center">
                        <div className="w-12 h-12 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-4">
                          <Timer className="w-6 h-6 text-warning" />
                        </div>
                        <h3 className="font-semibold mb-1">Timed Mode</h3>
                        <p className="text-sm text-muted-foreground">
                          10 minutes challenge
                        </p>
                      </CardContent>
                    </Card>
                  </Link>
                </div>

                <div className="mt-6">
                  <p className="text-sm font-medium mb-3">Select Level:</p>
                  <div className="flex flex-wrap gap-2">
                    {CEFR_LEVELS.map((level) => (
                      <Link key={level} to={`/practice?level=${level}`}>
                        <Button variant="outline" size="sm">
                          <CEFRBadge level={level} size="sm" />
                        </Button>
                      </Link>
                    ))}
                  </div>
                </div>
              </CardContent>
            </Card>

            {/* Recent Sessions */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Clock className="w-5 h-5" />
                  Recent Practice
                </CardTitle>
              </CardHeader>
              <CardContent>
                {sessions.length === 0 ? (
                  <div className="text-center py-8 text-muted-foreground">
                    <BookOpen className="w-12 h-12 mx-auto mb-3 opacity-50" />
                    <p>No practice sessions yet</p>
                    <p className="text-sm">Start your first practice above!</p>
                  </div>
                ) : (
                  <div className="space-y-3">
                    {sessions.map((session) => (
                      <div
                        key={session.id}
                        className="flex items-center justify-between p-4 rounded-lg bg-muted/50"
                      >
                        <div className="flex items-center gap-3">
                          <CEFRBadge level={session.cefr} size="sm" />
                          <div>
                            <p className="font-medium">
                              {session.correct_count}/{session.total_questions} correct
                            </p>
                            <p className="text-sm text-muted-foreground">
                              {format(new Date(session.started_at), 'MMM d, yyyy')}
                            </p>
                          </div>
                        </div>
                        <div className="text-right">
                          <p className="font-semibold text-success">+{session.xp_earned} XP</p>
                          <p className="text-sm text-muted-foreground">
                            {session.accuracy}% accuracy
                          </p>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Sidebar */}
          <div className="space-y-6">
            {/* Stats Card */}
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <TrendingUp className="w-5 h-5" />
                  Your Stats
                </CardTitle>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Total XP</span>
                  <span className="font-bold text-lg">{currentXP.toLocaleString()}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Questions Answered</span>
                  <span className="font-bold text-lg">{stats.totalAttempts}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Correct Answers</span>
                  <span className="font-bold text-lg text-success">{stats.totalCorrect}</span>
                </div>
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">Accuracy</span>
                  <span className="font-bold text-lg">{stats.accuracy}%</span>
                </div>
              </CardContent>
            </Card>

            {/* Donation Card */}
            <Card className="border-2 border-dashed">
              <CardContent className="p-6 text-center">
                <Trophy className="w-10 h-10 mx-auto mb-3 text-warning" />
                <h3 className="font-semibold mb-2">Support Our Mission</h3>
                <p className="text-sm text-muted-foreground mb-4">
                  Help us keep this resource free and accessible for all learners
                </p>
                <DonationButton size="lg" className="w-full" />
              </CardContent>
            </Card>
          </div>
        </div>
      </main>
    </div>
  );
};

export default Dashboard;
