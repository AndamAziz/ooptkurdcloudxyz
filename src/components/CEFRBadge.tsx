import { cn } from '@/lib/utils';
import { getCEFRColor, type CEFRLevel } from '@/lib/constants';

interface CEFRBadgeProps {
  level: CEFRLevel;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

export const CEFRBadge = ({ level, size = 'md', className }: CEFRBadgeProps) => {
  const sizeClasses = {
    sm: 'px-2 py-0.5 text-xs',
    md: 'px-2.5 py-1 text-sm',
    lg: 'px-3 py-1.5 text-base',
  };

  return (
    <span
      className={cn(
        'cefr-badge',
        getCEFRColor(level),
        sizeClasses[size],
        className
      )}
    >
      {level}
    </span>
  );
};
