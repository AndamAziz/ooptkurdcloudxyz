import { Link } from 'react-router-dom';
import { 
  BookOpen, 
  Trophy, 
  Target, 
  Users, 
  ArrowRight,
  CheckCircle,
  Sparkles
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { DonationButton } from '@/components/DonationButton';
import { useAuth } from '@/contexts/AuthContext';
import { CEFR_LEVELS, LEVEL_DESCRIPTIONS, type CEFRLevel } from '@/lib/constants';

const Index = () => {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      {/* Hero Section */}
      <section className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-accent/5" />
        <div className="container relative py-20 lg:py-32">
          <div className="max-w-3xl mx-auto text-center animate-fade-in">
            <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-primary/10 text-primary text-sm font-medium mb-6">
              <Sparkles className="w-4 h-4" />
              4000+ Oxford-Style Questions
            </div>
            
            <h1 className="font-serif text-4xl md:text-5xl lg:text-6xl font-bold mb-6 leading-tight">
              Master English with{' '}
              <span className="gradient-text">CEFR Practice</span>
            </h1>
            
            <p className="text-xl text-muted-foreground mb-8 max-w-2xl mx-auto">
              Level up your English skills from A1 to C2 with our comprehensive question bank. 
              Track your progress, earn XP, and watch yourself improve.
            </p>
            
            <div className="flex flex-col sm:flex-row gap-4 justify-center">
              {user ? (
                <Button asChild size="lg" className="text-lg px-8">
                  <Link to="/dashboard">
                    Go to Dashboard
                    <ArrowRight className="ml-2 w-5 h-5" />
                  </Link>
                </Button>
              ) : (
                <>
                  <Button asChild size="lg" className="text-lg px-8">
                    <Link to="/auth">
                      Start Learning Free
                      <ArrowRight className="ml-2 w-5 h-5" />
                    </Link>
                  </Button>
                  <Button asChild variant="outline" size="lg" className="text-lg px-8">
                    <Link to="/auth">Sign In</Link>
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      </section>

      {/* Features Section */}
      <section className="py-20 bg-muted/50">
        <div className="container">
          <div className="text-center mb-12">
            <h2 className="font-serif text-3xl font-bold mb-4">Why Choose CEFR Practice?</h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">
              Our platform is designed to help you improve systematically
            </p>
          </div>
          
          <div className="grid md:grid-cols-3 gap-6">
            <Card className="card-hover">
              <CardContent className="p-6 text-center">
                <div className="w-14 h-14 rounded-2xl bg-primary/10 flex items-center justify-center mx-auto mb-4">
                  <Target className="w-7 h-7 text-primary" />
                </div>
                <h3 className="font-semibold text-lg mb-2">Targeted Practice</h3>
                <p className="text-muted-foreground">
                  Questions organized by CEFR levels A1-C2. Focus on your current level or challenge yourself.
                </p>
              </CardContent>
            </Card>
            
            <Card className="card-hover">
              <CardContent className="p-6 text-center">
                <div className="w-14 h-14 rounded-2xl bg-success/10 flex items-center justify-center mx-auto mb-4">
                  <Trophy className="w-7 h-7 text-success" />
                </div>
                <h3 className="font-semibold text-lg mb-2">XP & Level Up</h3>
                <p className="text-muted-foreground">
                  Earn experience points with every question. Level up automatically as you improve.
                </p>
              </CardContent>
            </Card>
            
            <Card className="card-hover">
              <CardContent className="p-6 text-center">
                <div className="w-14 h-14 rounded-2xl bg-warning/10 flex items-center justify-center mx-auto mb-4">
                  <BookOpen className="w-7 h-7 text-warning" />
                </div>
                <h3 className="font-semibold text-lg mb-2">Oxford-Style Questions</h3>
                <p className="text-muted-foreground">
                  Practice with questions following the respected Oxford English testing format.
                </p>
              </CardContent>
            </Card>
          </div>
        </div>
      </section>

      {/* CEFR Levels Section */}
      <section className="py-20">
        <div className="container">
          <div className="text-center mb-12">
            <h2 className="font-serif text-3xl font-bold mb-4">CEFR Levels Explained</h2>
            <p className="text-muted-foreground max-w-2xl mx-auto">
              The Common European Framework of Reference for Languages
            </p>
          </div>
          
          <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
            {CEFR_LEVELS.map((level) => (
              <Card key={level} className="card-hover">
                <CardContent className="p-5">
                  <div className="flex items-start gap-4">
                    <CEFRBadge level={level} size="lg" />
                    <div>
                      <h3 className="font-semibold mb-1">Level {level}</h3>
                      <p className="text-sm text-muted-foreground">
                        {LEVEL_DESCRIPTIONS[level]}
                      </p>
                    </div>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </section>

      {/* CTA Section */}
      <section className="py-20 bg-primary text-primary-foreground">
        <div className="container text-center">
          <h2 className="font-serif text-3xl font-bold mb-4">
            Ready to Improve Your English?
          </h2>
          <p className="text-primary-foreground/80 mb-8 max-w-xl mx-auto">
            Join thousands of learners who are improving their English skills every day.
          </p>
          <div className="flex flex-col sm:flex-row gap-4 justify-center">
            {!user && (
              <Button asChild size="lg" variant="secondary" className="text-lg px-8">
                <Link to="/auth">
                  Get Started Free
                  <ArrowRight className="ml-2 w-5 h-5" />
                </Link>
              </Button>
            )}
            <DonationButton 
              variant="outline" 
              size="lg" 
              className="border-primary-foreground/20 text-primary-foreground hover:bg-primary-foreground/10"
            />
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="py-8 border-t">
        <div className="container flex flex-col md:flex-row items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <BookOpen className="w-5 h-5 text-primary" />
            <span className="font-semibold">CEFR Practice</span>
          </div>
          <p className="text-sm text-muted-foreground">
            © {new Date().getFullYear()} CEFR Practice. All rights reserved.
          </p>
          <DonationButton variant="ghost" size="sm" />
        </div>
      </footer>
    </div>
  );
};

export default Index;
