import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { BookOpen, LogOut, User, LayoutDashboard, Trophy, Menu } from 'lucide-react';
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
import { DonationButton } from './DonationButton';
import { CEFRBadge } from './CEFRBadge';
import { ThemeToggle } from './ThemeToggle';
import { useAuth } from '@/contexts/AuthContext';
import type { CEFRLevel } from '@/lib/constants';

export const Header = () => {
  const { user, profile, isAdmin, signOut } = useAuth();
  const navigate = useNavigate();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

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
          <ThemeToggle />
          <DonationButton variant="ghost" size="sm" />
          
          {user ? (
            <div className="flex items-center gap-3">
              {profile && (
                <CEFRBadge level={profile.current_cefr as CEFRLevel} size="sm" />
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
          {user && profile && (
            <CEFRBadge level={profile.current_cefr as CEFRLevel} size="sm" />
          )}
          
          <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="h-10 w-10">
                <Menu className="h-5 w-5" />
                <span className="sr-only">Toggle menu</span>
              </Button>
            </SheetTrigger>
            <SheetContent side="right" className="w-[280px] bg-card">
              <SheetHeader>
                <SheetTitle className="text-left flex items-center gap-2">
                  <BookOpen className="w-5 h-5 text-primary" />
                  CEFR Practice
                </SheetTitle>
              </SheetHeader>
              
              <nav className="flex flex-col gap-2 mt-6">
                {user ? (
                  <>
                    <div className="p-3 rounded-lg bg-muted mb-2">
                      <p className="font-medium">{profile?.display_name || profile?.username || 'Student'}</p>
                      <p className="text-sm text-muted-foreground">{user.email}</p>
                    </div>
                    
                    <Button asChild variant="ghost" className="justify-start h-12" onClick={closeMobileMenu}>
                      <Link to="/dashboard">
                        <User className="mr-3 h-5 w-5" />
                        Dashboard
                      </Link>
                    </Button>
                    
                    <Button asChild variant="ghost" className="justify-start h-12" onClick={closeMobileMenu}>
                      <Link to="/leaderboard">
                        <Trophy className="mr-3 h-5 w-5" />
                        Leaderboard
                      </Link>
                    </Button>
                    
                    {isAdmin && (
                      <Button asChild variant="ghost" className="justify-start h-12" onClick={closeMobileMenu}>
                        <Link to="/admin">
                          <LayoutDashboard className="mr-3 h-5 w-5" />
                          Admin Panel
                        </Link>
                      </Button>
                    )}
                    
                    <div className="my-2 border-t" />
                    
                    <DonationButton variant="outline" className="justify-start h-12" />

                    <div className="flex items-center justify-between p-3 rounded-lg bg-muted">
                      <span className="text-sm">Dark Mode</span>
                      <ThemeToggle />
                    </div>
                    
                    <div className="my-2 border-t" />
                    
                    <Button variant="ghost" className="justify-start h-12 text-destructive" onClick={handleSignOut}>
                      <LogOut className="mr-3 h-5 w-5" />
                      Sign out
                    </Button>
                  </>
                ) : (
                  <>
                    <Button asChild variant="ghost" className="justify-start h-12" onClick={closeMobileMenu}>
                      <Link to="/leaderboard">
                        <Trophy className="mr-3 h-5 w-5" />
                        Leaderboard
                      </Link>
                    </Button>
                    
                    <DonationButton variant="outline" className="justify-start h-12" />
                    
                    <div className="my-2 border-t" />
                    
                    <Button asChild className="h-12" onClick={closeMobileMenu}>
                      <Link to="/auth">Sign In</Link>
                    </Button>
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
