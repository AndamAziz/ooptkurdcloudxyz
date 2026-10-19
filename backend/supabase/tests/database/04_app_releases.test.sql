-- In-app update metadata: public read of published releases only; no client writes.
begin;
select plan(8);

insert into public.app_releases (version_code, version_name, apk_path, sha256, size_bytes, published) values
  (900001, '9.0.1', 'englishmaster-9.0.1.apk', repeat('a', 64), 1000, true),
  (900002, '9.0.2', 'englishmaster-9.0.2.apk', repeat('b', 64), 1000, false);

set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select is((select max(version_code) from public.app_releases where version_code >= 900000), 900001,
  'anon sees the published release but not the unpublished one');
select throws_ok($$insert into public.app_releases (version_code, version_name, apk_path, sha256, size_bytes, published)
                   values (900003, 'x', 'x.apk', repeat('c', 64), 1, true)$$, '42501', null, 'anon cannot publish releases');

reset role;
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated"}', true);
select is((select count(*)::int from public.app_releases where version_code >= 900000), 1, 'signed-in users see only published releases');
select throws_ok($$update public.app_releases set published = true where version_code = 900002$$,
  '42501', null, 'signed-in users cannot publish releases');

reset role;
set local role service_role;
select lives_ok($$update public.app_releases set published = true where version_code = 900002$$, 'release pipeline (service role) can publish');

reset role;
select throws_ok($$insert into public.app_releases (version_code, version_name, apk_path, sha256, size_bytes) values (1, 'x', 'x.apk', 'not-a-hash', 1)$$,
  '23514', null, 'sha256 must be 64 lowercase hex characters');
select throws_ok($$insert into public.app_releases (version_code, version_name, apk_path, sha256, size_bytes, min_supported_version_code)
                   values (5, 'x', 'x.apk', repeat('d', 64), 1, 6)$$, '23514', null, 'minimum supported version cannot exceed the release');
select is((select public from storage.buckets where id = 'app-releases'), true, 'release bucket exists and is publicly readable');

select * from finish();
rollback;
