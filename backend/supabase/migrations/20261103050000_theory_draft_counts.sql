-- Which chapters have questions waiting to be published.
--
-- The staff page listed every chapter with its published count and a "Publish the drafts" button,
-- and nothing else. A chapter holding 26 imported drafts looked exactly like an empty one, so the
-- button got pressed on chapters that had nothing waiting while the full one was left alone. This
-- gives the page the number it was missing.
--
-- Staff only: a learner has no business knowing what is still being checked.
create function public.staff_theory_draft_counts()
returns table (section_slug text, drafts integer)
language plpgsql
security definer
set search_path = ''
as $$
begin
  perform private.require_staff(false);
  return query
    select s.slug, count(q.id)::integer
      from public.theory_sections s
      join public.theory_questions q on q.section_id = s.id and q.status = 'draft'
     group by s.slug;
end;
$$;
comment on function public.staff_theory_draft_counts() is
  'Staff: how many questions are waiting to be published, per chapter.';
revoke all on function public.staff_theory_draft_counts() from public, anon;
grant execute on function public.staff_theory_draft_counts() to authenticated;
