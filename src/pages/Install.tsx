import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Download, Smartphone, Check, Share, MoreVertical, ArrowLeft } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Header } from '@/components/Header';
import { FloatingBackButton } from '@/components/shared/FloatingBackButton';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const Install = () => {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  useEffect(() => {
    // Check if already installed
    if (window.matchMedia('(display-mode: standalone)').matches) {
      setIsInstalled(true);
    }

    // Check if iOS
    const isIOSDevice = /iPad|iPhone|iPod/.test(navigator.userAgent);
    setIsIOS(isIOSDevice);

    // Listen for the beforeinstallprompt event
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);

    // Listen for app installed event
    window.addEventListener('appinstalled', () => {
      setIsInstalled(true);
      setDeferredPrompt(null);
    });

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    };
  }, []);

  const handleInstall = async () => {
    if (!deferredPrompt) return;

    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    
    if (outcome === 'accepted') {
      setIsInstalled(true);
    }
    setDeferredPrompt(null);
  };

  return (
    <div className="min-h-screen bg-background overflow-x-hidden">
      <Header />
      
      <main className="container py-6 md:py-12 px-4">
        <div className="max-w-2xl mx-auto">
          {/* Back Button */}
          <Link to="/dashboard">
            <Button variant="ghost" size="sm" className="mb-4 -ml-2 h-10">
              <ArrowLeft className="w-4 h-4 mr-2" />
              Back to Dashboard
            </Button>
          </Link>

          <div className="text-center mb-6 md:mb-8">
            <div className="inline-flex items-center justify-center w-14 h-14 md:w-16 md:h-16 rounded-full bg-primary/10 mb-3 md:mb-4">
              <Smartphone className="w-7 h-7 md:w-8 md:h-8 text-primary" />
            </div>
            <h1 className="font-serif text-xl md:text-3xl font-bold mb-2">Install CEFR Practice</h1>
            <p className="text-sm md:text-base text-muted-foreground">
              Install our app for the best experience with offline access
            </p>
          </div>

          {isInstalled ? (
            <Card className="border-success/50 bg-success/5">
              <CardContent className="p-6 text-center">
                <div className="w-12 h-12 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                  <Check className="w-6 h-6 text-success" />
                </div>
                <h2 className="font-semibold text-lg mb-2">App Installed!</h2>
                <p className="text-muted-foreground text-sm">
                  CEFR Practice is installed on your device. You can now practice offline!
                </p>
              </CardContent>
            </Card>
          ) : isIOS ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Share className="w-5 h-5" />
                  Install on iOS
                </CardTitle>
                <CardDescription>
                  Follow these steps to install the app on your iPhone or iPad
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    1
                  </div>
                  <div>
                    <p className="font-medium text-sm">Tap the Share button</p>
                    <p className="text-xs text-muted-foreground">
                      Find the <Share className="w-3 h-3 inline" /> share icon in Safari's toolbar
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    2
                  </div>
                  <div>
                    <p className="font-medium text-sm">Scroll down and tap "Add to Home Screen"</p>
                    <p className="text-xs text-muted-foreground">
                      This option lets you install the app
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    3
                  </div>
                  <div>
                    <p className="font-medium text-sm">Tap "Add" to confirm</p>
                    <p className="text-xs text-muted-foreground">
                      The app icon will appear on your home screen
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          ) : deferredPrompt ? (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Download className="w-5 h-5" />
                  Install Now
                </CardTitle>
                <CardDescription>
                  Add CEFR Practice to your home screen for quick access
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Button onClick={handleInstall} className="w-full min-h-[48px]" size="lg">
                  <Download className="w-5 h-5 mr-2" />
                  Install App
                </Button>
                
                <ul className="mt-6 space-y-2 text-sm text-muted-foreground">
                  <li className="flex items-center gap-2">
                    <Check className="w-4 h-4 text-success" />
                    Works offline
                  </li>
                  <li className="flex items-center gap-2">
                    <Check className="w-4 h-4 text-success" />
                    Faster loading
                  </li>
                  <li className="flex items-center gap-2">
                    <Check className="w-4 h-4 text-success" />
                    App-like experience
                  </li>
                </ul>
              </CardContent>
            </Card>
          ) : (
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <MoreVertical className="w-5 h-5" />
                  Install on Android
                </CardTitle>
                <CardDescription>
                  Follow these steps to install the app on your Android device
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    1
                  </div>
                  <div>
                    <p className="font-medium text-sm">Tap the menu button</p>
                    <p className="text-xs text-muted-foreground">
                      Find the <MoreVertical className="w-3 h-3 inline" /> three dots in Chrome's toolbar
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    2
                  </div>
                  <div>
                    <p className="font-medium text-sm">Tap "Add to Home screen" or "Install app"</p>
                    <p className="text-xs text-muted-foreground">
                      This option lets you install the app
                    </p>
                  </div>
                </div>
                <div className="flex items-start gap-3 p-3 rounded-lg bg-muted">
                  <div className="w-8 h-8 rounded-full bg-primary text-primary-foreground flex items-center justify-center text-sm font-bold flex-shrink-0">
                    3
                  </div>
                  <div>
                    <p className="font-medium text-sm">Tap "Install" to confirm</p>
                    <p className="text-xs text-muted-foreground">
                      The app will be added to your home screen
                    </p>
                  </div>
                </div>
              </CardContent>
            </Card>
          )}

          {/* Benefits */}
          <div className="mt-8 grid gap-4 md:grid-cols-3">
            <Card>
              <CardContent className="p-4 text-center">
                <div className="w-10 h-10 rounded-full bg-primary/10 flex items-center justify-center mx-auto mb-3">
                  <Download className="w-5 h-5 text-primary" />
                </div>
                <h3 className="font-semibold text-sm mb-1">Offline Practice</h3>
                <p className="text-xs text-muted-foreground">
                  Practice anytime, even without internet
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4 text-center">
                <div className="w-10 h-10 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-3">
                  <Check className="w-5 h-5 text-success" />
                </div>
                <h3 className="font-semibold text-sm mb-1">Fast & Smooth</h3>
                <p className="text-xs text-muted-foreground">
                  Loads instantly like a native app
                </p>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-4 text-center">
                <div className="w-10 h-10 rounded-full bg-warning/10 flex items-center justify-center mx-auto mb-3">
                  <Smartphone className="w-5 h-5 text-warning" />
                </div>
                <h3 className="font-semibold text-sm mb-1">Home Screen</h3>
                <p className="text-xs text-muted-foreground">
                  Quick access from your home screen
                </p>
              </CardContent>
            </Card>
          </div>
        </div>
      </main>

      <FloatingBackButton />
    </div>
  );
};

export default Install;
