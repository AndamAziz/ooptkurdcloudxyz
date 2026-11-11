-- Two chapter 1-10 questions cannot be answered as the source wrote them: one asks what the main
-- hazard in a photograph is, the other which plate goes with a road sign, and neither the
-- photograph nor the sign was ever part of the data. Every other question of that kind was
-- repaired instead - given the sign chapter 11 already teaches, given its four options as one
-- picture, or reworded to say in words what the picture showed.
--
-- They are retired rather than deleted: answers already given to a question are kept, and the
-- repository no longer carries them, so nothing puts them back.
update public.theory_questions
   set status = 'retired', updated_at = now()
 where external_ref in ('theory:hazard-awareness:245', 'theory:vehicle-handling:360')
   and status <> 'retired';
