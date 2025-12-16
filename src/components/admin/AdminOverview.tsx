import { Users, TrendingUp, Target, Award, ArrowUp, ArrowDown } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Skeleton } from '@/components/ui/skeleton';
import {
  BarChart,
  Bar,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { useAdminStats } from '@/hooks/useAdminStats';

const COLORS = ['#3B82F6', '#60A5FA', '#93C5FD', '#10B981', '#34D399', '#6EE7B7'];

export const AdminOverview = () => {
  const {
    stats,
    levelDistribution,
    registrationTrend,
    dailyActiveUsers,
    accuracyByLevel,
    loading,
  } = useAdminStats();

  if (loading) {
    return (
      <div className="space-y-6">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
          {[1, 2, 3, 4].map(i => (
            <Card key={i}>
              <CardHeader className="pb-2">
                <Skeleton className="h-4 w-24" />
              </CardHeader>
              <CardContent>
                <Skeleton className="h-8 w-16" />
                <Skeleton className="h-3 w-20 mt-2" />
              </CardContent>
            </Card>
          ))}
        </div>
        <div className="grid gap-4 md:grid-cols-2">
          {[1, 2, 3, 4].map(i => (
            <Card key={i}>
              <CardHeader>
                <Skeleton className="h-5 w-32" />
              </CardHeader>
              <CardContent>
                <Skeleton className="h-[200px] w-full" />
              </CardContent>
            </Card>
          ))}
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4 md:space-y-6">
      {/* Stats Cards */}
      <div className="grid grid-cols-2 gap-2 md:gap-4 lg:grid-cols-4">
        <Card className="p-0">
          <CardHeader className="flex flex-row items-center justify-between p-3 md:p-4 pb-1 md:pb-2">
            <CardTitle className="text-[10px] md:text-sm font-medium">Students</CardTitle>
            <Users className="h-3 w-3 md:h-4 md:w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="p-3 md:p-4 pt-0">
            <div className="text-lg md:text-2xl font-bold">{stats.totalStudents.toLocaleString()}</div>
            <p className="text-[9px] md:text-xs text-muted-foreground flex items-center gap-0.5">
              <span className="text-green-500 flex items-center">
                <ArrowUp className="h-2 w-2 md:h-3 md:w-3" />
                {stats.newThisWeek}
              </span>
              this week
            </p>
          </CardContent>
        </Card>

        <Card className="p-0">
          <CardHeader className="flex flex-row items-center justify-between p-3 md:p-4 pb-1 md:pb-2">
            <CardTitle className="text-[10px] md:text-sm font-medium">Active</CardTitle>
            <TrendingUp className="h-3 w-3 md:h-4 md:w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="p-3 md:p-4 pt-0">
            <div className="text-lg md:text-2xl font-bold">{stats.activeToday.toLocaleString()}</div>
            <p className="text-[9px] md:text-xs text-muted-foreground flex items-center gap-0.5">
              <span className={`flex items-center ${stats.activeDiff >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                {stats.activeDiff >= 0 ? <ArrowUp className="h-2 w-2 md:h-3 md:w-3" /> : <ArrowDown className="h-2 w-2 md:h-3 md:w-3" />}
                {Math.abs(stats.activeDiff)}
              </span>
              vs yesterday
            </p>
          </CardContent>
        </Card>

        <Card className="p-0">
          <CardHeader className="flex flex-row items-center justify-between p-3 md:p-4 pb-1 md:pb-2">
            <CardTitle className="text-[10px] md:text-sm font-medium">Attempts</CardTitle>
            <Target className="h-3 w-3 md:h-4 md:w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="p-3 md:p-4 pt-0">
            <div className="text-lg md:text-2xl font-bold">{stats.totalAttempts.toLocaleString()}</div>
            <p className="text-[9px] md:text-xs text-muted-foreground flex items-center gap-0.5">
              <span className="text-green-500 flex items-center">
                <ArrowUp className="h-2 w-2 md:h-3 md:w-3" />
                {stats.attemptsToday}
              </span>
              today
            </p>
          </CardContent>
        </Card>

        <Card className="p-0">
          <CardHeader className="flex flex-row items-center justify-between p-3 md:p-4 pb-1 md:pb-2">
            <CardTitle className="text-[10px] md:text-sm font-medium">Certificates</CardTitle>
            <Award className="h-3 w-3 md:h-4 md:w-4 text-muted-foreground" />
          </CardHeader>
          <CardContent className="p-3 md:p-4 pt-0">
            <div className="text-lg md:text-2xl font-bold">{stats.totalCertificates.toLocaleString()}</div>
            <p className="text-[9px] md:text-xs text-muted-foreground flex items-center gap-0.5">
              <span className="text-green-500 flex items-center">
                <ArrowUp className="h-2 w-2 md:h-3 md:w-3" />
                {stats.certsThisWeek}
              </span>
              this week
            </p>
          </CardContent>
        </Card>
      </div>

      {/* Charts */}
      <div className="grid gap-3 md:gap-4 grid-cols-1 md:grid-cols-2">
        {/* Registration Trend */}
        <Card>
          <CardHeader className="p-3 md:p-4 pb-2">
            <CardTitle className="text-xs md:text-base">Registrations (30d)</CardTitle>
          </CardHeader>
          <CardContent className="p-2 md:p-4 pt-0">
            <ResponsiveContainer width="100%" height={140}>
              <LineChart data={registrationTrend}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis 
                  dataKey="date" 
                  fontSize={8} 
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis fontSize={8} tickLine={false} width={25} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '11px',
                  }}
                />
                <Line 
                  type="monotone" 
                  dataKey="count" 
                  stroke="hsl(var(--primary))" 
                  strokeWidth={2}
                  dot={false}
                  name="New"
                />
              </LineChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Level Distribution */}
        <Card>
          <CardHeader className="p-3 md:p-4 pb-2">
            <CardTitle className="text-xs md:text-base">Students by Level</CardTitle>
          </CardHeader>
          <CardContent className="p-2 md:p-4 pt-0">
            <ResponsiveContainer width="100%" height={140}>
              <PieChart>
                <Pie
                  data={levelDistribution}
                  dataKey="count"
                  nameKey="level"
                  cx="50%"
                  cy="50%"
                  outerRadius={45}
                  label={({ level, count }) => `${level}: ${count}`}
                  labelLine={false}
                  fontSize={9}
                >
                  {levelDistribution.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={COLORS[index % COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '11px',
                  }}
                />
              </PieChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Daily Active Users */}
        <Card>
          <CardHeader className="p-3 md:p-4 pb-2">
            <CardTitle className="text-xs md:text-base">Active Users (14d)</CardTitle>
          </CardHeader>
          <CardContent className="p-2 md:p-4 pt-0">
            <ResponsiveContainer width="100%" height={140}>
              <BarChart data={dailyActiveUsers}>
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis 
                  dataKey="date" 
                  fontSize={8} 
                  tickLine={false}
                  interval="preserveStartEnd"
                />
                <YAxis fontSize={8} tickLine={false} width={25} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '11px',
                  }}
                />
                <Bar 
                  dataKey="count" 
                  fill="hsl(var(--primary))" 
                  radius={[4, 4, 0, 0]}
                  name="Active"
                />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Accuracy by Level */}
        <Card>
          <CardHeader className="p-3 md:p-4 pb-2">
            <CardTitle className="text-xs md:text-base">Accuracy by Level</CardTitle>
          </CardHeader>
          <CardContent className="p-2 md:p-4 pt-0">
            <ResponsiveContainer width="100%" height={140}>
              <BarChart data={accuracyByLevel} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" className="stroke-muted" />
                <XAxis type="number" domain={[0, 100]} fontSize={8} tickLine={false} />
                <YAxis type="category" dataKey="level" fontSize={8} tickLine={false} width={22} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'hsl(var(--card))',
                    border: '1px solid hsl(var(--border))',
                    borderRadius: '8px',
                    fontSize: '11px',
                  }}
                  formatter={(value) => [`${value}%`, 'Accuracy']}
                />
                <Bar 
                  dataKey="accuracy" 
                  fill="hsl(var(--chart-2))" 
                  radius={[0, 4, 4, 0]}
                  name="Accuracy"
                />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>
      </div>
    </div>
  );
};
