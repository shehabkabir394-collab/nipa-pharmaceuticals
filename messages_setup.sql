-- Persistent direct messages for signed-in NIPA accounts.
create table if not exists public.app_messages (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles(id) on delete cascade,
  recipient_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 4000),
  created_at timestamptz not null default now(),
  read_at timestamptz,
  constraint app_messages_no_self_message check (sender_id <> recipient_id)
);

create index if not exists app_messages_recipient_unread_idx
  on public.app_messages (recipient_id, read_at, created_at desc);
create index if not exists app_messages_sender_recipient_idx
  on public.app_messages (sender_id, recipient_id, created_at desc);

alter table public.app_messages enable row level security;
drop policy if exists "message participants can read" on public.app_messages;
create policy "message participants can read"
  on public.app_messages for select to authenticated
  using (auth.uid() = sender_id or auth.uid() = recipient_id);

revoke all on public.app_messages from public, anon, authenticated;
grant select on public.app_messages to authenticated;

create or replace function public.get_message_contacts()
returns table (id uuid, full_name text, email text, mpo_code text, role text)
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
begin
  if v_user_id is null or not exists (
    select 1 from public.profiles as me where me.id = v_user_id and me.active is true
  ) then
    raise exception 'Active signed-in account required';
  end if;

  return query
    select p.id::uuid, p.full_name::text, p.email::text, p.mpo_code::text, p.role::text
    from public.profiles as p
    where p.active is true and p.id <> v_user_id
    order by case when p.role = 'admin' then 0 else 1 end, lower(coalesce(p.full_name, p.email));
end;
$$;

create or replace function public.send_app_message(p_recipient_id uuid, p_body text)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_message public.app_messages%rowtype;
  v_body text := btrim(coalesce(p_body, ''));
begin
  if v_user_id is null or not exists (
    select 1 from public.profiles as me where me.id = v_user_id and me.active is true
  ) then
    raise exception 'Active signed-in account required';
  end if;
  if p_recipient_id is null or p_recipient_id = v_user_id then
    raise exception 'Choose another account';
  end if;
  if char_length(v_body) < 1 or char_length(v_body) > 4000 then
    raise exception 'Message must be between 1 and 4000 characters';
  end if;
  if not exists (
    select 1 from public.profiles as recipient
    where recipient.id = p_recipient_id and recipient.active is true
  ) then
    raise exception 'Recipient account is not active';
  end if;

  insert into public.app_messages(sender_id, recipient_id, body)
  values (v_user_id, p_recipient_id, v_body)
  returning * into v_message;
  return to_jsonb(v_message);
end;
$$;

create or replace function public.mark_app_message_read(p_message_id uuid)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_updated_id uuid;
begin
  if v_user_id is null then
    raise exception 'Sign in required';
  end if;
  update public.app_messages as m
  set read_at = now()
  where m.id = p_message_id and m.recipient_id = v_user_id and m.read_at is null
  returning m.id into v_updated_id;
  return v_updated_id is not null;
end;
$$;

revoke all on function public.get_message_contacts() from public, anon;
revoke all on function public.send_app_message(uuid, text) from public, anon;
revoke all on function public.mark_app_message_read(uuid) from public, anon;
grant execute on function public.get_message_contacts() to authenticated;
grant execute on function public.send_app_message(uuid, text) to authenticated;
grant execute on function public.mark_app_message_read(uuid) to authenticated;
