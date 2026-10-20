-- Practice by topic (Life in the UK, Kurdistan, stories and dialogues, history and nature).
--
-- get_practice_questions_v3 is v2 plus the question's tags, so the app can group downloaded
-- questions by topic offline. v1 and v2 stay unchanged for older app builds.
create function public.get_practice_questions_v3(
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
  media_caption text,
  tags text[]
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
           q.passage, q.media_type, q.media_path, q.media_caption, q.tags
      from public.assessment_questions q
     where q.cefr_level = p_cefr_level
       and q.status = 'published'
       and (p_after is null or q.id > p_after)
     order by q.id
     limit p_limit;
end;
$$;
revoke all on function public.get_practice_questions_v3(public.cefr_level, uuid, integer) from public, anon;
grant execute on function public.get_practice_questions_v3(public.cefr_level, uuid, integer) to authenticated;
