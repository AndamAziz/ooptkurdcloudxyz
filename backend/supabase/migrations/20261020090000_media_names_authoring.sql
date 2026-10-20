-- Richer questions (reading passages, images, short videos), question authoring by staff,
-- bulk publishing of reviewed drafts, and staff correction of learner names.
--
-- * assessment_questions gains passage / media_type / media_path / media_caption. Media files live
--   in the public bucket 'question-media' (images <= 5 MB by convention, videos <= 25 MB); only
--   teachers and admins can upload.
-- * get_practice_questions_v2 returns the new fields (v1 is kept unchanged for older app builds).
-- * staff_create_question: teacher/admin adds a question; published at once (teacher-authored
--   counts as reviewed) or saved as a draft.
-- * staff_publish_drafts: approves and publishes every unreviewed draft of one level in one step.
-- * staff_set_display_name: teacher/admin corrects a learner's name. Certificates already issued
--   keep the name they were issued with; the new name is used for future certificates.
-- * admin_list_users is opened to teachers (read-only list); role and status changes stay admin-only.

alter table public.assessment_questions
  add column passage       text check (char_length(passage) <= 3000),
  add column media_type    text check (media_type in ('image', 'video')),
  add column media_path    text check (media_path ~ '^[A-Za-z0-9][A-Za-z0-9._/-]{0,199}\.(png|jpg|jpeg|webp|mp4)$'),
  add column media_caption text check (char_length(media_caption) <= 300),
  add constraint assessment_questions_media_pair check ((media_type is null) = (media_path is null));

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('question-media', 'question-media', true, 26214400,
        array['image/png', 'image/jpeg', 'image/webp', 'video/mp4'])
on conflict (id) do nothing;

create policy "question-media: staff upload" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'question-media'
              and ((select private.has_role('teacher')) or (select private.has_role('admin'))));

