import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/contexts/AuthContext';
import { Loader2 } from 'lucide-react';

interface ProtectedRouteProps {
  children: React.ReactNode;
  requireAdmin?: boolean;
  skipPlacementCheck?: boolean;
}

export const ProtectedRoute = ({ 
  children, 
  requireAdmin = false,
  skipPlacementCheck = false 
}: ProtectedRouteProps) => {
  const { user, profile, isAdmin, loading } = useAuth();
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

  if (requireAdmin && !isAdmin) {
    return <Navigate to="/dashboard" replace />;
  }

  // Check if user needs to complete placement test (only for non-admin routes)
  if (!skipPlacementCheck && !requireAdmin && profile && !profile.placement_completed) {
    return <Navigate to="/placement" replace />;
  }

  return <>{children}</>;
};
