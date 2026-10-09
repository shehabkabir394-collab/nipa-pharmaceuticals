-- Allow each signed-in account to sync only its own app data row.
-- Safe to run more than once in Supabase SQL Editor.
create table if not exists public.app_user_state (
  user_id uuid primary key references auth.users(id) on delete cascade,
  data jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);

alter table public.app_user_state enable row level security;

drop policy if exists "app_user_state_select_own" on public.app_user_state;
create policy "app_user_state_select_own"
  on public.app_user_state for select to authenticated
  using (auth.uid() = user_id);

drop policy if exists "app_user_state_insert_own" on public.app_user_state;
create policy "app_user_state_insert_own"
  on public.app_user_state for insert to authenticated
  with check (auth.uid() = user_id);

drop policy if exists "app_user_state_update_own" on public.app_user_state;
create policy "app_user_state_update_own"
  on public.app_user_state for update to authenticated
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "app_user_state_delete_own" on public.app_user_state;
create policy "app_user_state_delete_own"
  on public.app_user_state for delete to authenticated
  using (auth.uid() = user_id);

create unique index if not exists app_user_state_user_id_uidx
  on public.app_user_state(user_id);

revoke all on public.app_user_state from anon, public;
grant select, insert, update, delete on public.app_user_state to authenticated;
