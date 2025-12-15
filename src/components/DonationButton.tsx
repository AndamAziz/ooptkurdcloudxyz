import { Heart } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { PAYPAL_DONATION_URL } from '@/lib/constants';

interface DonationButtonProps {
  variant?: 'default' | 'outline' | 'ghost';
  size?: 'sm' | 'default' | 'lg';
  showIcon?: boolean;
  className?: string;
}

export const DonationButton = ({ 
  variant = 'outline', 
  size = 'default',
  showIcon = true,
  className 
}: DonationButtonProps) => {
  const handleDonate = () => {
    window.open(PAYPAL_DONATION_URL, '_blank', 'noopener,noreferrer');
  };

  return (
    <Button
      variant={variant}
      size={size}
      onClick={handleDonate}
      className={className}
    >
      {showIcon && <Heart className="w-4 h-4 mr-2" />}
      Support Our Work
    </Button>
  );
};
