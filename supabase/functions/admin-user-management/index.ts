import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.49.1';
import { Resend } from 'https://esm.sh/resend@2.0.0';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

const resend = new Resend(Deno.env.get('RESEND_API_KEY'));

interface UserProfile {
  display_name: string | null;
  username: string | null;
}

async function sendNotificationEmail(
  email: string,
  action: 'suspended' | 'activated',
  profile: UserProfile,
  reason?: string | null
) {
  const name = profile.display_name || profile.username || 'Student';

  const subject = action === 'suspended'
    ? 'Your CEFR Practice Account Has Been Suspended'
    : 'Your CEFR Practice Account Has Been Reactivated';

  const html = action === 'suspended'
    ? `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
        <h1 style="color: #dc2626;">Account Suspended</h1>
        <p>Dear ${name},</p>
        <p>Your CEFR Practice account has been suspended by an administrator.</p>
        ${reason ? `
          <div style="background: #fef2f2; border-left: 4px solid #dc2626; padding: 12px 16px; margin: 16px 0;">
            <strong>Reason:</strong><br/>
            ${reason}
          </div>
        ` : ''}
        <p>While your account is suspended, you will not be able to:</p>
        <ul>
          <li>Practice questions</li>
          <li>Earn XP or certificates</li>
          <li>Update your profile</li>
        </ul>
        <p>If you believe this was a mistake or have questions, please contact our support team.</p>
        <p style="color: #6b7280; font-size: 14px; margin-top: 24px;">
          Best regards,<br/>
          CEFR Practice Team
        </p>
      </div>
    `
    : `
      <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
        <h1 style="color: #16a34a;">Account Reactivated</h1>
        <p>Dear ${name},</p>
        <p>Great news! Your CEFR Practice account has been reactivated.</p>
        <p>You can now:</p>
        <ul>
          <li>Continue practicing questions</li>
          <li>Earn XP and level up</li>
          <li>Work towards your certificates</li>
        </ul>
        <p>Welcome back! We're glad to have you continue your English learning journey.</p>
        <p style="color: #6b7280; font-size: 14px; margin-top: 24px;">
          Best regards,<br/>
          CEFR Practice Team
        </p>
      </div>
    `;

  try {
    const { error } = await resend.emails.send({
      from: 'CEFR Practice <onboarding@resend.dev>',
      to: [email],
      subject,
      html,
    });

    if (error) {
      console.error('Failed to send notification email:', error);
    } else {
      console.log(`Notification email sent to ${email} for ${action}`);
    }
  } catch (err) {
    console.error('Error sending notification email:', err);
  }
}

