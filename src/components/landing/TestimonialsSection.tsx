import { Star, Quote } from 'lucide-react';
import { Card, CardContent } from '@/components/ui/card';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useScrollAnimation } from '@/hooks/useScrollAnimation';
import { cn } from '@/lib/utils';
import type { CEFRLevel } from '@/lib/constants';

const testimonials = [
  {
    name: 'Maria Santos',
    avatar: 'MS',
    level: 'B1' as CEFRLevel,
    previousLevel: 'A1' as CEFRLevel,
    quote: "This app helped me improve from A1 to B1 in just 3 months! The Oxford-style questions are perfect for exam preparation.",
    rating: 5,
  },
  {
    name: 'Ahmed Hassan',
    avatar: 'AH',
    level: 'B2' as CEFRLevel,
    previousLevel: 'A2' as CEFRLevel,
    quote: "I love how I can track my progress and earn XP. It makes learning feel like a game. Highly recommended!",
    rating: 5,
  },
  {
    name: 'Sophie Chen',
    avatar: 'SC',
    level: 'C1' as CEFRLevel,
    previousLevel: 'B1' as CEFRLevel,
    quote: "The variety of questions and instant feedback helped me understand my mistakes. Now I feel confident in my English skills.",
    rating: 5,
  },
];

const TestimonialCard = ({ 
  testimonial, 
  index 
}: { 
  testimonial: typeof testimonials[0];
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
      <Card className="h-full relative overflow-hidden group hover:shadow-lg transition-shadow">
        <div className="absolute top-0 right-0 w-20 h-20 bg-primary/5 rounded-bl-full" />
        <CardContent className="p-4 sm:p-6">
          {/* Quote icon */}
          <Quote className="w-8 h-8 text-primary/20 mb-3" />
          
          {/* Rating */}
          <div className="flex gap-0.5 mb-3">
            {Array.from({ length: testimonial.rating }).map((_, i) => (
              <Star key={i} className="w-4 h-4 fill-yellow-400 text-yellow-400" />
            ))}
          </div>
          
          {/* Quote text */}
          <blockquote className="text-sm sm:text-base text-muted-foreground mb-4 leading-relaxed">
            &ldquo;{testimonial.quote}&rdquo;
          </blockquote>
          
          {/* Author */}
          <div className="flex items-center gap-3 pt-4 border-t">
            <Avatar className="w-10 h-10 sm:w-12 sm:h-12 border-2 border-primary/20">
              <AvatarFallback className="bg-primary/10 text-primary font-semibold text-sm">
                {testimonial.avatar}
              </AvatarFallback>
            </Avatar>
            <div className="flex-1 min-w-0">
              <p className="font-semibold text-sm sm:text-base truncate">{testimonial.name}</p>
              <div className="flex items-center gap-1.5 text-xs sm:text-sm text-muted-foreground">
                <span>{testimonial.previousLevel}</span>
                <span>→</span>
                <CEFRBadge level={testimonial.level} size="sm" />
              </div>
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};

export const TestimonialsSection = () => {
  const { ref: headerRef, isInView: headerInView } = useScrollAnimation();

  return (
    <section className="py-12 md:py-20 bg-muted/30">
      <div className="container px-4">
        <div 
          ref={headerRef}
          className={cn(
            "text-center mb-8 md:mb-12 transition-all duration-700",
            headerInView ? "opacity-100 translate-y-0" : "opacity-0 translate-y-4"
          )}
        >
          <h2 className="font-serif text-2xl md:text-3xl font-bold mb-3 md:mb-4">
            What Our Students Say
          </h2>
          <p className="text-sm md:text-base text-muted-foreground max-w-2xl mx-auto">
            Real stories from learners who improved their English with CEFR Practice
          </p>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4 md:gap-6">
          {testimonials.map((testimonial, index) => (
            <TestimonialCard 
              key={testimonial.name} 
              testimonial={testimonial} 
              index={index}
            />
          ))}
        </div>
      </div>
    </section>
  );
};
