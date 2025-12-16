import { useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { BookOpen, Mail, Lock, User, Loader2, Eye, EyeOff } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';

import { useToast } from '@/hooks/use-toast';
import { useAuth } from '@/contexts/AuthContext';
import { supabase } from '@/integrations/supabase/client';
import { z } from 'zod';

const signInSchema = z.object({
  email: z.string().email('Please enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
});

const signUpSchema = z.object({
  email: z.string().email('Please enter a valid email'),
  password: z.string().min(6, 'Password must be at least 6 characters'),
  username: z.string().min(3, 'Username must be at least 3 characters').max(20),
});

const Auth = () => {
  const [isLoading, setIsLoading] = useState(false);
  const [signInData, setSignInData] = useState({ email: '', password: '' });
  const [signUpData, setSignUpData] = useState({ email: '', password: '', username: '' });
  const [showSignInPassword, setShowSignInPassword] = useState(false);
  const [showSignUpPassword, setShowSignUpPassword] = useState(false);
  const [forgotPasswordEmail, setForgotPasswordEmail] = useState('');
  const [showForgotPassword, setShowForgotPassword] = useState(false);
  const { user, signIn, signUp } = useAuth();
  const { toast } = useToast();
  const location = useLocation();

  const from = location.state?.from?.pathname || '/dashboard';

  if (user) {
    return <Navigate to={from} replace />;
  }

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const validated = signInSchema.parse(signInData);
      const { error } = await signIn(validated.email, validated.password);
      
      if (error) {
        toast({
          title: 'Sign in failed',
          description: error.message === 'Invalid login credentials' 
            ? 'Invalid email or password. Please try again.'
            : error.message,
          variant: 'destructive',
        });
      }
    } catch (err) {
      if (err instanceof z.ZodError) {
        toast({
          title: 'Validation error',
          description: err.errors[0].message,
          variant: 'destructive',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleSignUp = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const validated = signUpSchema.parse(signUpData);
      const { error } = await signUp(validated.email, validated.password, validated.username);
      
      if (error) {
        const errorMessage = error.message.includes('already registered')
          ? 'This email is already registered. Please sign in instead.'
          : error.message;
        
        toast({
          title: 'Sign up failed',
          description: errorMessage,
          variant: 'destructive',
        });
      } else {
        toast({
          title: 'Welcome!',
          description: 'Your account has been created successfully.',
        });
      }
    } catch (err) {
      if (err instanceof z.ZodError) {
        toast({
          title: 'Validation error',
          description: err.errors[0].message,
          variant: 'destructive',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleForgotPassword = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsLoading(true);

    try {
      const emailSchema = z.string().email('Please enter a valid email');
      emailSchema.parse(forgotPasswordEmail);

      const { error } = await supabase.auth.resetPasswordForEmail(forgotPasswordEmail, {
        redirectTo: `${window.location.origin}/reset-password`,
      });

      if (error) {
        toast({
          title: 'Error',
          description: error.message,
          variant: 'destructive',
        });
      } else {
        toast({
          title: 'Check your email',
          description: 'We sent you a password reset link.',
        });
        setShowForgotPassword(false);
        setForgotPasswordEmail('');
      }
    } catch (err) {
      if (err instanceof z.ZodError) {
        toast({
          title: 'Validation error',
          description: err.errors[0].message,
          variant: 'destructive',
        });
      }
    } finally {
      setIsLoading(false);
    }
  };

  // OAuth sign-in removed - students use email/password only

  if (showForgotPassword) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/5 via-background to-accent/5 px-4 py-8 overflow-x-hidden">
        <div className="w-full max-w-md animate-fade-in">
          <div className="flex justify-center mb-6 md:mb-8">
            <div className="flex items-center gap-2 md:gap-3">
              <div className="flex items-center justify-center w-10 h-10 md:w-12 md:h-12 rounded-xl bg-primary shadow-glow">
                <BookOpen className="w-5 h-5 md:w-7 md:h-7 text-primary-foreground" />
              </div>
              <div>
                <h1 className="font-serif text-xl md:text-2xl font-bold">CEFR Practice</h1>
                <p className="text-xs md:text-sm text-muted-foreground">Master English, level by level</p>
              </div>
            </div>
          </div>

          <Card className="shadow-lg w-full max-w-full">
            <CardHeader className="p-4 md:p-6">
              <CardTitle className="text-lg md:text-xl">Reset Password</CardTitle>
              <CardDescription className="text-sm">
                Enter your email and we'll send you a reset link
              </CardDescription>
            </CardHeader>
            <CardContent className="p-4 md:p-6 pt-0">
              <form onSubmit={handleForgotPassword} className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="forgot-email" className="text-sm">Email</Label>
                  <div className="relative">
                    <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      id="forgot-email"
                      type="email"
                      placeholder="you@example.com"
                      className="pl-10 min-h-[44px]"
                      value={forgotPasswordEmail}
                      onChange={(e) => setForgotPasswordEmail(e.target.value)}
                      required
                    />
                  </div>
                </div>

                <Button type="submit" className="w-full min-h-[48px]" disabled={isLoading}>
                  {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                  Send Reset Link
                </Button>

                <Button
                  type="button"
                  variant="ghost"
                  className="w-full"
                  onClick={() => setShowForgotPassword(false)}
                >
                  Back to Sign In
                </Button>
              </form>
            </CardContent>
          </Card>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary/5 via-background to-accent/5 px-4 py-8 overflow-x-hidden">
      <div className="w-full max-w-md animate-fade-in">
        <div className="flex justify-center mb-6 md:mb-8">
          <div className="flex items-center gap-2 md:gap-3">
            <div className="flex items-center justify-center w-10 h-10 md:w-12 md:h-12 rounded-xl bg-primary shadow-glow">
              <BookOpen className="w-5 h-5 md:w-7 md:h-7 text-primary-foreground" />
            </div>
            <div>
              <h1 className="font-serif text-xl md:text-2xl font-bold">CEFR Practice</h1>
              <p className="text-xs md:text-sm text-muted-foreground">Master English, level by level</p>
            </div>
          </div>
        </div>

        <Card className="shadow-lg w-full max-w-full">
          <Tabs defaultValue="signin" className="w-full">
            <CardHeader className="pb-4 p-4 md:p-6">
              <TabsList className="grid w-full grid-cols-2">
                <TabsTrigger value="signin" className="text-sm md:text-base">Sign In</TabsTrigger>
                <TabsTrigger value="signup" className="text-sm md:text-base">Sign Up</TabsTrigger>
              </TabsList>
            </CardHeader>

            <CardContent className="p-4 md:p-6 pt-0">
              <TabsContent value="signin" className="mt-0">
                <CardTitle className="text-lg md:text-xl mb-2">Welcome back</CardTitle>
                <CardDescription className="mb-4 md:mb-6 text-sm">
                  Sign in to continue your learning journey
                </CardDescription>

                
                <form onSubmit={handleSignIn} className="space-y-3 md:space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="signin-email" className="text-sm">Email</Label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="signin-email"
                        type="email"
                        placeholder="you@example.com"
                        className="pl-10 min-h-[44px]"
                        value={signInData.email}
                        onChange={(e) => setSignInData({ ...signInData, email: e.target.value })}
                        required
                      />
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <Label htmlFor="signin-password" className="text-sm">Password</Label>
                      <Button
                        type="button"
                        variant="link"
                        className="px-0 h-auto text-xs text-muted-foreground hover:text-primary"
                        onClick={() => setShowForgotPassword(true)}
                      >
                        Forgot password?
                      </Button>
                    </div>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="signin-password"
                        type={showSignInPassword ? 'text' : 'password'}
                        placeholder="••••••••"
                        className="pl-10 pr-10 min-h-[44px]"
                        value={signInData.password}
                        onChange={(e) => setSignInData({ ...signInData, password: e.target.value })}
                        required
                      />
                      <button
                        type="button"
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                        onClick={() => setShowSignInPassword(!showSignInPassword)}
                      >
                        {showSignInPassword ? (
                          <EyeOff className="w-4 h-4" />
                        ) : (
                          <Eye className="w-4 h-4" />
                        )}
                      </button>
                    </div>
                  </div>

                  <Button type="submit" className="w-full min-h-[48px]" disabled={isLoading}>
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Sign In
                  </Button>
                </form>
              </TabsContent>

              <TabsContent value="signup" className="mt-0">
                <CardTitle className="text-lg md:text-xl mb-2">Create account</CardTitle>
                <CardDescription className="mb-4 md:mb-6 text-sm">
                  Start your English learning journey today
                </CardDescription>

                
                <form onSubmit={handleSignUp} className="space-y-3 md:space-y-4">
                  <div className="space-y-2">
                    <Label htmlFor="signup-username" className="text-sm">Username</Label>
                    <div className="relative">
                      <User className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="signup-username"
                        type="text"
                        placeholder="yourname"
                        className="pl-10 min-h-[44px]"
                        value={signUpData.username}
                        onChange={(e) => setSignUpData({ ...signUpData, username: e.target.value })}
                        required
                      />
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label htmlFor="signup-email" className="text-sm">Email</Label>
                    <div className="relative">
                      <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="signup-email"
                        type="email"
                        placeholder="you@example.com"
                        className="pl-10 min-h-[44px]"
                        value={signUpData.email}
                        onChange={(e) => setSignUpData({ ...signUpData, email: e.target.value })}
                        required
                      />
                    </div>
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="signup-password" className="text-sm">Password</Label>
                    <div className="relative">
                      <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                      <Input
                        id="signup-password"
                        type={showSignUpPassword ? 'text' : 'password'}
                        placeholder="••••••••"
                        className="pl-10 pr-10 min-h-[44px]"
                        value={signUpData.password}
                        onChange={(e) => setSignUpData({ ...signUpData, password: e.target.value })}
                        required
                      />
                      <button
                        type="button"
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                        onClick={() => setShowSignUpPassword(!showSignUpPassword)}
                      >
                        {showSignUpPassword ? (
                          <EyeOff className="w-4 h-4" />
                        ) : (
                          <Eye className="w-4 h-4" />
                        )}
                      </button>
                    </div>
                  </div>

                  <Button type="submit" className="w-full min-h-[48px]" disabled={isLoading}>
                    {isLoading && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
                    Create Account
                  </Button>
                </form>
              </TabsContent>
            </CardContent>
          </Tabs>
        </Card>
      </div>
    </div>
  );
};

export default Auth;
