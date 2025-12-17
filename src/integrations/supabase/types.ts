export type Json =
  | string
  | number
  | boolean
  | null
  | { [key: string]: Json | undefined }
  | Json[]

export type Database = {
  // Allows to automatically instantiate createClient with right options
  // instead of createClient<Database, { PostgrestVersion: 'XX' }>(URL, KEY)
  __InternalSupabase: {
    PostgrestVersion: "13.0.5"
  }
  public: {
    Tables: {
      admin_logs: {
        Row: {
          action_type: string
          admin_id: string
          created_at: string
          details: Json | null
          id: string
          target_user_id: string | null
        }
        Insert: {
          action_type: string
          admin_id: string
          created_at?: string
          details?: Json | null
          id?: string
          target_user_id?: string | null
        }
        Update: {
          action_type?: string
          admin_id?: string
          created_at?: string
          details?: Json | null
          id?: string
          target_user_id?: string | null
        }
        Relationships: []
      }
      attempts: {
        Row: {
          chosen_answer: string | null
          created_at: string
          id: string
          is_correct: boolean | null
          question_id: number
          time_spent_sec: number | null
          user_id: string
        }
        Insert: {
          chosen_answer?: string | null
          created_at?: string
          id?: string
          is_correct?: boolean | null
          question_id: number
          time_spent_sec?: number | null
          user_id: string
        }
        Update: {
          chosen_answer?: string | null
          created_at?: string
          id?: string
          is_correct?: boolean | null
          question_id?: number
          time_spent_sec?: number | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "attempts_question_id_fkey"
            columns: ["question_id"]
            isOneToOne: false
            referencedRelation: "questions"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "attempts_question_id_fkey"
            columns: ["question_id"]
            isOneToOne: false
            referencedRelation: "questions_safe"
            referencedColumns: ["id"]
          },
        ]
      }
      certificates: {
        Row: {
          admin_notes: string | null
          cefr_level: Database["public"]["Enums"]["cefr_level"]
          certificate_number: string
          certificate_type: Database["public"]["Enums"]["certificate_type"]
          created_at: string
          id: string
          issued_at: string
          pdf_url: string | null
          score_percentage: number
          status: Database["public"]["Enums"]["certificate_status"]
          study_hours: number
          total_correct: number
          total_questions_answered: number
          updated_at: string
          user_id: string
          verified_by_admin: boolean
        }
        Insert: {
          admin_notes?: string | null
          cefr_level: Database["public"]["Enums"]["cefr_level"]
          certificate_number?: string
          certificate_type: Database["public"]["Enums"]["certificate_type"]
          created_at?: string
          id?: string
          issued_at?: string
          pdf_url?: string | null
          score_percentage?: number
          status?: Database["public"]["Enums"]["certificate_status"]
          study_hours?: number
          total_correct?: number
          total_questions_answered?: number
          updated_at?: string
          user_id: string
          verified_by_admin?: boolean
        }
        Update: {
          admin_notes?: string | null
          cefr_level?: Database["public"]["Enums"]["cefr_level"]
          certificate_number?: string
          certificate_type?: Database["public"]["Enums"]["certificate_type"]
          created_at?: string
          id?: string
          issued_at?: string
          pdf_url?: string | null
          score_percentage?: number
          status?: Database["public"]["Enums"]["certificate_status"]
          study_hours?: number
          total_correct?: number
          total_questions_answered?: number
          updated_at?: string
          user_id?: string
          verified_by_admin?: boolean
        }
        Relationships: []
      }
      levels: {
        Row: {
          cefr: Database["public"]["Enums"]["cefr_level"]
          description: string | null
          min_xp: number
        }
        Insert: {
          cefr: Database["public"]["Enums"]["cefr_level"]
          description?: string | null
          min_xp: number
        }
        Update: {
          cefr?: Database["public"]["Enums"]["cefr_level"]
          description?: string | null
          min_xp?: number
        }
        Relationships: []
      }
      placement_results: {
        Row: {
          completed_at: string | null
          correct_answers: number | null
          created_at: string | null
          id: string
          level_scores: Json | null
          recommended_level: Database["public"]["Enums"]["cefr_level"]
          total_questions: number | null
          user_id: string
        }
        Insert: {
          completed_at?: string | null
          correct_answers?: number | null
          created_at?: string | null
          id?: string
          level_scores?: Json | null
          recommended_level?: Database["public"]["Enums"]["cefr_level"]
          total_questions?: number | null
          user_id: string
        }
        Update: {
          completed_at?: string | null
          correct_answers?: number | null
          created_at?: string | null
          id?: string
          level_scores?: Json | null
          recommended_level?: Database["public"]["Enums"]["cefr_level"]
          total_questions?: number | null
          user_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "placement_results_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "leaderboard_view"
            referencedColumns: ["user_id"]
          },
          {
            foreignKeyName: "placement_results_user_id_fkey"
            columns: ["user_id"]
            isOneToOne: false
            referencedRelation: "profiles"
            referencedColumns: ["user_id"]
          },
        ]
      }
      practice_sessions: {
        Row: {
          accuracy: number | null
          cefr: Database["public"]["Enums"]["cefr_level"]
          correct_count: number | null
          ended_at: string | null
          id: string
          mode: string | null
          started_at: string
          summary_text: string | null
          total_questions: number | null
          user_id: string
          xp_earned: number | null
        }
        Insert: {
          accuracy?: number | null
          cefr: Database["public"]["Enums"]["cefr_level"]
          correct_count?: number | null
          ended_at?: string | null
          id?: string
          mode?: string | null
          started_at?: string
          summary_text?: string | null
          total_questions?: number | null
          user_id: string
          xp_earned?: number | null
        }
        Update: {
          accuracy?: number | null
          cefr?: Database["public"]["Enums"]["cefr_level"]
          correct_count?: number | null
          ended_at?: string | null
          id?: string
          mode?: string | null
          started_at?: string
          summary_text?: string | null
          total_questions?: number | null
          user_id?: string
          xp_earned?: number | null
        }
        Relationships: []
      }
      profiles: {
        Row: {
          account_status: string | null
          created_at: string
          current_cefr: Database["public"]["Enums"]["cefr_level"] | null
          display_name: string | null
          id: string
          last_active_at: string | null
          last_practice_date: string | null
          longest_streak: number | null
          placement_completed: boolean | null
          placement_date: string | null
          placement_score: number | null
          registration_source: string | null
          streak: number | null
          suspend_reason: string | null
          total_practice_time_minutes: number | null
          user_id: string
          username: string | null
          xp: number | null
        }
        Insert: {
          account_status?: string | null
          created_at?: string
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          id?: string
          last_active_at?: string | null
          last_practice_date?: string | null
          longest_streak?: number | null
          placement_completed?: boolean | null
          placement_date?: string | null
          placement_score?: number | null
          registration_source?: string | null
          streak?: number | null
          suspend_reason?: string | null
          total_practice_time_minutes?: number | null
          user_id: string
          username?: string | null
          xp?: number | null
        }
        Update: {
          account_status?: string | null
          created_at?: string
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          id?: string
          last_active_at?: string | null
          last_practice_date?: string | null
          longest_streak?: number | null
          placement_completed?: boolean | null
          placement_date?: string | null
          placement_score?: number | null
          registration_source?: string | null
          streak?: number | null
          suspend_reason?: string | null
          total_practice_time_minutes?: number | null
          user_id?: string
          username?: string | null
          xp?: number | null
        }
        Relationships: []
      }
      questions: {
        Row: {
          cefr: Database["public"]["Enums"]["cefr_level"]
          correct_answer: string | null
          created_at: string
          difficulty: number | null
          explanation: string | null
          id: number
          options_a: string | null
          options_b: string | null
          options_c: string | null
          options_d: string | null
          prompt: string
          q_type: string | null
          source: string | null
          tags: string | null
        }
        Insert: {
          cefr: Database["public"]["Enums"]["cefr_level"]
          correct_answer?: string | null
          created_at?: string
          difficulty?: number | null
          explanation?: string | null
          id?: number
          options_a?: string | null
          options_b?: string | null
          options_c?: string | null
          options_d?: string | null
          prompt: string
          q_type?: string | null
          source?: string | null
          tags?: string | null
        }
        Update: {
          cefr?: Database["public"]["Enums"]["cefr_level"]
          correct_answer?: string | null
          created_at?: string
          difficulty?: number | null
          explanation?: string | null
          id?: number
          options_a?: string | null
          options_b?: string | null
          options_c?: string | null
          options_d?: string | null
          prompt?: string
          q_type?: string | null
          source?: string | null
          tags?: string | null
        }
        Relationships: []
      }
      rate_limits: {
        Row: {
          action: string
          created_at: string
          id: string
          user_id: string
        }
        Insert: {
          action: string
          created_at?: string
          id?: string
          user_id: string
        }
        Update: {
          action?: string
          created_at?: string
          id?: string
          user_id?: string
        }
        Relationships: []
      }
      user_roles: {
        Row: {
          id: string
          role: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Insert: {
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id: string
        }
        Update: {
          id?: string
          role?: Database["public"]["Enums"]["app_role"]
          user_id?: string
        }
        Relationships: []
      }
    }
    Views: {
      leaderboard_view: {
        Row: {
          current_cefr: Database["public"]["Enums"]["cefr_level"] | null
          display_name: string | null
          user_id: string | null
          username: string | null
          xp: number | null
        }
        Insert: {
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          user_id?: string | null
          username?: string | null
          xp?: number | null
        }
        Update: {
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          user_id?: string | null
          username?: string | null
          xp?: number | null
        }
        Relationships: []
      }
      questions_safe: {
        Row: {
          cefr: Database["public"]["Enums"]["cefr_level"] | null
          created_at: string | null
          difficulty: number | null
          id: number | null
          options_a: string | null
          options_b: string | null
          options_c: string | null
          options_d: string | null
          prompt: string | null
          q_type: string | null
          source: string | null
          tags: string | null
        }
        Insert: {
          cefr?: Database["public"]["Enums"]["cefr_level"] | null
          created_at?: string | null
          difficulty?: number | null
          id?: number | null
          options_a?: string | null
          options_b?: string | null
          options_c?: string | null
          options_d?: string | null
          prompt?: string | null
          q_type?: string | null
          source?: string | null
          tags?: string | null
        }
        Update: {
          cefr?: Database["public"]["Enums"]["cefr_level"] | null
          created_at?: string | null
          difficulty?: number | null
          id?: number | null
          options_a?: string | null
          options_b?: string | null
          options_c?: string | null
          options_d?: string | null
          prompt?: string | null
          q_type?: string | null
          source?: string | null
          tags?: string | null
        }
        Relationships: []
      }
    }
    Functions: {
      check_answer: {
        Args: { p_chosen_answer: string; p_question_id: number }
        Returns: {
          correct_answer: string
          explanation: string
          is_correct: boolean
        }[]
      }
      check_rate_limit: {
        Args: {
          p_action?: string
          p_max_count?: number
          p_user_id: string
          p_window_minutes?: number
        }
        Returns: boolean
      }
      complete_placement_test: {
        Args: {
          p_correct_answers: number
          p_level_scores: Json
          p_recommended_level: Database["public"]["Enums"]["cefr_level"]
          p_user_id: string
        }
        Returns: string
      }
      generate_certificate_number: { Args: never; Returns: string }
      get_cefr_for_xp: {
        Args: { xp_value: number }
        Returns: Database["public"]["Enums"]["cefr_level"]
      }
      get_placement_questions: {
        Args: { p_user_id: string }
        Returns: {
          cefr: Database["public"]["Enums"]["cefr_level"]
          difficulty: number
          id: number
          options_a: string
          options_b: string
          options_c: string
          options_d: string
          prompt: string
        }[]
      }
      get_question_progress: {
        Args: { p_user_id: string }
        Returns: {
          accuracy: number
          answered_questions: number
          can_earn_certificate: boolean
          cefr: Database["public"]["Enums"]["cefr_level"]
          certificate_target: number
          completion_percent: number
          correct_count: number
          correct_needed_for_cert: number
          remaining_questions: number
          total_questions: number
          wrong_count: number
        }[]
      }
      get_random_questions: {
        Args: {
          p_cefr: Database["public"]["Enums"]["cefr_level"]
          p_limit?: number
          p_user_id: string
        }
        Returns: {
          cefr: Database["public"]["Enums"]["cefr_level"] | null
          created_at: string | null
          difficulty: number | null
          id: number | null
          options_a: string | null
          options_b: string | null
          options_c: string | null
          options_d: string | null
          prompt: string | null
          q_type: string | null
          source: string | null
          tags: string | null
        }[]
        SetofOptions: {
          from: "*"
          to: "questions_safe"
          isOneToOne: false
          isSetofReturn: true
        }
      }
      has_role: {
        Args: {
          _role: Database["public"]["Enums"]["app_role"]
          _user_id: string
        }
        Returns: boolean
      }
      increment_user_xp: {
        Args: { _user_id: string; _xp_increment: number }
        Returns: undefined
      }
      reset_level_progress: {
        Args: {
          p_cefr: Database["public"]["Enums"]["cefr_level"]
          p_user_id: string
        }
        Returns: undefined
      }
      skip_placement_test: { Args: { p_user_id: string }; Returns: undefined }
    }
    Enums: {
      app_role: "student" | "admin"
      cefr_level: "A1" | "A2" | "B1" | "B2" | "C1" | "C2"
      certificate_status: "earned" | "pending" | "revoked"
      certificate_type: "level_completion" | "course_completion" | "excellence"
    }
    CompositeTypes: {
      [_ in never]: never
    }
  }
}

