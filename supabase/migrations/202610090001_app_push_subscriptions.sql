-- Store one Web Push subscription per signed-in device.
create table if not exists public.app_push_subscriptions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  endpoint text not null unique,
  subscription jsonb not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.app_push_subscriptions enable row level security;

drop policy if exists "Users can read own push subscriptions" on public.app_push_subscriptions;
create policy "Users can read own push subscriptions"
  on public.app_push_subscriptions for select
  to authenticated using (auth.uid() = user_id);

drop policy if exists "Users can add own push subscriptions" on public.app_push_subscriptions;
create policy "Users can add own push subscriptions"
  on public.app_push_subscriptions for insert
  to authenticated with check (auth.uid() = user_id);

drop policy if exists "Users can update own push subscriptions" on public.app_push_subscriptions;
create policy "Users can update own push subscriptions"
  on public.app_push_subscriptions for update
  to authenticated using (auth.uid() = user_id) with check (auth.uid() = user_id);

drop policy if exists "Users can remove own push subscriptions" on public.app_push_subscriptions;
create policy "Users can remove own push subscriptions"
  on public.app_push_subscriptions for delete
  to authenticated using (auth.uid() = user_id);

create index if not exists app_push_subscriptions_user_id_idx
  on public.app_push_subscriptions(user_id);
