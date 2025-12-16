import { Users, BookOpen, Award, TrendingUp } from 'lucide-react';
import { useCountUp } from '@/hooks/useScrollAnimation';
import { cn } from '@/lib/utils';

const stats = [
  { label: 'Questions', value: 4000, suffix: '+', icon: BookOpen, color: 'text-primary' },
  { label: 'Students', value: 500, suffix: '+', icon: Users, color: 'text-green-500' },
  { label: 'CEFR Levels', value: 6, suffix: '', icon: Award, color: 'text-yellow-500' },
  { label: 'Avg. Improvement', value: 40, suffix: '%', icon: TrendingUp, color: 'text-blue-500' },
];

const StatCounter = ({ 
  value, 
  suffix, 
  label, 
  icon: Icon, 
  color,
  delay = 0 
}: { 
  value: number; 
  suffix: string; 
  label: string; 
  icon: React.ElementType;
  color: string;
  delay?: number;
}) => {
  const { count, ref, isInView } = useCountUp(value, 2000);

  return (
    <div 
      ref={ref}
      className={cn(
        "text-center transition-all duration-700",
        isInView ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
      )}
      style={{ transitionDelay: `${delay}ms` }}
    >
      <div className={cn("w-10 h-10 sm:w-12 sm:h-12 rounded-xl mx-auto mb-2 flex items-center justify-center bg-muted/80", color)}>
        <Icon className="w-5 h-5 sm:w-6 sm:h-6" />
      </div>
      <p className="text-2xl sm:text-3xl md:text-4xl font-bold text-foreground">
        {count.toLocaleString()}{suffix}
      </p>
      <p className="text-xs sm:text-sm text-muted-foreground mt-1">{label}</p>
    </div>
  );
};

export const SocialProofSection = () => {
  return (
    <section className="py-8 sm:py-12 md:py-16 border-b bg-gradient-to-b from-background to-muted/30">
      <div className="container px-4">
        <p className="text-center text-sm sm:text-base text-muted-foreground mb-6 sm:mb-8">
          Join <span className="font-semibold text-foreground">500+ learners</span> improving their English every day
        </p>
        
        <div className="grid grid-cols-2 md:grid-cols-4 gap-6 sm:gap-8">
          {stats.map((stat, index) => (
            <StatCounter
              key={stat.label}
              value={stat.value}
              suffix={stat.suffix}
              label={stat.label}
              icon={stat.icon}
              color={stat.color}
              delay={index * 100}
            />
          ))}
        </div>
      </div>
    </section>
  );
};
