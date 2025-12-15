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
          created_at: string
          current_cefr: Database["public"]["Enums"]["cefr_level"] | null
          display_name: string | null
          id: string
          last_practice_date: string | null
          streak: number | null
          user_id: string
          username: string | null
          xp: number | null
        }
        Insert: {
          created_at?: string
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          id?: string
          last_practice_date?: string | null
          streak?: number | null
          user_id: string
          username?: string | null
          xp?: number | null
        }
        Update: {
          created_at?: string
          current_cefr?: Database["public"]["Enums"]["cefr_level"] | null
          display_name?: string | null
          id?: string
          last_practice_date?: string | null
          streak?: number | null
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
      get_cefr_for_xp: {
        Args: { xp_value: number }
        Returns: Database["public"]["Enums"]["cefr_level"]
      }
      has_role: {
        Args: {
          _role: Database["public"]["Enums"]["app_role"]
          _user_id: string
        }
        Returns: boolean
      }
    }
    Enums: {
      app_role: "student" | "admin"
      cefr_level: "A1" | "A2" | "B1" | "B2" | "C1" | "C2"
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
    },
  },
} as const
