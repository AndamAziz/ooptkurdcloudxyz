import { useEffect, useState, useCallback } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { 
  CheckCircle2, 
  XCircle, 
  ArrowRight, 
  Clock, 
  Loader2,
  Home,
  RotateCcw
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Progress } from '@/components/ui/progress';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useAuth } from '@/contexts/AuthContext';
import { supabase } from '@/integrations/supabase/client';
import { 
  PRACTICE_MODES, 
  TIMED_DURATION_SECONDS, 
  XP_REWARDS,
  type CEFRLevel,
  CEFR_LEVELS 
} from '@/lib/constants';
import { cn } from '@/lib/utils';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';

interface Question {
  id: number;
  cefr: CEFRLevel;
  difficulty: number;
  q_type: string;
  prompt: string;
  options_a: string | null;
  options_b: string | null;
  options_c: string | null;
  options_d: string | null;
  correct_answer: string | null;
  explanation: string | null;
  tags: string | null;
}

type PracticeState = 'setup' | 'practicing' | 'feedback' | 'results';

const Practice = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user, profile, refreshProfile } = useAuth();

  const [state, setState] = useState<PracticeState>('setup');
  const [mode, setMode] = useState(searchParams.get('mode') || PRACTICE_MODES.TEN_QUESTIONS);
  const [selectedLevel, setSelectedLevel] = useState<CEFRLevel>(
    (searchParams.get('level') as CEFRLevel) || (profile?.current_cefr as CEFRLevel) || 'A1'
  );
  
  const [questions, setQuestions] = useState<Question[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [selectedAnswer, setSelectedAnswer] = useState<string | null>(null);
  const [isCorrect, setIsCorrect] = useState<boolean | null>(null);
  const [answers, setAnswers] = useState<{ questionId: number; answer: string; correct: boolean }[]>([]);
  
  const [timeLeft, setTimeLeft] = useState(TIMED_DURATION_SECONDS);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [questionStartTime, setQuestionStartTime] = useState<number>(0);

  // Timer for timed mode
  useEffect(() => {
    if (state === 'practicing' && mode === PRACTICE_MODES.TIMED && timeLeft > 0) {
      const timer = setInterval(() => {
        setTimeLeft((prev) => {
          if (prev <= 1) {
            endSession();
            return 0;
          }
          return prev - 1;
        });
      }, 1000);
      return () => clearInterval(timer);
    }
  }, [state, mode, timeLeft]);

  const fetchQuestions = async () => {
    setLoading(true);
    const limit = mode === PRACTICE_MODES.TEN_QUESTIONS ? 10 : 50;
    
    const { data, error } = await supabase
      .from('questions')
      .select('*')
      .eq('cefr', selectedLevel)
      .limit(limit);

    if (data && data.length > 0) {
      // Shuffle questions
      const shuffled = [...data].sort(() => Math.random() - 0.5);
      setQuestions(shuffled as Question[]);
    }
    setLoading(false);
  };

  const startSession = async () => {
    await fetchQuestions();
    
    // Create session
    const { data: session } = await supabase
      .from('practice_sessions')
      .insert({
        user_id: user!.id,
        cefr: selectedLevel,
        mode,
        total_questions: 0,
        correct_count: 0,
      })
      .select()
      .single();

    if (session) {
      setSessionId(session.id);
    }
    
    setState('practicing');
    setQuestionStartTime(Date.now());
  };

  const handleAnswer = async (answer: string) => {
    if (selectedAnswer) return;
    
    const question = questions[currentIndex];
    const correct = answer.toUpperCase() === question.correct_answer?.toUpperCase();
    const timeSpent = Math.round((Date.now() - questionStartTime) / 1000);
    
    setSelectedAnswer(answer);
    setIsCorrect(correct);
    setAnswers([...answers, { questionId: question.id, answer, correct }]);

    // Record attempt
    await supabase.from('attempts').insert({
      user_id: user!.id,
      question_id: question.id,
      chosen_answer: answer,
      is_correct: correct,
      time_spent_sec: timeSpent,
    });

    setState('feedback');
  };

  const nextQuestion = () => {
    if (currentIndex >= questions.length - 1 || 
        (mode === PRACTICE_MODES.TEN_QUESTIONS && currentIndex >= 9)) {
      endSession();
    } else {
      setCurrentIndex(currentIndex + 1);
      setSelectedAnswer(null);
      setIsCorrect(null);
      setState('practicing');
      setQuestionStartTime(Date.now());
    }
  };

  const endSession = async () => {
    const totalQuestions = answers.length;
    const correctCount = answers.filter(a => a.correct).length;
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;
    const xpEarned = (correctCount * XP_REWARDS.CORRECT) + ((totalQuestions - correctCount) * XP_REWARDS.INCORRECT);

    // Generate summary
    const summary = `You completed ${totalQuestions} questions at ${selectedLevel} level with ${accuracy}% accuracy. You earned ${xpEarned} XP!`;

    // Update session
    if (sessionId) {
      await supabase
        .from('practice_sessions')
        .update({
          ended_at: new Date().toISOString(),
          total_questions: totalQuestions,
          correct_count: correctCount,
          accuracy,
          xp_earned: xpEarned,
          summary_text: summary,
        })
        .eq('id', sessionId);
    }

    // Update user XP
    if (profile) {
      await supabase
        .from('profiles')
        .update({ xp: profile.xp + xpEarned })
        .eq('user_id', user!.id);
      
      await refreshProfile();
    }

    setState('results');
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  const currentQuestion = questions[currentIndex];
  const progress = mode === PRACTICE_MODES.TEN_QUESTIONS 
    ? ((currentIndex + 1) / 10) * 100 
    : ((currentIndex + 1) / questions.length) * 100;

  if (state === 'setup') {
    return (
      <div className="min-h-screen bg-background">
        <Header />
        <main className="container max-w-2xl py-12">
          <Card className="animate-fade-in">
            <CardHeader className="text-center">
              <CardTitle className="font-serif text-2xl">Start Practice Session</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div>
                <label className="text-sm font-medium mb-2 block">Select Level</label>
                <Select value={selectedLevel} onValueChange={(v) => setSelectedLevel(v as CEFRLevel)}>
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {CEFR_LEVELS.map((level) => (
                      <SelectItem key={level} value={level}>
                        <div className="flex items-center gap-2">
                          <CEFRBadge level={level} size="sm" />
                          <span>{level}</span>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div>
                <label className="text-sm font-medium mb-2 block">Select Mode</label>
                <div className="grid grid-cols-2 gap-4">
                  <Button
                    variant={mode === PRACTICE_MODES.TEN_QUESTIONS ? 'default' : 'outline'}
                    className="h-auto py-4"
                    onClick={() => setMode(PRACTICE_MODES.TEN_QUESTIONS)}
                  >
                    <div className="text-center">
                      <p className="font-semibold">10 Questions</p>
                      <p className="text-xs opacity-80">Quick practice</p>
                    </div>
                  </Button>
                  <Button
                    variant={mode === PRACTICE_MODES.TIMED ? 'default' : 'outline'}
                    className="h-auto py-4"
                    onClick={() => setMode(PRACTICE_MODES.TIMED)}
                  >
                    <div className="text-center">
                      <p className="font-semibold">Timed Mode</p>
                      <p className="text-xs opacity-80">10 minutes</p>
                    </div>
                  </Button>
                </div>
              </div>

              <Button 
                className="w-full" 
                size="lg" 
                onClick={startSession}
                disabled={loading}
              >
                {loading ? (
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                ) : null}
                Start Practice
              </Button>
            </CardContent>
          </Card>
        </main>
      </div>
    );
  }

  if (state === 'results') {
    const totalQuestions = answers.length;
    const correctCount = answers.filter(a => a.correct).length;
    const accuracy = totalQuestions > 0 ? Math.round((correctCount / totalQuestions) * 100) : 0;
    const xpEarned = (correctCount * XP_REWARDS.CORRECT) + ((totalQuestions - correctCount) * XP_REWARDS.INCORRECT);

    return (
      <div className="min-h-screen bg-background">
        <Header />
        <main className="container max-w-2xl py-12">
          <Card className="animate-scale-in">
            <CardHeader className="text-center pb-2">
              <div className="w-20 h-20 rounded-full bg-success/10 flex items-center justify-center mx-auto mb-4">
                <CheckCircle2 className="w-10 h-10 text-success" />
              </div>
              <CardTitle className="font-serif text-2xl">Session Complete!</CardTitle>
            </CardHeader>
            <CardContent className="space-y-6">
              <div className="grid grid-cols-2 gap-4 text-center">
                <div className="p-4 rounded-lg bg-muted">
                  <p className="text-3xl font-bold">{correctCount}/{totalQuestions}</p>
                  <p className="text-sm text-muted-foreground">Correct</p>
                </div>
                <div className="p-4 rounded-lg bg-muted">
                  <p className="text-3xl font-bold">{accuracy}%</p>
                  <p className="text-sm text-muted-foreground">Accuracy</p>
                </div>
              </div>

              <div className="p-4 rounded-lg bg-success/10 text-center">
                <p className="text-2xl font-bold text-success">+{xpEarned} XP</p>
                <p className="text-sm text-muted-foreground">Experience earned</p>
              </div>

              <div className="flex gap-4">
                <Button variant="outline" className="flex-1" onClick={() => navigate('/dashboard')}>
                  <Home className="w-4 h-4 mr-2" />
                  Dashboard
                </Button>
                <Button className="flex-1" onClick={() => {
                  setAnswers([]);
                  setCurrentIndex(0);
                  setSelectedAnswer(null);
                  setIsCorrect(null);
                  setTimeLeft(TIMED_DURATION_SECONDS);
                  setState('setup');
                }}>
                  <RotateCcw className="w-4 h-4 mr-2" />
                  Practice Again
                </Button>
              </div>
            </CardContent>
          </Card>
        </main>
      </div>
    );
  }

  if (loading || !currentQuestion) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <Loader2 className="w-8 h-8 animate-spin text-primary" />
      </div>
    );
  }

  const options = [
    { key: 'A', value: currentQuestion.options_a },
    { key: 'B', value: currentQuestion.options_b },
    { key: 'C', value: currentQuestion.options_c },
    { key: 'D', value: currentQuestion.options_d },
  ].filter(o => o.value);

  return (
    <div className="min-h-screen bg-background">
      <Header />
      <main className="container max-w-3xl py-8">
        {/* Progress Header */}
        <div className="mb-6 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <CEFRBadge level={selectedLevel} />
              <span className="text-sm text-muted-foreground">
                Question {currentIndex + 1} of {mode === PRACTICE_MODES.TEN_QUESTIONS ? 10 : questions.length}
              </span>
            </div>
            {mode === PRACTICE_MODES.TIMED && (
              <div className={cn(
                "flex items-center gap-2 px-3 py-1 rounded-full",
                timeLeft < 60 ? "bg-destructive/10 text-destructive" : "bg-muted"
              )}>
                <Clock className="w-4 h-4" />
                <span className="font-mono font-medium">{formatTime(timeLeft)}</span>
              </div>
            )}
          </div>
          <Progress value={progress} className="h-2" />
        </div>

        {/* Question Card */}
        <Card className="animate-fade-in">
          <CardContent className="p-8">
            <p className="text-lg leading-relaxed mb-8">{currentQuestion.prompt}</p>

            <div className="space-y-3">
              {options.map((option) => {
                const isSelected = selectedAnswer === option.key;
                const isCorrectAnswer = option.key.toUpperCase() === currentQuestion.correct_answer?.toUpperCase();
                const showCorrect = state === 'feedback' && isCorrectAnswer;
                const showWrong = state === 'feedback' && isSelected && !isCorrectAnswer;

                return (
                  <button
                    key={option.key}
                    onClick={() => handleAnswer(option.key)}
                    disabled={state === 'feedback'}
                    className={cn(
                      "w-full p-4 rounded-lg border-2 text-left transition-all",
                      "flex items-center gap-4",
                      state === 'practicing' && "hover:border-primary hover:bg-primary/5",
                      showCorrect && "border-success bg-success/10",
                      showWrong && "border-destructive bg-destructive/10",
                      !showCorrect && !showWrong && isSelected && "border-primary bg-primary/10"
                    )}
                  >
                    <span className={cn(
                      "w-8 h-8 rounded-full flex items-center justify-center text-sm font-semibold",
                      showCorrect ? "bg-success text-success-foreground" : 
                      showWrong ? "bg-destructive text-destructive-foreground" :
                      "bg-muted"
                    )}>
                      {option.key}
                    </span>
                    <span className="flex-1">{option.value}</span>
                    {showCorrect && <CheckCircle2 className="w-5 h-5 text-success" />}
                    {showWrong && <XCircle className="w-5 h-5 text-destructive" />}
                  </button>
                );
              })}
            </div>

            {/* Feedback */}
            {state === 'feedback' && (
              <div className="mt-6 animate-slide-up">
                <div className={cn(
                  "p-4 rounded-lg mb-4",
                  isCorrect ? "bg-success/10" : "bg-destructive/10"
                )}>
                  <p className={cn(
                    "font-semibold mb-1",
                    isCorrect ? "text-success" : "text-destructive"
                  )}>
                    {isCorrect ? "Correct! +10 XP" : "Incorrect +2 XP"}
                  </p>
                  {currentQuestion.explanation && (
                    <p className="text-sm text-muted-foreground">
                      {currentQuestion.explanation}
                    </p>
                  )}
                </div>
                
                <Button onClick={nextQuestion} className="w-full">
                  {currentIndex >= (mode === PRACTICE_MODES.TEN_QUESTIONS ? 9 : questions.length - 1) 
                    ? 'See Results' 
                    : 'Next Question'}
                  <ArrowRight className="w-4 h-4 ml-2" />
                </Button>
              </div>
            )}
          </CardContent>
        </Card>
      </main>
    </div>
  );
};

export default Practice;
