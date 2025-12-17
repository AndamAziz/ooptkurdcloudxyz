import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { Ban, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requireAdmin?: boolean;
}

export const ProtectedRoute = ({ children, requireAdmin = false }: ProtectedRouteProps) => {
  const { user, isAdmin, loading, profile, signOut } = useAuth();
  const location = useLocation();

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  if (!user) {
    return <Navigate to="/auth" state={{ from: location }} replace />;
  }

  // Block suspended non-admin accounts from using the app UI
  if (!isAdmin && profile?.account_status === 'suspended') {
    return (
      <main className="min-h-screen flex items-center justify-center p-6">
        <Card className="w-full max-w-md">
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Ban className="h-5 w-5 text-destructive" />
              Account Suspended
            </CardTitle>
            <CardDescription>
              Your account is currently suspended and you can’t continue using the app.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            {profile?.suspend_reason ? (
              <div className="rounded-md border bg-muted/40 p-3 text-sm">
                <div className="font-medium">Reason</div>
                <div className="text-muted-foreground">{profile.suspend_reason}</div>
              </div>
            ) : null}

            <Button className="w-full" variant="destructive" onClick={() => void signOut()}>
              Sign out
            </Button>
          </CardContent>
        </Card>
      </main>
    );
  }

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children}</>;
};
