import { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BookOpen, LogOut, User, LayoutDashboard, Trophy, Menu, Award, Zap } from 'lucide-react';
import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Separator } from '@/components/ui/separator';
import { DonationButton } from './DonationButton';
import { CEFRBadge } from './CEFRBadge';
import { ThemeToggle } from './ThemeToggle';
import { useAuth } from '@/contexts/AuthContext';
import { useQuestionProgress } from '@/hooks/useQuestionProgress';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';

// Threshold for considering a level "completed" (accuracy percentage)
const LEVEL_COMPLETION_THRESHOLD = 60;

export const Header = () => {
  const { user, profile, isAdmin, signOut } = useAuth();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { progress: questionProgress } = useQuestionProgress(user?.id);

  // Calculate the effective current level based on question progress
  const effectiveCurrentLevel = useMemo((): CEFRLevel => {
    if (!questionProgress || questionProgress.length === 0) {
      return (profile?.current_cefr as CEFRLevel) || 'A1';
    }

    // Use the same logic as LevelProgressCard/Dashboard for consistency
    // Requires 10+ attempts AND 60%+ accuracy to unlock next level
    const isLevelUnlocked = (index: number): boolean => {
      if (index === 0) return true;
      const prevLevelName = CEFR_LEVELS[index - 1];
      const prevLevel = questionProgress.find(p => p.cefr === prevLevelName);
      if (!prevLevel) return false;
      return prevLevel.answered_questions >= 10 && prevLevel.accuracy >= LEVEL_COMPLETION_THRESHOLD;
    };

    const isLevelCompleted = (index: number): boolean => {
      if (!isLevelUnlocked(index)) return false;
      const levelName = CEFR_LEVELS[index];
      const level = questionProgress.find(p => p.cefr === levelName);
      if (!level) return false;
      return level.answered_questions >= 10 && level.accuracy >= LEVEL_COMPLETION_THRESHOLD;
    };

    // Find the first unlocked level that isn't completed
    for (let i = 0; i < CEFR_LEVELS.length; i++) {
      if (!isLevelUnlocked(i)) {
        return CEFR_LEVELS[Math.max(0, i - 1)];
      }
      if (!isLevelCompleted(i)) {
        return CEFR_LEVELS[i];
      }
    }
    
    // All levels completed, return last level
    return CEFR_LEVELS[CEFR_LEVELS.length - 1];
  }, [questionProgress, profile?.current_cefr]);

  const handleSignOut = async () => {
    await signOut();
    navigate('/auth');
    setMobileMenuOpen(false);
  };

  const getInitials = () => {
    if (profile?.display_name) {
      return profile.display_name.substring(0, 2).toUpperCase();
    }
    if (profile?.username) {
      return profile.username.substring(0, 2).toUpperCase();
    }
    if (user?.email) {
      return user.email.substring(0, 2).toUpperCase();
    }
    return 'U';
  };

  const closeMobileMenu = () => setMobileMenuOpen(false);

  return (
    <header className="sticky top-0 z-50 w-full border-b bg-card/95 backdrop-blur supports-[backdrop-filter]:bg-card/60">
      <div className="container flex h-14 md:h-16 items-center justify-between">
        {/* Logo */}
        <Link to="/" className="flex items-center gap-2">
          <div className="flex items-center justify-center w-8 h-8 md:w-10 md:h-10 rounded-lg bg-primary">
            <BookOpen className="w-4 h-4 md:w-6 md:h-6 text-primary-foreground" />
          </div>
          <span className="font-serif text-lg md:text-xl font-bold hidden sm:inline">CEFR Practice</span>
          <span className="font-serif text-lg font-bold sm:hidden">CEFR</span>
        </Link>

        {/* Desktop Navigation */}
        <nav className="hidden md:flex items-center gap-4">
          <Button asChild variant="ghost" size="sm">
            <Link to="/leaderboard">
              <Trophy className="w-4 h-4 mr-2" />
              Leaderboard
            </Link>
          </Button>
          {user && (
            <Button asChild variant="ghost" size="sm">
              <Link to="/certificates">
                <Award className="w-4 h-4 mr-2" />
                Certificates
              </Link>
            </Button>
          )}
          <ThemeToggle />
          
          {user ? (
            <div className="flex items-center gap-3">
              {user && (
                <CEFRBadge level={effectiveCurrentLevel} size="sm" />
              )}
              
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button variant="ghost" className="relative h-9 w-9 rounded-full">
                    <Avatar className="h-9 w-9">
                      <AvatarFallback className="bg-primary text-primary-foreground">
                        {getInitials()}
                      </AvatarFallback>
                    </Avatar>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent className="w-56 bg-popover" align="end">
                  <div className="flex items-center justify-start gap-2 p-2">
                    <div className="flex flex-col space-y-1 leading-none">
                      {profile?.display_name && (
                        <p className="font-medium">{profile.display_name}</p>
                      )}
                      <p className="text-sm text-muted-foreground">{user.email}</p>
                    </div>
                  </div>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/dashboard" className="cursor-pointer">
                      <User className="mr-2 h-4 w-4" />
                      Dashboard
                    </Link>
                  </DropdownMenuItem>
                  <DropdownMenuItem asChild>
                    <Link to="/certificates" className="cursor-pointer">
                      <Award className="mr-2 h-4 w-4" />
                      Certificates
                    </Link>
                  </DropdownMenuItem>
                  {isAdmin && (
                    <DropdownMenuItem asChild>
                      <Link to="/admin" className="cursor-pointer">
                        <LayoutDashboard className="mr-2 h-4 w-4" />
                        Admin Panel
                      </Link>
                    </DropdownMenuItem>
                  )}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem onClick={handleSignOut} className="cursor-pointer">
                    <LogOut className="mr-2 h-4 w-4" />
                    Sign out
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          ) : (
            <Button asChild>
              <Link to="/auth">Sign In</Link>
            </Button>
          )}
        </nav>

        {/* Mobile Navigation */}
        <div className="flex md:hidden items-center gap-2">
          {user && (
            <CEFRBadge level={effectiveCurrentLevel} size="sm" />
          )}
          
          <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="h-10 w-10">
                <Menu className="h-5 w-5" />
                <span className="sr-only">Toggle menu</span>
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[300px] sm:w-[320px] bg-card p-0">
              <SheetHeader className="p-4 pb-0">
                <SheetTitle className="text-left flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-primary" />
                  CEFR Practice
                </SheetTitle>
              </SheetHeader>
              
              <nav className="flex flex-col mt-4">
                {user ? (
                  <>
                    {/* Enhanced Profile Section */}
                    <div className="px-4 pb-4">
                      <div className="flex items-start gap-3 p-3 rounded-xl bg-gradient-to-br from-muted/80 to-muted/40 border">
                        <Avatar className="h-12 w-12 border-2 border-primary/20">
                          <AvatarFallback className="bg-primary text-primary-foreground text-lg">
                            {getInitials()}
                          </AvatarFallback>
                        </Avatar>
                        <div className="flex-1 min-w-0">
                          <p className="font-semibold text-sm truncate">
                            {profile?.display_name || profile?.username || 'Student'}
                          </p>
                          <p className="text-xs text-muted-foreground truncate">{user.email}</p>
                          <div className="flex items-center gap-2 mt-1.5">
                            <CEFRBadge level={effectiveCurrentLevel} size="sm" />
                            <div className="flex items-center gap-1 text-xs text-muted-foreground">
                              <Zap className="w-3 h-3 text-primary" />
                              <span className="font-medium text-foreground">{profile?.xp || 0}</span>
                              <span>XP</span>
                            </div>
                          </div>
                        </div>
                      </div>
                    </div>
                    
                    <Separator />
                    
                    {/* Navigation Links */}
                    <div className="p-2">
                      <Button asChild variant="ghost" className="w-full justify-start h-12 px-4" onClick={closeMobileMenu}>
                        <Link to="/dashboard">
                          <User className="mr-3 h-5 w-5" />
                          Dashboard
                        </Link>
                      </Button>
                      
                      <Button asChild variant="ghost" className="w-full justify-start h-12 px-4" onClick={closeMobileMenu}>
                        <Link to="/certificates">
                          <Award className="mr-3 h-5 w-5" />
                          Certificates
                        </Link>
                      </Button>
                      
                      <Button asChild variant="ghost" className="w-full justify-start h-12 px-4" onClick={closeMobileMenu}>
                        <Link to="/leaderboard">
                          <Trophy className="mr-3 h-5 w-5" />
                          Leaderboard
                        </Link>
                      </Button>
                      
                      {isAdmin && (
                        <Button asChild variant="ghost" className="w-full justify-start h-12 px-4" onClick={closeMobileMenu}>
                          <Link to="/admin">
                            <LayoutDashboard className="mr-3 h-5 w-5" />
                            Admin Panel
                          </Link>
                        </Button>
                      )}
                    </div>
                    
                    <Separator />
                    
                    {/* Support & Settings */}
                    <div className="p-2">
                      <DonationButton variant="ghost" className="w-full justify-start h-12 px-4" />

                      <div className="flex items-center justify-between h-12 px-4 rounded-md hover:bg-muted/50">
                        <span className="text-sm flex items-center gap-3">
                          <span className="text-lg">🌙</span>
                          Dark Mode
                        </span>
                        <ThemeToggle />
                      </div>
                    </div>
                    
                    <Separator />
                    
                    {/* Sign Out */}
                    <div className="p-2">
                      <Button 
                        variant="ghost" 
                        className="w-full justify-start h-12 px-4 text-destructive hover:text-destructive hover:bg-destructive/10" 
                        onClick={handleSignOut}
                      >
                        <LogOut className="mr-3 h-5 w-5" />
                        Sign out
                      </Button>
                    </div>
                  </>
                ) : (
                  <>
                    <div className="p-2">
                      <Button asChild variant="ghost" className="w-full justify-start h-12 px-4" onClick={closeMobileMenu}>
                        <Link to="/leaderboard">
                          <Trophy className="mr-3 h-5 w-5" />
                          Leaderboard
                        </Link>
                      </Button>
                      
                      <DonationButton variant="ghost" className="w-full justify-start h-12 px-4" />
                    </div>
                    
                    <Separator />
                    
                    <div className="p-4">
                      <Button asChild className="w-full h-12" onClick={closeMobileMenu}>
                        <Link to="/auth">Sign In</Link>
                      </Button>
                    </div>
                  </>
                )}
              </nav>
            </SheetContent>
          </Sheet>
        </div>
      </div>
    </header>
  );
};
