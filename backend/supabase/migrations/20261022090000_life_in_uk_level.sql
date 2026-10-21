-- Life in the UK test preparation: its questions live in the question bank under their own
-- "level". A new enum value cannot be used in the transaction that adds it, so the functions
-- that must ignore it are replaced in the next migration.
alter type public.cefr_level add value if not exists 'LIFE_IN_UK';
