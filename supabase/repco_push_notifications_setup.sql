-- Run once in the SAME Supabase project used by Rephco.
create table if not exists public.repco_device_tokens (
  user_id uuid not null references auth.users(id) on delete cascade,
  device_token text not null,
  platform text not null default 'android',
  updated_at timestamptz not null default now(),
  primary key (user_id, device_token)
);
alter table public.repco_device_tokens enable row level security;
drop policy if exists "Users manage their own Repco device tokens" on public.repco_device_tokens;
create policy "Users manage their own Repco device tokens" on public.repco_device_tokens
  for all to authenticated
  using (user_id=(select auth.uid()))
  with check (user_id=(select auth.uid()));
grant select, insert, update, delete on public.repco_device_tokens to authenticated;
