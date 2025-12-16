import { useEffect, useState } from 'react';
import { 
  Users, 
  BookOpen, 
  BarChart3, 
  Upload,
  Search,
  Trash2,
  FileSpreadsheet,
  CheckCircle2,
  AlertCircle,
  Award,
  Activity,
  Download,
  AlertTriangle
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
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from '@/components/ui/alert-dialog';
import { Header } from '@/components/Header';
import { CEFRBadge } from '@/components/CEFRBadge';
import { AdminCertificates } from '@/components/admin/AdminCertificates';
import { AdminOverview } from '@/components/admin/AdminOverview';
import { AdminStudentList } from '@/components/admin/AdminStudentList';
import { AdminActivityFeed } from '@/components/admin/AdminActivityFeed';
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
  const [importFailures, setImportFailures] = useState<Array<{ rowNumber: number; reason: string; promptPreview: string }>>([]);
  const [isImporting, setIsImporting] = useState(false);
  const [importProgress, setImportProgress] = useState({ current: 0, total: 0 });
  const [isDeletingAll, setIsDeletingAll] = useState(false);
  const [deleteLevelFilter, setDeleteLevelFilter] = useState<string>('all');

  useEffect(() => {
    fetchData();
  }, []);

  // Fetch questions when filter changes
  useEffect(() => {
    const fetchFilteredQuestions = async () => {
      let query = supabase
        .from('questions')
        .select('*')
        .order('id', { ascending: false })
        .limit(100);

      if (filterLevel !== 'all') {
        query = query.eq('cefr', filterLevel as CEFRLevel);
      }

      const { data } = await query;
      setQuestions((data as Question[]) || []);
    };

    fetchFilteredQuestions();
  }, [filterLevel]);

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
      const [{ count: totalQuestionsCount }, questionsByLevel] = await Promise.all([
        supabase.from('questions').select('*', { count: 'exact', head: true }),
        fetchQuestionCountsByLevel(),
      ]);

      const { count: studentsCount } = await supabase
        .from('profiles')
        .select('*', { count: 'exact', head: true });

      const { count: attemptsCount } = await supabase
        .from('attempts')
        .select('*', { count: 'exact', head: true });

      setStats({
        totalQuestions: totalQuestionsCount || 0,
        totalStudents: studentsCount || 0,
        totalAttempts: attemptsCount || 0,
        questionsByLevel,
      });

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
    setImportFailures([]);
    setImportProgress({ current: 0, total: 0 });

    const normalizeHeader = (h: string) =>
      h
        .replace(/^\uFEFF/, '')
        .trim()
        .toLowerCase()
        .replace(/\s+/g, '_');

    const parseCSV = (csvText: string) => {
      const rows: string[][] = [];
      let row: string[] = [];
      let field = '';
      let inQuotes = false;

      for (let i = 0; i < csvText.length; i++) {
        const char = csvText[i];
        const next = i + 1 < csvText.length ? csvText[i + 1] : '';

        if (char === '"') {
          if (inQuotes && next === '"') {
            field += '"';
            i++;
            continue;
          }
          inQuotes = !inQuotes;
          continue;
        }

        if (char === ',' && !inQuotes) {
          row.push(field);
          field = '';
          continue;
        }

        if ((char === '\n' || char === '\r') && !inQuotes) {
          if (char === '\r' && next === '\n') i++;
          row.push(field);
          field = '';

          if (row.some((c) => c.trim() !== '')) rows.push(row);
          row = [];
          continue;
        }

        field += char;
      }

      row.push(field);
      if (row.some((c) => c.trim() !== '')) rows.push(row);

      return rows;
    };

    try {
      const text = await file.text();
      const rows = parseCSV(text);
      if (rows.length < 2) throw new Error('CSV has no data rows');

      const headers = rows[0].map(normalizeHeader);
      const dataRows = rows.slice(1);
      const totalRows = dataRows.length;
      setImportProgress({ current: 0, total: totalRows });

      const existingPrompts = await fetchExistingPrompts();

      let inserted = 0;
      let skipped = 0;
      let failed = 0;
      const failures: Array<{ rowNumber: number; reason: string; promptPreview: string }> = [];

      const batchSize = 50;
      const batch: Array<{ question: any; rowNumber: number; promptPreview: string }> = [];

      const flushBatch = async () => {
        if (batch.length === 0) return;

        const payload = batch.map((b) => b.question);
        const { error } = await supabase.from('questions').insert(payload);

        if (!error) {
          inserted += batch.length;
          batch.length = 0;
          return;
        }

        for (const item of batch) {
          const { error: rowError } = await supabase.from('questions').insert(item.question);
          if (rowError) {
            failed++;
            failures.push({
              rowNumber: item.rowNumber,
              reason: rowError.message,
              promptPreview: item.promptPreview,
            });
          } else {
            inserted++;
          }
        }

        batch.length = 0;
      };

      for (let i = 0; i < dataRows.length; i++) {
        const values = dataRows[i];
        const rowNumber = i + 2;

        const row: Record<string, string> = {};
        headers.forEach((h, idx) => {
          row[h] = (values[idx] ?? '').trim();
        });

        const cefrValue = (row.cefr || '').toUpperCase();
        const prompt = (row.prompt || '').trim();
        const promptPreview = prompt ? prompt.slice(0, 80) : '(empty)';

        if (!CEFR_LEVELS.includes(cefrValue as CEFRLevel)) {
          failed++;
          failures.push({
            rowNumber,
            reason: `Invalid CEFR level: "${row.cefr || ''}"`,
            promptPreview,
          });
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        if (!prompt) {
          failed++;
          failures.push({ rowNumber, reason: 'Missing prompt', promptPreview });
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        if (existingPrompts.has(prompt.toLowerCase())) {
          skipped++;
          setImportProgress({ current: i + 1, total: totalRows });
          continue;
        }

        existingPrompts.add(prompt.toLowerCase());

        const question = {
          cefr: cefrValue as CEFRLevel,
          difficulty: parseInt(row.difficulty) || 2,
          q_type: row.q_type || 'MCQ',
          prompt,
          options_a: row.options_a || row['options_a'] || row['options a'] || null,
          options_b: row.options_b || row['options_b'] || row['options b'] || null,
          options_c: row.options_c || row['options_c'] || row['options c'] || null,
          options_d: row.options_d || row['options_d'] || row['options d'] || null,
          correct_answer: row.correct_answer || null,
          explanation: row.explanation || null,
          tags: row.tags || null,
          source: row.source || null,
        };

        batch.push({ question, rowNumber, promptPreview });
        if (batch.length >= batchSize) {
          await flushBatch();
        }

        setImportProgress({ current: i + 1, total: totalRows });
      }

      await flushBatch();

      setImportProgress({ current: totalRows, total: totalRows });
      setImportResults({ inserted, skipped, failed });
      setImportFailures(failures);

      toast({
        title: 'Import Complete',
        description: `${inserted} new, ${skipped} duplicates skipped, ${failed} failed`,
      });

      fetchData();
    } catch {
      toast({
        title: 'Import Failed',
        description: 'Error parsing CSV file',
        variant: 'destructive',
      });
    } finally {
      setIsImporting(false);
    }
  };

  const downloadTemplate = () => {
    const headers = ['cefr', 'prompt', 'options_a', 'options_b', 'options_c', 'options_d', 'correct_answer', 'explanation', 'difficulty', 'q_type', 'tags', 'source'];
    const exampleRow = [
      'A1',
      'What is the correct form? "She ___ to school every day."',
      'go',
      'goes',
      'going',
      'went',
      'B',
      'Third person singular requires "goes"',
      '2',
      'MCQ',
      'grammar,present-simple',
      'Team Import'
    ];
    
    const csvContent = [
      headers.join(','),
      exampleRow.map(cell => `"${cell.replace(/"/g, '""')}"`).join(',')
    ].join('\n');
    
    const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'questions_template.csv';
    link.click();
    URL.revokeObjectURL(url);
    
    toast({
      title: 'Template Downloaded',
      description: 'Fill in your questions and upload the file',
    });
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

  const deleteAllQuestions = async (level?: string) => {
    setIsDeletingAll(true);
    try {
      let query = supabase.from('questions').delete();
      
      if (level && level !== 'all') {
        query = query.eq('cefr', level as CEFRLevel);
      } else {
        query = query.neq('id', 0);
      }
      
      const { error } = await query;
      
      if (error) {
        toast({
          title: 'Error',
          description: 'Failed to delete questions',
          variant: 'destructive',
        });
      } else {
        const levelText = level && level !== 'all' ? `${level} level` : 'All';
        toast({ 
          title: `${levelText} questions deleted`,
          description: level && level !== 'all' 
            ? `All ${level} questions have been removed` 
            : 'The question bank has been cleared',
        });
        if (level && level !== 'all') {
          setQuestions(questions.filter(q => q.cefr !== level));
        } else {
          setQuestions([]);
        }
        fetchData();
        setDeleteLevelFilter('all');
      }
    } catch {
      toast({
        title: 'Error',
        description: 'Failed to delete questions',
        variant: 'destructive',
      });
    } finally {
      setIsDeletingAll(false);
    }
  };

  const getDeleteCount = () => {
    if (deleteLevelFilter === 'all') {
      return stats.totalQuestions;
    }
    return stats.questionsByLevel[deleteLevelFilter] || 0;
  };

  const filteredQuestions = questions.filter(q => {
    const matchesSearch = q.prompt.toLowerCase().includes(searchQuery.toLowerCase());
    const matchesLevel = filterLevel === 'all' || q.cefr === filterLevel;
    return matchesSearch && matchesLevel;
  });

  const exportAllQuestions = async () => {
    try {
      toast({ title: 'Exporting...', description: 'Preparing CSV file' });
      
      const allQuestions: Question[] = [];
      let offset = 0;
      
      while (true) {
        const { data, error } = await supabase
          .from('questions')
          .select('*')
          .order('cefr', { ascending: true })
          .order('id', { ascending: true })
          .range(offset, offset + QUESTIONS_PAGE_SIZE - 1);
        
        if (error) throw error;
        if (!data || data.length === 0) break;
        
        allQuestions.push(...(data as Question[]));
        if (data.length < QUESTIONS_PAGE_SIZE) break;
        offset += QUESTIONS_PAGE_SIZE;
      }

      const headers = ['cefr', 'prompt', 'options_a', 'options_b', 'options_c', 'options_d', 'correct_answer', 'explanation', 'difficulty', 'q_type', 'tags', 'source'];
      
      const csvRows = allQuestions.map(q => 
        headers.map(h => {
          const value = q[h as keyof Question];
          const str = value?.toString() || '';
          return `"${str.replace(/"/g, '""')}"`;
        }).join(',')
      );
      
      const csvContent = [headers.join(','), ...csvRows].join('\n');
      const blob = new Blob(['\uFEFF' + csvContent], { type: 'text/csv;charset=utf-8;' });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `questions_export_${new Date().toISOString().split('T')[0]}.csv`;
      link.click();
      URL.revokeObjectURL(url);
      
      toast({ title: 'Export Complete', description: `${allQuestions.length} questions exported` });
    } catch {
      toast({ title: 'Export Failed', variant: 'destructive' });
    }
  };

  return (
    <div className="min-h-screen bg-background">
      <Header />
      
      <main className="container py-4 md:py-8 px-3 md:px-4">
        <div className="mb-4 md:mb-8">
          <h1 className="font-serif text-xl md:text-3xl font-bold">Admin Dashboard</h1>
          <p className="text-xs md:text-base text-muted-foreground">Manage questions, certificates, and monitor usage</p>
        </div>

        <Tabs defaultValue="overview" className="space-y-4 md:space-y-6">
          <TabsList className="w-full grid grid-cols-5 h-auto p-1">
            <TabsTrigger value="overview" className="text-[10px] md:text-sm px-1 md:px-3 py-1.5 md:py-2">
              <BarChart3 className="w-3 h-3 md:w-4 md:h-4 md:mr-1" />
              <span className="hidden md:inline">Overview</span>
            </TabsTrigger>
            <TabsTrigger value="students" className="text-[10px] md:text-sm px-1 md:px-3 py-1.5 md:py-2">
              <Users className="w-3 h-3 md:w-4 md:h-4 md:mr-1" />
              <span className="hidden md:inline">Students</span>
            </TabsTrigger>
            <TabsTrigger value="questions" className="text-[10px] md:text-sm px-1 md:px-3 py-1.5 md:py-2">
              <BookOpen className="w-3 h-3 md:w-4 md:h-4 md:mr-1" />
              <span className="hidden md:inline">Questions</span>
            </TabsTrigger>
            <TabsTrigger value="certificates" className="text-[10px] md:text-sm px-1 md:px-3 py-1.5 md:py-2">
              <Award className="w-3 h-3 md:w-4 md:h-4 md:mr-1" />
              <span className="hidden md:inline">Certificates</span>
            </TabsTrigger>
            <TabsTrigger value="import" className="text-[10px] md:text-sm px-1 md:px-3 py-1.5 md:py-2">
              <Upload className="w-3 h-3 md:w-4 md:h-4 md:mr-1" />
              <span className="hidden md:inline">Import</span>
            </TabsTrigger>
          </TabsList>

          <TabsContent value="overview">
            <div className="grid gap-4 md:gap-6 lg:grid-cols-3">
              <div className="lg:col-span-2 space-y-4">
                {/* Questions Summary Card */}
                <Card>
                  <CardHeader className="p-3 md:p-4 pb-2">
                    <div className="flex items-center justify-between">
                      <CardTitle className="text-sm md:text-base flex items-center gap-2">
                        <BookOpen className="w-4 h-4" />
                        Questions Bank
                      </CardTitle>
                      <div className="text-lg md:text-2xl font-bold text-primary">
                        {stats.totalQuestions.toLocaleString()}
                      </div>
                    </div>
                  </CardHeader>
                  <CardContent className="p-3 md:p-4 pt-0">
                    <div className="grid grid-cols-3 md:grid-cols-6 gap-2">
                      {CEFR_LEVELS.map(level => (
                        <div key={level} className="text-center p-2 rounded-lg bg-muted/50">
                          <CEFRBadge level={level} size="sm" />
                          <div className="text-sm md:text-lg font-bold mt-1">
                            {(stats.questionsByLevel[level] || 0).toLocaleString()}
                          </div>
                        </div>
                      ))}
                    </div>
                  </CardContent>
                </Card>
                <AdminOverview />
              </div>
              <div>
                <AdminActivityFeed />
              </div>
            </div>
          </TabsContent>

          <TabsContent value="students">
            <AdminStudentList />
          </TabsContent>

          <TabsContent value="questions">
            <Card>
              <CardHeader className="p-3 md:p-6">
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                  <div>
                    <CardTitle className="text-base md:text-lg">Questions Database</CardTitle>
                    <CardDescription className="text-xs md:text-sm">Manage your question bank</CardDescription>
                  </div>
                  <div className="flex gap-2">
                    <Button 
                      variant="outline" 
                      size="sm"
                      onClick={exportAllQuestions}
                      disabled={stats.totalQuestions === 0}
                      className="gap-2"
                    >
                      <Download className="w-4 h-4" />
                      Export CSV
                    </Button>
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button 
                          variant="destructive" 
                          size="sm"
                          disabled={isDeletingAll || stats.totalQuestions === 0}
                          className="gap-2"
                        >
                          <Trash2 className="w-4 h-4" />
                          Delete
                        </Button>
                      </AlertDialogTrigger>
                    <AlertDialogContent>
                      <AlertDialogHeader>
                        <AlertDialogTitle className="flex items-center gap-2 text-destructive">
                          <AlertTriangle className="w-5 h-5" />
                          Delete Questions
                        </AlertDialogTitle>
                        <AlertDialogDescription asChild>
                          <div className="space-y-4">
                            <p>Select which questions to delete:</p>
                            <Select value={deleteLevelFilter} onValueChange={setDeleteLevelFilter}>
                              <SelectTrigger>
                                <SelectValue placeholder="Select level" />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value="all">
                                  All Levels ({stats.totalQuestions.toLocaleString()} questions)
                                </SelectItem>
                                {CEFR_LEVELS.map(level => (
                                  <SelectItem key={level} value={level}>
                                    {level} ({(stats.questionsByLevel[level] || 0).toLocaleString()} questions)
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                            <p className="text-destructive font-medium">
                              This will permanently delete <strong>{getDeleteCount().toLocaleString()}</strong> questions. 
                              This action cannot be undone.
                            </p>
                          </div>
                        </AlertDialogDescription>
                      </AlertDialogHeader>
                      <AlertDialogFooter>
                        <AlertDialogCancel onClick={() => setDeleteLevelFilter('all')}>Cancel</AlertDialogCancel>
                        <AlertDialogAction
                          onClick={() => deleteAllQuestions(deleteLevelFilter)}
                          disabled={getDeleteCount() === 0}
                          className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
                        >
                          {isDeletingAll ? 'Deleting...' : `Delete ${deleteLevelFilter === 'all' ? 'All' : deleteLevelFilter} (${getDeleteCount()})`}
                        </AlertDialogAction>
                      </AlertDialogFooter>
                    </AlertDialogContent>
                    </AlertDialog>
                  </div>
                </div>
              </CardHeader>
              <CardContent className="p-3 md:p-6 pt-0">
                <div className="flex flex-col sm:flex-row gap-2 md:gap-4 mb-4 md:mb-6">
                  <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-muted-foreground" />
                    <Input
                      placeholder="Search..."
                      className="pl-10 text-sm"
                      value={searchQuery}
                      onChange={(e) => setSearchQuery(e.target.value)}
                    />
                  </div>
                  <Select value={filterLevel} onValueChange={setFilterLevel}>
                    <SelectTrigger className="w-full sm:w-24">
                      <SelectValue placeholder="Level" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All</SelectItem>
                      {CEFR_LEVELS.map(level => (
                        <SelectItem key={level} value={level}>{level}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                <div className="rounded-md border overflow-x-auto">
                  <Table>
                    <TableHeader>
                      <TableRow>
                        <TableHead className="w-10 md:w-16 text-xs">ID</TableHead>
                        <TableHead className="w-12 md:w-20 text-xs">Level</TableHead>
                        <TableHead className="text-xs">Prompt</TableHead>
                        <TableHead className="w-10 md:w-20 text-xs hidden sm:table-cell">Ans</TableHead>
                        <TableHead className="w-10 md:w-24 text-xs"></TableHead>
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {filteredQuestions.map((question) => (
                        <TableRow key={question.id}>
                          <TableCell className="font-mono text-xs p-2">{question.id}</TableCell>
                          <TableCell className="p-2">
                            <CEFRBadge level={question.cefr} size="sm" />
                          </TableCell>
                          <TableCell className="max-w-[120px] md:max-w-md truncate text-xs p-2">
                            {question.prompt}
                          </TableCell>
                          <TableCell className="font-semibold text-xs p-2 hidden sm:table-cell">
                            {question.correct_answer || '-'}
                          </TableCell>
                          <TableCell className="p-1">
                            <Button
                              variant="ghost"
                              size="icon"
                              className="h-7 w-7"
                              onClick={() => deleteQuestion(question.id)}
                            >
                              <Trash2 className="w-3 h-3 text-destructive" />
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

          <TabsContent value="certificates">
            <AdminCertificates />
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
                {/* Download Template Section */}
                <div className="bg-primary/5 border border-primary/20 rounded-lg p-4 md:p-6">
                  <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
                    <div>
                      <h3 className="font-semibold text-sm md:text-base flex items-center gap-2">
                        <Download className="w-4 h-4" />
                        Step 1: Download Template
                      </h3>
                      <p className="text-xs md:text-sm text-muted-foreground mt-1">
                        Get the standard CSV template with correct format and example
                      </p>
                    </div>
                    <Button onClick={downloadTemplate} variant="outline" className="shrink-0">
                      <Download className="w-4 h-4 mr-2" />
                      Download Template
                    </Button>
                  </div>
                  
                  <div className="mt-4 text-xs text-muted-foreground space-y-1">
                    <p><strong>Required columns:</strong> cefr, prompt, options_a, options_b, options_c, options_d, correct_answer</p>
                    <p><strong>Optional columns:</strong> explanation, difficulty (1-5), q_type, tags, source</p>
                    <p><strong>CEFR levels:</strong> A1, A2, B1, B2, C1, C2</p>
                    <p><strong>correct_answer:</strong> A, B, C, or D</p>
                  </div>
                </div>

                {/* Upload Section */}
                <div className="border-2 border-dashed rounded-lg p-6 md:p-8 text-center">
                  <FileSpreadsheet className="w-10 h-10 md:w-12 md:h-12 mx-auto mb-4 text-muted-foreground" />
                  <p className="mb-2 font-medium text-sm md:text-base">Step 2: Upload Your CSV</p>
                  <p className="text-xs md:text-sm text-muted-foreground mb-4">
                    Fill the template with your questions and upload
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
                  <div className="space-y-4">
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

                    {importFailures.length > 0 && (
                      <div className="rounded-lg border bg-card p-4">
                        <p className="font-medium">Failed rows (first 20)</p>
                        <p className="text-sm text-muted-foreground mb-3">
                          These rows are usually malformed CSV (commas/newlines inside quotes) or missing required fields.
                        </p>
                        <div className="max-h-56 overflow-auto text-sm rounded-md border">
                          {importFailures.slice(0, 20).map((f) => (
                            <div
                              key={`${f.rowNumber}-${f.promptPreview}`}
                              className="grid grid-cols-[80px_1fr] gap-3 px-3 py-2 border-b last:border-b-0"
                            >
                              <div className="font-mono text-muted-foreground">Row {f.rowNumber}</div>
                              <div>
                                <div className="font-medium">{f.reason}</div>
                                <div className="text-muted-foreground truncate">{f.promptPreview}</div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
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