Deno.serve(async (req) => {
  // Handle CORS preflight requests
  if (req.method === 'OPTIONS') {
    return new Response(null, { headers: corsHeaders });
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!;
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY')!;

    // Create admin client with service role key
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false,
      },
    });

    // Get the authorization header to verify the caller is an admin
    const authHeader = req.headers.get('Authorization');
    if (!authHeader) {
      console.error('Missing authorization header');
      return new Response(
        JSON.stringify({ error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Extract the JWT token from the Authorization header
    const token = authHeader.replace('Bearer ', '');
    
    if (!token || token === authHeader) {
      console.error('Invalid authorization header format');
      return new Response(
        JSON.stringify({ error: 'Invalid authorization header format' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Verify the caller's token using the admin client
    const { data: { user }, error: authError } = await supabaseAdmin.auth.getUser(token);
    
    if (authError || !user) {
      console.error('Auth error:', authError);
      console.error('Token length:', token?.length || 0);
      return new Response(
        JSON.stringify({ error: 'Invalid token' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    console.log('Authenticated user:', user.id);

    // Check if caller is an admin
    const { data: roleData, error: roleError } = await supabaseAdmin
      .from('user_roles')
      .select('role')
      .eq('user_id', user.id)
      .eq('role', 'admin')
      .single();

    if (roleError || !roleData) {
      return new Response(
        JSON.stringify({ error: 'Unauthorized: Admin access required' }),
        { status: 403, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Parse request body
    const { action, userId, newPassword, reason } = await req.json();

    if (!action || !userId) {
      return new Response(
        JSON.stringify({ error: 'Missing required fields: action and userId' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Prevent admin from modifying their own account through this endpoint
    if (userId === user.id) {
      return new Response(
        JSON.stringify({ error: 'Cannot modify your own account through this endpoint' }),
        { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Helper to get target user's email and profile
    const getTargetUserInfo = async () => {
      const [authResult, profileResult] = await Promise.all([
        supabaseAdmin.auth.admin.getUserById(userId),
        supabaseAdmin.from('profiles').select('display_name, username').eq('user_id', userId).single(),
      ]);
      return {
        email: authResult.data?.user?.email,
        profile: profileResult.data as UserProfile | null,
      };
    };

    switch (action) {
      case 'reset_password': {
        if (!newPassword || newPassword.length < 6) {
          return new Response(
            JSON.stringify({ error: 'Password must be at least 6 characters' }),
            { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        const { error: updateError } = await supabaseAdmin.auth.admin.updateUserById(
          userId,
          { password: newPassword }
        );

        if (updateError) {
          console.error('Error resetting password:', updateError);
          return new Response(
            JSON.stringify({ error: 'Failed to reset password' }),
            { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        // Log admin action
        await supabaseAdmin.from('admin_logs').insert({
          admin_id: user.id,
          target_user_id: userId,
          action_type: 'password_reset',
          details: { timestamp: new Date().toISOString() },
        });

        return new Response(
          JSON.stringify({ success: true, message: 'Password reset successfully' }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      case 'delete_user': {
        // Delete user from auth (this will cascade to profiles due to foreign key)
        const { error: deleteError } = await supabaseAdmin.auth.admin.deleteUser(userId);

        if (deleteError) {
          console.error('Error deleting user:', deleteError);
          return new Response(
            JSON.stringify({ error: 'Failed to delete user' }),
            { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        // Log admin action
        await supabaseAdmin.from('admin_logs').insert({
          admin_id: user.id,
          target_user_id: userId,
          action_type: 'user_deleted',
          details: { timestamp: new Date().toISOString() },
        });

        return new Response(
          JSON.stringify({ success: true, message: 'User deleted successfully' }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      case 'suspend_user': {
        // Get user info for email notification
        const { email: targetEmail, profile: targetProfile } = await getTargetUserInfo();

        // Update user status and reason in profiles
        const { error: suspendError } = await supabaseAdmin
          .from('profiles')
          .update({ 
            account_status: 'suspended',
            suspend_reason: reason || null,
          })
          .eq('user_id', userId);

        if (suspendError) {
          console.error('Error suspending user:', suspendError);
          return new Response(
            JSON.stringify({ error: 'Failed to suspend user' }),
            { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        // Log admin action with reason
        await supabaseAdmin.from('admin_logs').insert({
          admin_id: user.id,
          target_user_id: userId,
          action_type: 'user_suspended',
          details: { 
            timestamp: new Date().toISOString(), 
            reason: reason || null,
            target_email: targetEmail,
            target_name: targetProfile?.display_name || targetProfile?.username,
          },
        });

        // Send notification email (fire-and-forget)
        if (targetEmail && targetProfile) {
          sendNotificationEmail(targetEmail, 'suspended', targetProfile, reason).catch(console.error);
        }

        return new Response(
          JSON.stringify({ success: true, message: 'User suspended successfully' }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      case 'activate_user': {
        // Get user info for email notification
        const { email: targetEmail, profile: targetProfile } = await getTargetUserInfo();

        // Update user status and clear reason in profiles
        const { error: activateError } = await supabaseAdmin
          .from('profiles')
          .update({ 
            account_status: 'active',
            suspend_reason: null,
          })
          .eq('user_id', userId);

        if (activateError) {
          console.error('Error activating user:', activateError);
          return new Response(
            JSON.stringify({ error: 'Failed to activate user' }),
            { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
          );
        }

        // Log admin action
        await supabaseAdmin.from('admin_logs').insert({
          admin_id: user.id,
          target_user_id: userId,
          action_type: 'user_activated',
          details: { 
            timestamp: new Date().toISOString(),
            target_email: targetEmail,
            target_name: targetProfile?.display_name || targetProfile?.username,
          },
        });

        // Send notification email (fire-and-forget)
        if (targetEmail && targetProfile) {
          sendNotificationEmail(targetEmail, 'activated', targetProfile).catch(console.error);
        }

        return new Response(
          JSON.stringify({ success: true, message: 'User activated successfully' }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      default:
        return new Response(
          JSON.stringify({ error: 'Invalid action' }),
          { status: 400, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
    }
  } catch (error) {
    console.error('Error in admin-user-management:', error);
    return new Response(
      JSON.stringify({ error: 'Internal server error' }),
      { status: 500, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
    );
  }
});
