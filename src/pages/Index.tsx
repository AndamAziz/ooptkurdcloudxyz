import { Link } from 'react-router-dom';
import { 
  BookOpen, 
  Trophy, 
  Target, 
  ArrowRight,
  Sparkles,
  ChevronRight
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { DonationButton } from '@/components/DonationButton';
import { SocialProofSection } from '@/components/landing/SocialProofSection';
import { TestimonialsSection } from '@/components/landing/TestimonialsSection';
import { useAuth } from '@/contexts/AuthContext';
import { useScrollAnimation } from '@/hooks/useScrollAnimation';
import { CEFR_LEVELS, LEVEL_DESCRIPTIONS, type CEFRLevel } from '@/lib/constants';
import { cn } from '@/lib/utils';

// Animated Feature Card component
const FeatureCard = ({ 
  icon: Icon, 
  title, 
  description, 
  colorClass,
  index 
}: { 
  icon: React.ElementType; 
  title: string; 
  description: string;
  colorClass: string;
  index: number;
}) => {
  const { ref, isInView } = useScrollAnimation({ threshold: 0.2 });

  return (
    <div
      ref={ref}
      className={cn(
        "transition-all duration-700",
        isInView ? "opacity-100 translate-y-0" : "opacity-0 translate-y-8"
      )}
      style={{ transitionDelay: `${index * 150}ms` }}
    >
      <Card className="card-hover h-full">
        <CardContent className="p-4 md:p-6 text-center">
          <div className={cn(
            "w-12 h-12 md:w-14 md:h-14 rounded-2xl flex items-center justify-center mx-auto mb-3 md:mb-4",
            colorClass
          )}>
            <Icon className="w-6 h-6 md:w-7 md:h-7" />
          </div>
          <h3 className="font-semibold text-base md:text-lg mb-2">{title}</h3>
          <p className="text-sm md:text-base text-muted-foreground">{description}</p>
        </CardContent>
      </Card>
    </div>
  );
};

// Animated Level Card component
const LevelCard = ({ 
  level, 
  index 
}: { 
  level: CEFRLevel; 
  index: number;
}) => {
  const { ref, isInView } = useScrollAnimation({ threshold: 0.1 });

  return (
    <div
      ref={ref}
      className={cn(
        "transition-all duration-500",
        isInView ? "opacity-100 translate-x-0" : "opacity-0 -translate-x-8"
      )}
      style={{ transitionDelay: `${index * 100}ms` }}
    >
      <Card className="card-hover">
        <CardContent className="p-4 md:p-5">
          <div className="flex items-start gap-3 md:gap-4">
            <CEFRBadge level={level} size="lg" />
            <div>
              <h3 className="font-semibold mb-1 text-sm md:text-base">Level {level}</h3>
              <p className="text-xs md:text-sm text-muted-foreground">
                {LEVEL_DESCRIPTIONS[level]}
              </p>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

// Animated CTA Button with pulse and arrow animation
const AnimatedCTAButton = ({ user }: { user: boolean }) => {
  if (user) return null;

  return (
    <Button 
      asChild 
      size="lg" 
      variant="secondary" 
      className="group relative text-base md:text-lg px-6 md:px-8 min-h-[52px] w-full sm:w-auto overflow-hidden animate-pulse-soft"
    >
      <Link to="/auth">
        <span className="relative z-10 flex items-center">
          Get Started Free
          <ChevronRight className="ml-2 w-4 h-4 md:w-5 md:h-5 transition-transform duration-300 group-hover:translate-x-1" />
        </span>
        {/* Shine effect on hover */}
        <span className="absolute inset-0 bg-gradient-to-r from-transparent via-white/20 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-700" />
      </Link>
    </Button>
  );
};

const Index = () => {
  const { user } = useAuth();
  const { ref: featuresHeaderRef, isInView: featuresHeaderInView } = useScrollAnimation();
  const { ref: levelsHeaderRef, isInView: levelsHeaderInView } = useScrollAnimation();
  const { ref: ctaRef, isInView: ctaInView } = useScrollAnimation();

  const features = [
    {
      icon: Target,
      title: 'Targeted Practice',
      description: 'Questions organized by CEFR levels A1-C2. Focus on your current level or challenge yourself.',
      colorClass: 'bg-primary/10 text-primary',
    },
    {
      icon: Trophy,
      title: 'XP & Level Up',
      description: 'Earn experience points with every question. Level up automatically as you improve.',
      colorClass: 'bg-green-500/10 text-green-500',
    },
    {
      icon: BookOpen,
      title: 'Oxford-Style Questions',
      description: 'Practice with questions following the respected Oxford English testing format.',
      colorClass: 'bg-yellow-500/10 text-yellow-500',
    },
  ];

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />
      
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-accent/5" />
        <div className="container relative py-12 md:py-20 lg:py-32 px-4">
          <div className="max-w-3xl mx-auto text-center animate-fade-in">
            <div className="inline-flex items-center gap-2 px-3 md:px-4 py-1.5 md:py-2 rounded-full bg-primary/10 text-primary text-xs md:text-sm font-medium mb-4 md:mb-6">
              <Sparkles className="w-3.5 h-3.5 md:w-4 md:h-4" />
              4000+ Oxford-Style Questions
            </div>
            
            <h1 className="font-serif text-3xl md:text-4xl lg:text-5xl xl:text-6xl font-bold mb-4 md:mb-6 leading-tight">
              Master English with{' '}
              <span className="gradient-text">CEFR Practice</span>
            </h1>
            
            <p className="text-base md:text-lg lg:text-xl text-muted-foreground mb-6 md:mb-8 max-w-2xl mx-auto px-4">
              Level up your English skills from A1 to C2 with our comprehensive question bank. 
              Track your progress, earn XP, and watch yourself improve.
            </p>
            
            <div className="flex flex-col sm:flex-row gap-3 md:gap-4 justify-center px-4">
              {user ? (
                <Button asChild size="lg" className="text-base md:text-lg px-6 md:px-8 min-h-[52px] w-full sm:w-auto">
                  <Link to="/dashboard">
                    Go to Dashboard
                    <ArrowRight className="ml-2 w-4 h-4 md:w-5 md:h-5" />
                  </Link>
                </Button>
              ) : (
                <>
                  <Button asChild size="lg" className="group text-base md:text-lg px-6 md:px-8 min-h-[52px] w-full sm:w-auto">
                    <Link to="/auth">
                      Start Learning Free
                      <ArrowRight className="ml-2 w-4 h-4 md:w-5 md:h-5 transition-transform group-hover:translate-x-1" />
                    </Link>
                  </Button>
                  <Button asChild variant="outline" size="lg" className="text-base md:text-lg px-6 md:px-8 min-h-[52px] w-full sm:w-auto">
                    <Link to="/auth">Sign In</Link>
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Social Proof Section */}
      <SocialProofSection />

      {/* Features Section */}
      <section className="py-12 md:py-20 bg-muted/50">
        <div className="container px-4">
          <div 
            ref={featuresHeaderRef}
            className={cn(
              "text-center mb-8 md:mb-12 transition-all duration-700",
              featuresHeaderInView ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
            )}
          >
            <h2 className="font-serif text-2xl md:text-3xl font-bold mb-3 md:mb-4">Why Choose CEFR Practice?</h2>
            <p className="text-sm md:text-base text-muted-foreground max-w-2xl mx-auto">
              Our platform is designed to help you improve systematically
            </p>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4 md:gap-6">
            {features.map((feature, index) => (
              <FeatureCard
                key={feature.title}
                icon={feature.icon}
                title={feature.title}
                description={feature.description}
                colorClass={feature.colorClass}
                index={index}
              />
            ))}
          </div>
        </div>
      </section>

      {/* CEFR Levels Section */}
      <section className="py-12 md:py-20">
        <div className="container px-4">
          <div 
            ref={levelsHeaderRef}
            className={cn(
              "text-center mb-8 md:mb-12 transition-all duration-700",
              levelsHeaderInView ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
            )}
          >
            <h2 className="font-serif text-2xl md:text-3xl font-bold mb-3 md:mb-4">CEFR Levels Explained</h2>
            <p className="text-sm md:text-base text-muted-foreground max-w-2xl mx-auto">
              The Common European Framework of Reference for Languages
            </p>
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 md:gap-4">
            {CEFR_LEVELS.map((level, index) => (
              <LevelCard key={level} level={level} index={index} />
            ))}
          </div>
        </div>
      </section>

      {/* Testimonials Section */}
      <TestimonialsSection />

      {/* CTA Section */}
      <section 
        ref={ctaRef}
        className={cn(
          "py-12 md:py-20 bg-primary text-primary-foreground transition-all duration-700",
          ctaInView ? "opacity-100" : "opacity-90"
        )}
      >
        <div className="container text-center px-4">
          <h2 className="font-serif text-2xl md:text-3xl font-bold mb-3 md:mb-4">
            Ready to Improve Your English?
          </h2>
          <p className="text-sm md:text-base text-primary-foreground/80 mb-6 md:mb-8 max-w-xl mx-auto">
            Join thousands of learners who are improving their English skills every day.
          </p>
          <div className="flex flex-col sm:flex-row gap-3 md:gap-4 justify-center">
            <AnimatedCTAButton user={!!user} />
            <DonationButton 
              variant="outline" 
              size="lg" 
              className="border-primary-foreground/20 text-primary-foreground hover:bg-primary-foreground/10 min-h-[52px] w-full sm:w-auto"
            />
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-6 md:py-8 border-t">
        <div className="container flex flex-col md:flex-row items-center justify-between gap-4 px-4">
          <div className="flex items-center gap-2">
            <BookOpen className="w-4 h-4 md:w-5 md:h-5 text-primary" />
            <span className="font-semibold text-sm md:text-base">CEFR Practice</span>
          </div>
          <p className="text-xs md:text-sm text-muted-foreground text-center">
            © {new Date().getFullYear()} CEFR Practice. All rights reserved.
          </p>
          <DonationButton variant="ghost" size="sm" />
        </div>
      </footer>
    </div>
  );
};

export default Index;
