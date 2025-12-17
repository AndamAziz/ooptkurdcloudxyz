import { useState, useEffect } from 'react';
import { Pencil } from 'lucide-react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
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

interface QuestionEditDialogProps {
  question: Question;
  onUpdate: () => void;
}

export const QuestionEditDialog = ({ question, onUpdate }: QuestionEditDialogProps) => {
  const { toast } = useToast();
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [formData, setFormData] = useState<Question>(question);

  useEffect(() => {
    setFormData(question);
  }, [question]);

  const handleSave = async () => {
    setSaving(true);
    try {
      const { error } = await supabase
        .from('questions')
        .update({
          cefr: formData.cefr,
          difficulty: formData.difficulty,
          q_type: formData.q_type,
          prompt: formData.prompt,
          options_a: formData.options_a,
          options_b: formData.options_b,
          options_c: formData.options_c,
          options_d: formData.options_d,
          correct_answer: formData.correct_answer,
          explanation: formData.explanation,
          tags: formData.tags,
          source: formData.source,
        })
        .eq('id', question.id);

      if (error) throw error;

      toast({
        title: 'Question updated',
        description: `Question #${question.id} has been updated successfully.`,
      });
      setOpen(false);
      onUpdate();
    } catch (error) {
      console.error('Error updating question:', error);
      toast({
        title: 'Error',
        description: 'Failed to update question. Please try again.',
        variant: 'destructive',
      });
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button variant="ghost" size="icon" className="h-7 w-7">
          <Pencil className="w-3 h-3 text-primary" />
        </Button>
      </DialogTrigger>
      <DialogContent className="max-w-2xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>Edit Question #{question.id}</DialogTitle>
          <DialogDescription>
            Make changes to this question. Click save when you're done.
          </DialogDescription>
        </DialogHeader>

        <div className="grid gap-4 py-4">
          {/* Level and Difficulty Row */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>CEFR Level</Label>
              <Select
                value={formData.cefr}
                onValueChange={(value) => setFormData({ ...formData, cefr: value as CEFRLevel })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CEFR_LEVELS.map((level) => (
                    <SelectItem key={level} value={level}>
                      {level}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Difficulty (1-5)</Label>
              <Select
                value={String(formData.difficulty || 2)}
                onValueChange={(value) => setFormData({ ...formData, difficulty: parseInt(value) })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {[1, 2, 3, 4, 5].map((d) => (
                    <SelectItem key={d} value={String(d)}>
                      {d}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>

          {/* Prompt */}
          <div className="space-y-2">
            <Label>Prompt / Question</Label>
            <Textarea
              value={formData.prompt || ''}
              onChange={(e) => setFormData({ ...formData, prompt: e.target.value })}
              rows={3}
              className="resize-none"
            />
          </div>

          {/* Options */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Option A</Label>
              <Input
                value={formData.options_a || ''}
                onChange={(e) => setFormData({ ...formData, options_a: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>Option B</Label>
              <Input
                value={formData.options_b || ''}
                onChange={(e) => setFormData({ ...formData, options_b: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>Option C</Label>
              <Input
                value={formData.options_c || ''}
                onChange={(e) => setFormData({ ...formData, options_c: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>Option D</Label>
              <Input
                value={formData.options_d || ''}
                onChange={(e) => setFormData({ ...formData, options_d: e.target.value })}
              />
            </div>
          </div>

          {/* Correct Answer */}
          <div className="space-y-2">
            <Label>Correct Answer</Label>
            <Select
              value={formData.correct_answer || ''}
              onValueChange={(value) => setFormData({ ...formData, correct_answer: value })}
            >
              <SelectTrigger>
                <SelectValue placeholder="Select correct answer" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="A">A</SelectItem>
                <SelectItem value="B">B</SelectItem>
                <SelectItem value="C">C</SelectItem>
                <SelectItem value="D">D</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {/* Explanation */}
          <div className="space-y-2">
            <Label>Explanation (optional)</Label>
            <Textarea
              value={formData.explanation || ''}
              onChange={(e) => setFormData({ ...formData, explanation: e.target.value })}
              rows={2}
              className="resize-none"
              placeholder="Explain why this is the correct answer..."
            />
          </div>

          {/* Type and Tags Row */}
          <div className="grid grid-cols-2 gap-4">
            <div className="space-y-2">
              <Label>Question Type</Label>
              <Input
                value={formData.q_type || 'MCQ'}
                onChange={(e) => setFormData({ ...formData, q_type: e.target.value })}
                placeholder="MCQ"
              />
            </div>
            <div className="space-y-2">
              <Label>Tags (optional)</Label>
              <Input
                value={formData.tags || ''}
                onChange={(e) => setFormData({ ...formData, tags: e.target.value })}
                placeholder="grammar, vocabulary..."
              />
            </div>
          </div>

          {/* Source */}
          <div className="space-y-2">
            <Label>Source (optional)</Label>
            <Input
              value={formData.source || ''}
              onChange={(e) => setFormData({ ...formData, source: e.target.value })}
              placeholder="Book name, URL, etc."
            />
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={() => setOpen(false)} disabled={saving}>
            Cancel
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? 'Saving...' : 'Save Changes'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