-- ---------------------------------------------------------------------------
-- Practice download with passages and media (no answer keys).
-- ---------------------------------------------------------------------------
create function public.get_practice_questions_v2(
  p_cefr_level public.cefr_level,
  p_after uuid default null,
  p_limit integer default 100
) returns table (
  id uuid,
  cefr_level public.cefr_level,
  skill text,
  prompt text,
  options text[],
  version integer,
  review_status text,
  updated_at timestamptz,
  passage text,
  media_type text,
  media_path text,
  media_caption text
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
begin
  perform private.require_learner();
  if p_limit is null or p_limit < 1 or p_limit > 200 then
    raise exception 'p_limit must be between 1 and 200' using errcode = '22023';
  end if;
  return query
    select q.id, q.cefr_level, q.skill, q.prompt, q.options, q.version, q.review_status, q.updated_at,
           q.passage, q.media_type, q.media_path, q.media_caption
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions_v2(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions_v2(public.cefr_level, uuid, integer) to authenticated;

-- Exam payload now carries passage and media too (same signature, so replace in place).
create or replace function private.exam_questions(p_ids uuid[])
returns jsonb
language sql stable security definer
set search_path = ''
as $$
  select coalesce(jsonb_agg(jsonb_strip_nulls(jsonb_build_object(
           'id', q.id, 'cefr_level', q.cefr_level, 'prompt', q.prompt, 'options', to_jsonb(q.options),
           'passage', q.passage, 'media_type', q.media_type, 'media_path', q.media_path,
           'media_caption', q.media_caption))
           order by o.ord), '[]'::jsonb)
    from unnest(p_ids) with ordinality as o(id, ord)
    join public.assessment_questions q on q.id = o.id;
$$;
revoke all on function private.exam_questions(uuid[]) from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Staff authoring
-- ---------------------------------------------------------------------------
create function public.staff_create_question(
  p_cefr_level public.cefr_level,
  p_skill text,
  p_prompt text,
  p_options text[],
  p_correct_option text,
  p_explanation text default null,
  p_passage text default null,
  p_media_type text default null,
  p_media_path text default null,
  p_media_caption text default null,
  p_publish boolean default true
) returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_id uuid;
  v_options text[];
begin
  select array_agg(btrim(o) order by n) into v_options from unnest(p_options) with ordinality as t(o, n);
  if p_cefr_level = 'PRE_A1' or coalesce(btrim(p_prompt), '') = '' then
    raise exception 'level A1-C2 and a prompt are required' using errcode = '22023';
  end if;
  if v_options is null or cardinality(v_options) not between 2 and 6
     or exists (select 1 from unnest(v_options) o where o = '')
     or (select count(distinct lower(o)) from unnest(v_options) o) <> cardinality(v_options) then
    raise exception '2-6 distinct, non-empty options are required' using errcode = '22023';
  end if;
  if upper(coalesce(p_correct_option, '')) !~ '^[A-F]$'
     or ascii(upper(p_correct_option)) - ascii('A') >= cardinality(v_options) then
    raise exception 'correct option must point to one of the options' using errcode = '22023';
  end if;

  insert into public.assessment_questions
    (cefr_level, skill, prompt, options, correct_option, explanation, passage, media_type, media_path, media_caption,
     status, review_status, source)
  values
    (p_cefr_level, p_skill, btrim(p_prompt), v_options, upper(p_correct_option), nullif(btrim(p_explanation), ''),
     nullif(btrim(p_passage), ''), p_media_type, nullif(btrim(p_media_path), ''), nullif(btrim(p_media_caption), ''),
     case when p_publish then 'published' else 'draft' end,
     case when p_publish then 'approved' else 'unreviewed' end,
     'staff')
  returning id into v_id;

  perform private.log_event(v_uid, 'staff.question_created', 'assessment_question', v_id,
                            jsonb_build_object('cefr_level', p_cefr_level, 'published', p_publish,
                                               'media_type', p_media_type));
  return v_id;
end;
$$;
revoke all on function public.staff_create_question(public.cefr_level, text, text, text[], text, text, text, text, text, text, boolean)
  from public, anon;
grant execute on function public.staff_create_question(public.cefr_level, text, text, text[], text, text, text, text, text, text, boolean)
  to authenticated;

create function public.staff_publish_drafts(p_cefr_level public.cefr_level)
returns integer
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_count integer;
begin
  update public.assessment_questions
     set status = 'published', review_status = 'approved'
   where cefr_level = p_cefr_level and status = 'draft' and review_status = 'unreviewed';
  get diagnostics v_count = row_count;
  perform private.log_event(v_uid, 'staff.drafts_published', null, null,
                            jsonb_build_object('cefr_level', p_cefr_level, 'count', v_count));
  return v_count;
end;
$$;
revoke all on function public.staff_publish_drafts(public.cefr_level) from public, anon;
grant execute on function public.staff_publish_drafts(public.cefr_level) to authenticated;

-- ---------------------------------------------------------------------------
-- Learner names
-- ---------------------------------------------------------------------------
create function public.staff_set_display_name(p_user_id uuid, p_display_name text)
returns text
language plpgsql security definer
set search_path = ''
as $$
declare
  v_uid uuid := private.require_staff(false);
  v_name text := btrim(coalesce(p_display_name, ''));
begin
  if char_length(v_name) not between 1 and 80 then
    raise exception 'display_name must be 1-80 characters' using errcode = '22023';
  end if;
  update public.profiles set display_name = v_name where user_id = p_user_id;
  if not found then
    raise exception 'user not found' using errcode = '22023';
  end if;
  -- The names themselves are personal data and are not copied into the log.
  perform private.log_event(v_uid, 'staff.display_name_changed', 'user', p_user_id, '{}'::jsonb);
  return v_name;
end;
$$;
revoke all on function public.staff_set_display_name(uuid, text) from public, anon;
grant execute on function public.staff_set_display_name(uuid, text) to authenticated;

-- Teachers may list learners (to correct names); role and status changes stay admin-only.
create or replace function public.admin_list_users(
  p_search text default null,
  p_limit integer default 50,
  p_offset integer default 0
) returns table (
  user_id uuid,
  email text,
  display_name text,
  roles public.app_role[],
  account_status public.account_status,
  created_at timestamptz,
  answered bigint,
  correct bigint,
  last_answer_at timestamptz
)
language plpgsql stable security definer
set search_path = ''
as $$
#variable_conflict use_column
declare
  v_pattern text;
begin
  perform private.require_staff(false);
  if p_limit is null or p_limit < 1 or p_limit > 200 or p_offset is null or p_offset < 0 then
    raise exception 'invalid paging' using errcode = '22023';
  end if;
  if p_search is not null and btrim(p_search) <> '' then
    v_pattern := '%' || replace(replace(replace(btrim(p_search), '\', '\\'), '%', '\%'), '_', '\_') || '%';
  end if;

  return query
    select p.user_id, u.email::text, p.display_name,
           coalesce((select array_agg(r.role order by r.role) from public.user_roles r where r.user_id = p.user_id), '{}'),
           p.account_status, p.created_at,
           (select count(*) from public.assessment_responses x where x.user_id = p.user_id),
           (select count(*) from public.assessment_responses x where x.user_id = p.user_id and x.is_correct),
           (select max(x.received_at) from public.assessment_responses x where x.user_id = p.user_id)
      from public.profiles p
      join auth.users u on u.id = p.user_id
     where v_pattern is null or p.display_name ilike v_pattern or u.email ilike v_pattern
     order by p.created_at desc, p.user_id
     limit p_limit offset p_offset;
end;
$$;
