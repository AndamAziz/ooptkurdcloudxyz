-- The two questions retired for having no picture are complete again: the chapter PDFs the owner
-- supplied carry the photograph one asks about and the four plates the other chooses between,
-- and both are now in content/theory/uk-theory-questions.json with that picture.
--
-- They were published before they were retired, in chapters that are published, so they go back
-- as published rather than waiting as drafts. The answers given to them before are untouched.
update public.theory_questions
   set status = 'published', updated_at = now()
 where external_ref in ('theory:hazard-awareness:245', 'theory:vehicle-handling:360')
   and status = 'retired';
