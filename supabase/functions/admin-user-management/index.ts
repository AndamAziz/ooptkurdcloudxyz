import { createClient } from 'https://esm.sh/@supabase/supabase-js@2.49.1';

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
};

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
      return new Response(
        JSON.stringify({ error: 'Missing authorization header' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

    // Create a client with the user's token to verify their identity
    const supabaseUser = createClient(supabaseUrl, supabaseAnonKey, {
      global: {
        headers: { Authorization: authHeader },
      },
      auth: {
        autoRefreshToken: false,
        persistSession: false,
      },
    });

    // Verify the caller's token
    const { data: { user }, error: authError } = await supabaseUser.auth.getUser();
    
    if (authError || !user) {
      console.error('Auth error:', authError);
      return new Response(
        JSON.stringify({ error: 'Invalid token' }),
        { status: 401, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
      );
    }

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

        // Log admin action
        await supabaseAdmin.from('admin_logs').insert({
          admin_id: user.id,
          target_user_id: userId,
          action_type: 'user_suspended',
          details: { timestamp: new Date().toISOString(), reason: reason || null },
        });

        return new Response(
          JSON.stringify({ success: true, message: 'User suspended successfully' }),
          { status: 200, headers: { ...corsHeaders, 'Content-Type': 'application/json' } }
        );
      }

      case 'activate_user': {
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
          details: { timestamp: new Date().toISOString() },
        });

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
