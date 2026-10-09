-- Secure self-service profile update. Users may change only their own name and MPO code.
-- The function intentionally does not permit changing email, role, or active status.
create or replace function public.update_own_profile(
  p_full_name text default null,
  p_mpo_code text default null
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  v_user_id uuid := auth.uid();
  v_profile public.profiles%rowtype;
begin
  if v_user_id is null then
    raise exception 'Authentication required';
  end if;

  if p_full_name is not null and length(trim(p_full_name)) = 0 then
    raise exception 'Name cannot be empty';
  end if;
  if p_full_name is not null and length(trim(p_full_name)) > 100 then
    raise exception 'Name is too long';
  end if;
  if p_mpo_code is not null and length(trim(p_mpo_code)) = 0 then
    raise exception 'MPO Code cannot be empty';
  end if;
  if p_mpo_code is not null and length(trim(p_mpo_code)) > 40 then
    raise exception 'MPO Code is too long';
  end if;

  update public.profiles as p
  set full_name = coalesce(nullif(trim(p_full_name), ''), p.full_name),
      mpo_code = coalesce(nullif(trim(p_mpo_code), ''), p.mpo_code)
  where p.id = v_user_id
  returning p.* into v_profile;

  if not found then
    raise exception 'Profile not found';
  end if;

  return jsonb_build_object(
    'id', v_profile.id,
    'email', v_profile.email,
    'full_name', v_profile.full_name,
    'mpo_code', v_profile.mpo_code,
    'role', v_profile.role,
    'active', v_profile.active
  );
end;
$$;

revoke all on function public.update_own_profile(text, text) from public;
grant execute on function public.update_own_profile(text, text) to authenticated;