type DatabaseWithoutInternals = Omit<Database, "__InternalSupabase">

type DefaultSchema = DatabaseWithoutInternals[Extract<keyof Database, "public">]

export type Tables<
  DefaultSchemaTableNameOrOptions extends
    | keyof (DefaultSchema["Tables"] & DefaultSchema["Views"])
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
        DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? (DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"] &
      DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Views"])[TableName] extends {
      Row: infer R
    }
    ? R
    : never
  : DefaultSchemaTableNameOrOptions extends keyof (DefaultSchema["Tables"] &
        DefaultSchema["Views"])
    ? (DefaultSchema["Tables"] &
        DefaultSchema["Views"])[DefaultSchemaTableNameOrOptions] extends {
        Row: infer R
      }
      ? R
      : never
    : never

export type TablesInsert<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Insert: infer I
    }
    ? I
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Insert: infer I
      }
      ? I
      : never
    : never

export type TablesUpdate<
  DefaultSchemaTableNameOrOptions extends
    | keyof DefaultSchema["Tables"]
    | { schema: keyof DatabaseWithoutInternals },
  TableName extends DefaultSchemaTableNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"]
    : never = never,
> = DefaultSchemaTableNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaTableNameOrOptions["schema"]]["Tables"][TableName] extends {
      Update: infer U
    }
    ? U
    : never
  : DefaultSchemaTableNameOrOptions extends keyof DefaultSchema["Tables"]
    ? DefaultSchema["Tables"][DefaultSchemaTableNameOrOptions] extends {
        Update: infer U
      }
      ? U
      : never
    : never

