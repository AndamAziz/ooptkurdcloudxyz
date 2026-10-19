-- EnglishMaster AI — in-app updates for directly distributed (non-Play) builds.
--
-- * app_releases: one row per published Android build. Readable by everyone (the app
--   checks for updates before sign-in); written only by the release pipeline using the
--   service role. sha256 lets the app verify the downloaded APK before installing it.
-- * storage bucket app-releases: public read so the app can download without a session;
--   no write policies, so only the service role (GitHub Actions) can upload.

create table public.app_releases (
  version_code               integer primary key check (version_code > 0),
  version_name               text not null check (char_length(version_name) between 1 and 40),
  apk_path                   text not null check (apk_path ~ '^[A-Za-z0-9._/-]+\.apk$'),
  sha256                     text not null check (sha256 ~ '^[0-9a-f]{64}$'),
  size_bytes                 bigint not null check (size_bytes > 0),
  min_supported_version_code integer not null default 1 check (min_supported_version_code > 0),
  notes_en                   text check (char_length(notes_en) <= 2000),
  notes_ckb                  text check (char_length(notes_ckb) <= 2000),
  published                  boolean not null default false,
  created_at                 timestamptz not null default now(),
  check (min_supported_version_code <= version_code)
);
comment on table public.app_releases is
  'Android builds for in-app updates. Public read of published rows; writes by the release pipeline (service role) only.';

alter table public.app_releases enable row level security;
revoke all on public.app_releases from anon, authenticated;
grant select on public.app_releases to anon, authenticated;

create policy "app_releases: published are public"
  on public.app_releases for select to anon, authenticated
  using (published);

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('app-releases', 'app-releases', true, 104857600, array['application/vnd.android.package-archive'])
on conflict (id) do nothing;
