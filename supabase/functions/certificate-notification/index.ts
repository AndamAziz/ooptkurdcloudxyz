import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

interface NotificationRequest {
  type: 'earned' | 'verified';
  userId: string;
  certificateNumber: string;
  cefrLevel: string;
  certificateType: string;
  scorePercentage: number;
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    // Verify JWT authentication
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      console.error('Missing authorization header');
      return new Response(
        JSON.stringify({ error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabase = createClient(supabaseUrl, supabaseKey);

    // Verify the user token
    const token = authHeader.replace('Bearer ', '');
    const { data: { user }, error: authError } = await supabase.auth.getUser(token);

    if (authError || !user) {
      console.error('Invalid authentication token:', authError);
      return new Response(
        JSON.stringify({ error: 'Invalid authentication token' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    const { type, userId, certificateNumber, cefrLevel, certificateType, scorePercentage }: NotificationRequest = await req.json();

    console.log('Certificate notification request:', { type, userId, certificateNumber, cefrLevel, authenticatedUser: user.id });

    // Verify the authenticated user owns the certificate or is an admin
    if (userId !== user.id) {
      const { data: roleData } = await supabase
        .from('user_roles')
        .select('role')
        .eq('user_id', user.id)
        .single();

      if (roleData?.role !== 'admin') {
        console.error('Unauthorized: User does not own certificate and is not admin');
        return new Response(
          JSON.stringify({ error: 'Unauthorized: Cannot send notification for another user' }),
          { status: 403, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }
    }

    // Get user profile
    const { data: profile, error: profileError } = await supabase
      .from('profiles')
      .select('display_name, username')
      .eq('user_id', userId)
      .single();

    if (profileError) {
      console.error('Error fetching profile:', profileError);
      throw new Error('Failed to fetch user profile');
    }

    const userName = profile?.display_name || profile?.username || 'Student';

    // Generate personalized email content using Lovable AI
    const LOVABLE_API_KEY = Deno.env.get('LOVABLE_API_KEY');
    if (!LOVABLE_API_KEY) {
      throw new Error('LOVABLE_API_KEY is not configured');
    }

    const certTypeLabel = {
      'level_completion': 'Level Completion',
      'course_completion': 'Course Completion', 
      'excellence': 'Excellence Award'
    }[certificateType] || certificateType;

    const prompt = type === 'earned'
      ? `Write a brief, warm congratulatory email (2-3 sentences) for a student named "${userName}" who just earned a CEFR ${cefrLevel} ${certTypeLabel} certificate with ${scorePercentage}% score. Be encouraging and mention they can view their certificate. Don't include subject line or signature, just the body text.`
      : `Write a brief notification email (2-3 sentences) for a student named "${userName}" letting them know their CEFR ${cefrLevel} ${certTypeLabel} certificate has been verified by an administrator. Mention certificate number ${certificateNumber}. Be professional and congratulatory. Don't include subject line or signature, just the body text.`;

    const aiResponse = await fetch('https://ai.gateway.lovable.dev/v1/chat/completions', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${LOVABLE_API_KEY}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        model: 'google/gemini-2.5-flash-lite',
        messages: [
          { role: 'system', content: 'You are a friendly education platform assistant. Write concise, warm emails.' },
          { role: 'user', content: prompt },
        ],
      }),
    });

    if (!aiResponse.ok) {
      const errorText = await aiResponse.text();
      console.error('AI gateway error:', aiResponse.status, errorText);
      throw new Error('Failed to generate email content');
    }

    const aiData = await aiResponse.json();
    const emailBody = aiData.choices?.[0]?.message?.content || 'Congratulations on your certificate achievement!';

    const subject = type === 'earned'
      ? `🎉 Congratulations! You've earned your CEFR ${cefrLevel} Certificate`
      : `✅ Your CEFR ${cefrLevel} Certificate has been verified`;

    // Log the notification (in production, you'd send actual email via Resend etc.)
    console.log('Email notification generated:', {
      to: userId,
      subject,
      body: emailBody,
      certificateNumber,
    });

    return new Response(
      JSON.stringify({ 
        success: true, 
        message: 'Notification sent',
        emailContent: {
          subject,
          body: emailBody,
        }
      }),
      { headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );

  } catch (error) {
    console.error('Error in certificate-notification:', error);
    return new Response(
      JSON.stringify({ error: error instanceof Error ? error.message : 'Unknown error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
