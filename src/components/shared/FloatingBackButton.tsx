import { useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';

interface FloatingBackButtonProps {
  to?: string;
  label?: string;
}

export const FloatingBackButton = ({ 
  to = '/dashboard', 
  label = 'Dashboard' 
}: FloatingBackButtonProps) => {
  const navigate = useNavigate();

  const handleClick = () => {
    // Trigger haptic feedback on supported devices
    if ('vibrate' in navigator) {
      navigator.vibrate(10);
    }
    navigate(to);
  };

  return (
    <Button 
      onClick={handleClick}
      size="sm" 
      className="fixed bottom-4 left-4 z-50 md:hidden h-12 px-4 rounded-full shadow-lg bg-primary hover:bg-primary/90 gap-2"
    >
      <ArrowLeft className="w-4 h-4" />
      <span className="text-sm font-medium">{label}</span>
    </Button>
  );
};
