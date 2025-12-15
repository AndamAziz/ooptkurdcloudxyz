import { Loader2 } from 'lucide-react';
import { cn } from '@/lib/utils';

interface LoadingSpinnerProps {
  className?: string;
  size?: 'sm' | 'md' | 'lg';
  fullScreen?: boolean;
}

export const LoadingSpinner = ({ 
  className, 
  size = 'md',
  fullScreen = false 
}: LoadingSpinnerProps) => {
  const sizeClasses = {
    sm: 'w-4 h-4',
    md: 'w-8 h-8',
    lg: 'w-12 h-12',
  };

  if (fullScreen) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <Loader2 className={cn('animate-spin text-primary', sizeClasses[size], className)} />
      </div>
    );
  }

  return <Loader2 className={cn('animate-spin text-primary', sizeClasses[size], className)} />;
};
