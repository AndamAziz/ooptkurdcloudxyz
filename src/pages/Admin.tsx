import { useEffect, useState } from 'react';
import { 
  Users, 
  BookOpen, 
  BarChart3, 
  Upload,
  Search,
  Plus,
  Pencil,
  Trash2,
  FileSpreadsheet,
  CheckCircle2,
  AlertCircle
} from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { useToast } from '@/hooks/use-toast';
import { supabase } from '@/integrations/supabase/client';
import { CEFR_LEVELS, type CEFRLevel } from '@/lib/constants';

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
  source: string | null;
}

interface Stats {
  totalQuestions: number;
  totalStudents: number;
  totalAttempts: number;
  questionsByLevel: Record<string, number>;
}

const Admin = () => {
  const { toast } = useToast();
  const [stats, setStats] = useState<Stats>({
    totalQuestions: 0,
    totalStudents: 0,
    totalAttempts: 0,
    questionsByLevel: {},
  });
  const [questions, setQuestions] = useState<Question[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [filterLevel, setFilterLevel] = useState<string>('all');
  const [importResults, setImportResults] = useState<{ inserted: number; skipped: number; failed: number } | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState({ current: 0, total: 0 });

  useEffect(() => {
    fetchData();
  }, []);

  const QUESTIONS_PAGE_SIZE = 1000;

  const fetchQuestionCountsByLevel = async () => {
    const questionsByLevel: Record<string, number> = {};

    let offset = 0;
    while (true) {
      const { data, error } = await supabase
        .from('questions')
        .select('cefr')
        .range(offset, offset + QUESTIONS_PAGE_SIZE - 1);

      if (error) throw error;
      if (!data || data.length === 0) break;

      data.forEach((q) => {
        const level = q.cefr as string;
        questionsByLevel[level] = (questionsByLevel[level] || 0) + 1;
      });

      if (data.length < QUESTIONS_PAGE_SIZE) break;
      offset += QUESTIONS_PAGE_SIZE;
    }

    return questionsByLevel;
  };

  const fetchExistingPrompts = async () => {
    const prompts = new Set<string>();

    let offset = 0;
    while (true) {
      const { data, error } = await supabase
        .from('questions')
        .select('prompt')
        .range(offset, offset + QUESTIONS_PAGE_SIZE - 1);

      if (error) throw error;
      if (!data || data.length === 0) break;

      data.forEach((q) => {
        if (q.prompt) prompts.add(q.prompt.trim().toLowerCase());
      });

      if (data.length < QUESTIONS_PAGE_SIZE) break;
      offset += QUESTIONS_PAGE_SIZE;
    }

    return prompts;
  };

  const fetchData = async () => {
    setLoading(true);

    try {
      // Accurate totals (PostgREST defaults to 1000 rows per request)
      const [{ count: totalQuestionsCount }, questionsByLevel] = await Promise.all([
        supabase.from('questions').select('*', { count: 'exact', head: true }),
        fetchQuestionCountsByLevel(),
      ]);

      // Fetch students count
      const { count: studentsCount } = await supabase
        .from('profiles')
        .select('*', { count: 'exact', head: true });

      // Fetch attempts count
      const { count: attemptsCount } = await supabase
        .from('attempts')
        .select('*', { count: 'exact', head: true });

      setStats({
        totalQuestions: totalQuestionsCount || 0,
        totalStudents: studentsCount || 0,
        totalAttempts: attemptsCount || 0,
        questionsByLevel,
      });

      // Fetch questions for table
      const { data: allQuestions } = await supabase
        .from('questions')
        .select('*')
        .order('id', { ascending: false })
        .limit(100);

      setQuestions((allQuestions as Question[]) || []);
    } catch {
      toast({
        title: 'Error',
        description: 'Failed to load admin data',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setIsImporting(true);
    setImportResults(null);
    setImportProgress({ current: 0, total: 0 });

    try {
      const text = await file.text();
      const lines = text.split('\n');
      const headers = lines[0].split(',').map(h => h.trim().toLowerCase());

      // Filter out empty lines
      const dataLines = lines.slice(1).filter(line => line.trim());
      const totalRows = dataLines.length;
      setImportProgress({ current: 0, total: totalRows });

      // Fetch existing prompts to check for duplicates (paginate because of 1000-row default limit)
      const existingPrompts = await fetchExistingPrompts();

      let inserted = 0;
      let skipped = 0;
      let failed = 0;

      // Process in batches for better performance
      const batchSize = 50;
      const questionsToInsert: any[] = [];

      for (let i = 0; i < dataLines.length; i++) {
        const line = dataLines[i].trim();
        if (!line) continue;

        // Parse CSV line (handling quoted values)
        const values: string[] = [];
        let current = '';
        let inQuotes = false;
        
        for (const char of line) {
          if (char === '"') {
            inQuotes = !inQuotes;
          } else if (char === ',' && !inQuotes) {
            values.push(current.trim());
            current = '';
          } else {
            current += char;
          }
        }
        values.push(current.trim());

        const row: Record<string, string> = {};
        headers.forEach((h, idx) => {
          row[h] = values[idx] || '';
        });

        // Map to question schema
        const cefrValue = row.cefr?.toUpperCase();
        if (!CEFR_LEVELS.includes(cefrValue as CEFRLevel)) {
          failed++;
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        const prompt = row.prompt?.trim() || '';
        if (!prompt) {
          failed++;
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        // Check for duplicate
        if (existingPrompts.has(prompt.toLowerCase())) {
          skipped++;
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        // Add to existing prompts to avoid duplicates within same file
        existingPrompts.add(prompt.toLowerCase());

        const question = {
          cefr: cefrValue as CEFRLevel,
          difficulty: parseInt(row.difficulty) || 2,
          q_type: row.q_type || 'MCQ',
          prompt,
          options_a: row.options_a || row['options a'] || null,
          options_b: row.options_b || row['options b'] || null,
          options_c: row.options_c || row['options c'] || null,
          options_d: row.options_d || row['options d'] || null,
          correct_answer: row.correct_answer || null,
          explanation: row.explanation || null,
          tags: row.tags || null,
          source: row.source || null,
        };

        questionsToInsert.push(question);

        // Insert in batches
        if (questionsToInsert.length >= batchSize) {
          const { error } = await supabase.from('questions').insert(questionsToInsert);
          if (error) {
            failed += questionsToInsert.length;
          } else {
            inserted += questionsToInsert.length;
          }
          questionsToInsert.length = 0;
          setImportProgress({ current: i + 1, total: totalRows });
        }
      }

      // Insert remaining questions
      if (questionsToInsert.length > 0) {
        const { error } = await supabase.from('questions').insert(questionsToInsert);
        if (error) {
          failed += questionsToInsert.length;
        } else {
          inserted += questionsToInsert.length;
        }
      }

      setImportProgress({ current: totalRows, total: totalRows });
      setImportResults({ inserted, skipped, failed });
      toast({
        title: 'Import Complete',
        description: `${inserted} new, ${skipped} duplicates skipped, ${failed} failed`,
      });

      fetchData();
    } catch (error) {
      toast({
        title: 'Import Failed',
        description: 'Error parsing CSV file',
        variant: 'destructive',
      });
    } finally {
      setIsImporting(false);
    }
  };

  const deleteQuestion = async (id: number) => {
    const { error } = await supabase.from('questions').delete().eq('id', id);
    
    if (error) {
      toast({
        title: 'Error',
        description: 'Failed to delete question',
        variant: 'destructive',
      });
    } else {
      toast({ title: 'Question deleted' });
      setQuestions(questions.filter(q => q.id !== id));
    }
  };

  const filteredQuestions = questions.filter(q => {
    const matchesSearch = q.prompt.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesLevel = filterLevel === 'all' || q.cefr === filterLevel;
    return matchesSearch && matchesLevel;
  });

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container py-8">
        <div className="mb-8">
          <h1 className="font-serif text-3xl font-bold">Admin Dashboard</h1>
          <p className="text-muted-foreground">Manage questions and monitor usage</p>
        </div>

        <Tabs defaultValue="overview" className="space-y-6">
          <TabsList>
            <TabsTrigger value="overview">Overview</TabsTrigger>
            <TabsTrigger value="questions">Questions</TabsTrigger>
            <TabsTrigger value="import">Import</TabsTrigger>
          </TabsList>

          <TabsContent value="overview">
            {/* Stats Grid */}
            <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4 mb-8">
              <Card>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-sm font-medium">Total Questions</CardTitle>
                  <BookOpen className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{stats.totalQuestions.toLocaleString()}</div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-sm font-medium">Total Students</CardTitle>
                  <Users className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{stats.totalStudents.toLocaleString()}</div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-sm font-medium">Total Attempts</CardTitle>
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="text-2xl font-bold">{stats.totalAttempts.toLocaleString()}</div>
                </CardContent>
              </Card>
              
              <Card>
                <CardHeader className="flex flex-row items-center justify-between pb-2">
                  <CardTitle className="text-sm font-medium">Questions by Level</CardTitle>
                  <BarChart3 className="h-4 w-4 text-muted-foreground" />
                </CardHeader>
                <CardContent>
                  <div className="flex flex-wrap gap-2">
                    {CEFR_LEVELS.map(level => (
                      <div key={level} className="text-xs">
                        <CEFRBadge level={level} size="sm" />
                        <span className="ml-1">{stats.questionsByLevel[level] || 0}</span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          </TabsContent>

          <TabsContent value="questions">
            <Card>
              <CardHeader>
                <div className="flex items-center justify-between">
                  <div>
                    <CardTitle>Questions Database</CardTitle>
                    <CardDescription>Manage your question bank</CardDescription>
                  </div>
                </div>
              </CardHeader>
              <CardContent>
                {/* Filters */}
                <div className="flex gap-4 mb-6">
                  <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      placeholder="Search questions..."
                      className="pl-10"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                  </div>
                  <Select value={filterLevel} onValueChange={setFilterLevel}>
                    <SelectTrigger className="w-32">
                      <SelectValue placeholder="Level" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All Levels</SelectItem>
                      {CEFR_LEVELS.map(level => (
                        <SelectItem key={level} value={level}>{level}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {/* Table */}
                <div className="rounded-md border">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-16">ID</TableHead>
                        <TableHead className="w-20">Level</TableHead>
                        <TableHead>Prompt</TableHead>
                        <TableHead className="w-20">Answer</TableHead>
                        <TableHead className="w-24">Actions</TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredQuestions.map((question) => (
                        <TableRow key={question.id}>
                          <TableCell className="font-mono text-sm">{question.id}</TableCell>
                          <TableCell>
                            <CEFRBadge level={question.cefr} size="sm" />
                          </TableCell>
                          <TableCell className="max-w-md truncate">
                            {question.prompt}
                          </TableCell>
                          <TableCell className="font-semibold">
                            {question.correct_answer || '-'}
                          </TableCell>
                          <TableCell>
                            <Button
                              variant="ghost"
                              size="icon"
                              onClick={() => deleteQuestion(question.id)}
                            >
                              <Trash2 className="w-4 h-4 text-destructive" />
                            </Button>
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </div>
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="import">
            <Card>
              <CardHeader>
                <CardTitle className="flex items-center gap-2">
                  <Upload className="w-5 h-5" />
                  Bulk Import Questions
                </CardTitle>
                <CardDescription>
                  Upload a CSV file with your question bank
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-6">
                <div className="border-2 border-dashed rounded-lg p-8 text-center">
                  <FileSpreadsheet className="w-12 h-12 mx-auto mb-4 text-muted-foreground" />
                  <p className="mb-2 font-medium">Upload CSV File</p>
                  <p className="text-sm text-muted-foreground mb-4">
                    Required columns: cefr, prompt, options_a, options_b, options_c, options_d, correct_answer
                  </p>
                  <Input
                    type="file"
                    accept=".csv"
                    onChange={handleFileUpload}
                    disabled={isImporting}
                    className="max-w-xs mx-auto"
                  />
                </div>

                {isImporting && (
                  <div className="space-y-4 py-4">
                    <div className="flex items-center justify-between text-sm">
                      <span>Importing questions...</span>
                      <span className="font-mono font-semibold">
                        {importProgress.total > 0 
                          ? `${Math.round((importProgress.current / importProgress.total) * 100)}%` 
                          : '0%'}
                      </span>
                    </div>
                    <div className="h-3 bg-muted rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-primary transition-all duration-300 ease-out"
                        style={{ 
                          width: importProgress.total > 0 
                            ? `${(importProgress.current / importProgress.total) * 100}%` 
                            : '0%' 
                        }}
                      />
                    </div>
                    <p className="text-sm text-muted-foreground text-center">
                      {importProgress.current.toLocaleString()} / {importProgress.total.toLocaleString()} rows processed
                    </p>
                  </div>
                )}

                {importResults && (
                  <div className="grid gap-4 md:grid-cols-3">
                    <Card className="bg-success/10 border-success/20">
                      <CardContent className="p-4 flex items-center gap-4">
                        <CheckCircle2 className="w-8 h-8 text-success" />
                        <div>
                          <p className="text-2xl font-bold">{importResults.inserted.toLocaleString()}</p>
                          <p className="text-sm text-muted-foreground">New questions</p>
                        </div>
                      </CardContent>
                    </Card>
                    <Card className="bg-warning/10 border-warning/20">
                      <CardContent className="p-4 flex items-center gap-4">
                        <AlertCircle className="w-8 h-8 text-warning" />
                        <div>
                          <p className="text-2xl font-bold">{importResults.skipped.toLocaleString()}</p>
                          <p className="text-sm text-muted-foreground">Duplicates skipped</p>
                        </div>
                      </CardContent>
                    </Card>
                    <Card className="bg-destructive/10 border-destructive/20">
                      <CardContent className="p-4 flex items-center gap-4">
                        <AlertCircle className="w-8 h-8 text-destructive" />
                        <div>
                          <p className="text-2xl font-bold">{importResults.failed.toLocaleString()}</p>
                          <p className="text-sm text-muted-foreground">Failed rows</p>
                        </div>
                      </CardContent>
                    </Card>
                  </div>
                )}

                <div className="rounded-lg bg-muted p-4">
                  <p className="font-medium mb-2">CSV Format Example:</p>
                  <code className="text-xs block overflow-x-auto">
                    id,cefr,difficulty,q_type,prompt,options_a,options_b,options_c,options_d,correct_answer,explanation,tags,source
                  </code>
                </div>
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </main>
    </div>
  );
};

export default Admin;
