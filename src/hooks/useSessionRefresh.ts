import { useEffect, useCallback, useRef } from 'react';
import { supabase } from '@/integrations/supabase/client';

interface UseSessionRefreshOptions {
  /** Interval in milliseconds to check/refresh session (default: 5 minutes) */
  refreshInterval?: number;
  /** Whether to enable automatic refresh (default: true) */
  enabled?: boolean;
  /** Callback when session expires and cannot be refreshed */
  onSessionExpired?: () => void;
}

export const useSessionRefresh = ({
  refreshInterval = 5 * 60 * 1000, // 5 minutes
  enabled = true,
  onSessionExpired,
}: UseSessionRefreshOptions = {}) => {
  const intervalRef = useRef<NodeJS.Timeout | null>(null);
  const lastRefreshRef = useRef<number>(Date.now());

  const refreshSession = useCallback(async () => {
    try {
      const { data, error } = await supabase.auth.refreshSession();
      
      if (error) {
        console.error('Session refresh failed:', error.message);
        
        // Check if session is completely expired
        const { data: sessionData } = await supabase.auth.getSession();
        if (!sessionData.session) {
          console.log('Session expired, triggering callback');
          onSessionExpired?.();
          return false;
        }
      }
      
      if (data.session) {
        lastRefreshRef.current = Date.now();
        console.log('Session refreshed successfully');
        return true;
      }
      
      return false;
    } catch (err) {
      console.error('Error refreshing session:', err);
      return false;
    }
  }, [onSessionExpired]);

  // Manual refresh function that can be called before important operations
  const ensureFreshSession = useCallback(async () => {
    const timeSinceLastRefresh = Date.now() - lastRefreshRef.current;
    
    // If it's been more than 4 minutes since last refresh, refresh now
    if (timeSinceLastRefresh > 4 * 60 * 1000) {
      return await refreshSession();
    }
    
    // Otherwise, just verify the session exists
    const { data } = await supabase.auth.getSession();
    return !!data.session;
  }, [refreshSession]);

  useEffect(() => {
    if (!enabled) return;

    // Initial refresh check
    refreshSession();

    // Set up interval for periodic refresh
    intervalRef.current = setInterval(() => {
      refreshSession();
    }, refreshInterval);

    // Also refresh on window focus (user returning to tab)
    const handleFocus = () => {
      const timeSinceLastRefresh = Date.now() - lastRefreshRef.current;
      if (timeSinceLastRefresh > 2 * 60 * 1000) { // 2 minutes
        refreshSession();
      }
    };

    window.addEventListener('focus', handleFocus);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
      window.removeEventListener('focus', handleFocus);
    };
  }, [enabled, refreshInterval, refreshSession]);

  return {
    refreshSession,
    ensureFreshSession,
  };
};