export type Enums<
  DefaultSchemaEnumNameOrOptions extends
    | keyof DefaultSchema["Enums"]
    | { schema: keyof DatabaseWithoutInternals },
  EnumName extends DefaultSchemaEnumNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"]
    : never = never,
> = DefaultSchemaEnumNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[DefaultSchemaEnumNameOrOptions["schema"]]["Enums"][EnumName]
  : DefaultSchemaEnumNameOrOptions extends keyof DefaultSchema["Enums"]
    ? DefaultSchema["Enums"][DefaultSchemaEnumNameOrOptions]
    : never

export type CompositeTypes<
  PublicCompositeTypeNameOrOptions extends
    | keyof DefaultSchema["CompositeTypes"]
    | { schema: keyof DatabaseWithoutInternals },
  CompositeTypeName extends PublicCompositeTypeNameOrOptions extends {
    schema: keyof DatabaseWithoutInternals
  }
    ? keyof DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"]
    : never = never,
> = PublicCompositeTypeNameOrOptions extends {
  schema: keyof DatabaseWithoutInternals
}
  ? DatabaseWithoutInternals[PublicCompositeTypeNameOrOptions["schema"]]["CompositeTypes"][CompositeTypeName]
  : PublicCompositeTypeNameOrOptions extends keyof DefaultSchema["CompositeTypes"]
    ? DefaultSchema["CompositeTypes"][PublicCompositeTypeNameOrOptions]
    : never

export const Constants = {
  public: {
    Enums: {
      app_role: ["student", "admin"],
      cefr_level: ["A1", "A2", "B1", "B2", "C1", "C2"],
      certificate_status: ["earned", "pending", "revoked"],
      certificate_type: ["level_completion", "course_completion", "excellence"],
    },
  },
} as const
