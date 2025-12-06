begin;

create extension if not exists pgcrypto;

drop table if exists public.user_profiles cascade;
drop table if exists public.user_settings cascade;

create table if not exists public.model_groups (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.accounts(id) on delete cascade,
  name text not null,
  base_url text not null,
  api_key text not null,
  provider_url text,
  created_at timestamptz default now()
);

create unique index if not exists model_groups_user_name_unique on public.model_groups(user_id, name);

create table if not exists public.models (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.accounts(id) on delete cascade,
  group_id uuid not null references public.model_groups(id) on delete cascade,
  name text not null,
  model_name text not null,
  remark text,
  created_at timestamptz default now()
);

create index if not exists models_group_id_idx on public.models(group_id);
create unique index if not exists models_group_name_unique on public.models(group_id, name);

create table if not exists public.conversations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.accounts(id) on delete cascade,
  title text not null,
  last_message text not null default '',
  last_message_time timestamptz not null default now(),
  message_count int not null default 0,
  created_at timestamptz default now()
);

create index if not exists conversations_user_time_idx on public.conversations(user_id, last_message_time desc);

create table if not exists public.chat_messages (
  id bigserial primary key,
  user_id uuid not null references public.accounts(id) on delete cascade,
  conversation_id uuid not null references public.conversations(id) on delete cascade,
  content text not null,
  is_from_user boolean not null default false,
  timestamp timestamptz not null default now(),
  is_error boolean not null default false
);

create index if not exists chat_messages_conv_time_idx on public.chat_messages(conversation_id, timestamp);
create index if not exists chat_messages_user_conv_idx on public.chat_messages(user_id, conversation_id);

create table if not exists public.accounts (
  id uuid primary key default gen_random_uuid(),
  email text not null unique,
  password_hash text not null,
  created_at timestamptz default now()
);

create table if not exists public.account_sessions (
  token text primary key,
  user_id uuid not null references public.accounts(id) on delete cascade,
  created_at timestamptz default now(),
  expires_at timestamptz default (now() + interval '30 days')
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

-- removed per accounts migration

create or replace function public.update_conversation_stats(conv_id uuid)
returns void
language plpgsql
as $$
begin
  update public.conversations c
  set
    message_count = (select count(*) from public.chat_messages where conversation_id = conv_id),
    last_message = coalesce((select content from public.chat_messages where conversation_id = conv_id order by timestamp desc limit 1), c.last_message),
    last_message_time = coalesce((select timestamp from public.chat_messages where conversation_id = conv_id order by timestamp desc limit 1), c.last_message_time)
  where c.id = conv_id;
end;
$$;

create or replace function public.chat_messages_after_change()
returns trigger
language plpgsql
as $$
begin
  if (tg_op = 'INSERT') then
    perform public.update_conversation_stats(new.conversation_id);
  elsif (tg_op = 'UPDATE') then
    if new.conversation_id <> old.conversation_id then
      perform public.update_conversation_stats(old.conversation_id);
    end if;
    perform public.update_conversation_stats(new.conversation_id);
  elsif (tg_op = 'DELETE') then
    perform public.update_conversation_stats(old.conversation_id);
  end if;
  return null;
end;
$$;

drop trigger if exists chat_messages_aiud on public.chat_messages;
create trigger chat_messages_aiud
after insert or update or delete on public.chat_messages
for each row execute function public.chat_messages_after_change();

alter table public.model_groups drop constraint if exists model_groups_user_id_fkey;
alter table public.model_groups add constraint model_groups_user_id_fkey foreign key (user_id) references public.accounts(id) on delete cascade;

alter table public.models drop constraint if exists models_user_id_fkey;
alter table public.models add constraint models_user_id_fkey foreign key (user_id) references public.accounts(id) on delete cascade;
alter table public.models drop constraint if exists models_group_id_fkey;
alter table public.models add constraint models_group_id_fkey foreign key (group_id) references public.model_groups(id) on delete cascade;

alter table public.conversations drop constraint if exists conversations_user_id_fkey;
alter table public.conversations add constraint conversations_user_id_fkey foreign key (user_id) references public.accounts(id) on delete cascade;

alter table public.chat_messages drop constraint if exists chat_messages_user_id_fkey;
alter table public.chat_messages add constraint chat_messages_user_id_fkey foreign key (user_id) references public.accounts(id) on delete cascade;
alter table public.chat_messages drop constraint if exists chat_messages_conversation_id_fkey;
alter table public.chat_messages add constraint chat_messages_conversation_id_fkey foreign key (conversation_id) references public.conversations(id) on delete cascade;

alter table public.model_groups disable row level security;
alter table public.models disable row level security;
alter table public.conversations disable row level security;
alter table public.chat_messages disable row level security;
-- accounts are managed via RPC

-- RLS disabled for business tables; access controlled via RPC security definer using sessions

-- removed policies for user_profiles/user_settings

insert into storage.buckets (id, name, public) values ('public-assets', 'public-assets', true) on conflict (id) do nothing;
insert into storage.buckets (id, name, public) values ('user-content', 'user-content', false) on conflict (id) do nothing;

drop policy if exists storage_public_read on storage.objects;
create policy storage_public_read on storage.objects for select to public using (bucket_id = 'public-assets');

drop policy if exists storage_user_content_select on storage.objects;
create policy storage_user_content_select on storage.objects for select to authenticated using (bucket_id = 'user-content' and (name like (auth.uid()::text || '/%') or owner = auth.uid()));

drop policy if exists storage_user_content_insert on storage.objects;
create policy storage_user_content_insert on storage.objects for insert to authenticated with check (bucket_id = 'user-content' and (name like (auth.uid()::text || '/%')));

drop policy if exists storage_user_content_update on storage.objects;
create policy storage_user_content_update on storage.objects for update to authenticated using (bucket_id = 'user-content' and owner = auth.uid()) with check (bucket_id = 'user-content' and owner = auth.uid());

drop policy if exists storage_user_content_delete on storage.objects;
create policy storage_user_content_delete on storage.objects for delete to authenticated using (bucket_id = 'user-content' and owner = auth.uid());

-- Accounts RPC functions
drop function if exists public.accounts_register(text, text);
create or replace function public.accounts_register(p_email text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  uid uuid;
  tok text;
begin
  select id into uid from public.accounts where email = p_email;
  if uid is not null then
    return jsonb_build_object('ok', false, 'message', '邮箱已存在');
  end if;

  insert into public.accounts(email, password_hash)
  values (p_email, extensions.crypt(p_password, extensions.gen_salt('bf')))
  returning id into uid;

  tok := encode(gen_random_bytes(32), 'hex');
  insert into public.account_sessions(token, user_id) values (tok, uid);

  return jsonb_build_object('ok', true, 'user_id', uid, 'email', p_email, 'session_token', tok, 'message', '注册并登录成功');
end;
$$;
grant execute on function public.accounts_register(text, text) to anon, authenticated;

drop function if exists public.accounts_login(text, text);
create or replace function public.accounts_login(p_email text, p_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  uid uuid;
  ph text;
  tok text;
begin
  select id, password_hash into uid, ph from public.accounts where email = p_email;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '账户不存在');
  end if;
  if extensions.crypt(p_password, ph) <> ph then
    return jsonb_build_object('ok', false, 'message', '邮箱或密码错误');
  end if;
  tok := encode(gen_random_bytes(32), 'hex');
  insert into public.account_sessions(token, user_id) values (tok, uid);
  return jsonb_build_object('ok', true, 'user_id', uid, 'email', p_email, 'session_token', tok, 'message', '登录成功');
end;
$$;
grant execute on function public.accounts_login(text, text) to anon, authenticated;

drop function if exists public.accounts_recover(text);
create or replace function public.accounts_recover(p_email text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  exists bool;
begin
  select exists(select 1 from public.accounts where email = p_email) into exists;
  if not exists then
    return jsonb_build_object('ok', false, 'message', '邮箱未注册');
  end if;
  return jsonb_build_object('ok', true, 'message', '已发送重置邮件');
end;
$$;
grant execute on function public.accounts_recover(text) to anon, authenticated;

commit;
drop function if exists public.accounts_reset_password(text, text);
create or replace function public.accounts_reset_password(p_email text, p_new_password text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  affected int;
begin
  update public.accounts
  set password_hash = extensions.crypt(p_new_password, extensions.gen_salt('bf'))
  where email = p_email;
  get diagnostics affected = row_count;
  if affected = 0 then
    return jsonb_build_object('ok', false, 'message', '邮箱未注册');
  end if;
  return jsonb_build_object('ok', true, 'message', '密码已重置');
end;
$$;
grant execute on function public.accounts_reset_password(text, text) to anon, authenticated;
drop function if exists public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean);
create or replace function public.sync_upload_backup(p_token text, p_data jsonb, p_sync_history boolean, p_sync_model_config boolean, p_sync_selected_model boolean, p_sync_api_key boolean)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
  sel_group_name text;
  sel_model_name text;
begin
  select user_id into uid from public.account_sessions where token = p_token;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '无效会话');
  end if;
  if p_sync_model_config then
    insert into public.model_groups(user_id, name, base_url, api_key, provider_url)
    select uid, (g->>'name')::text, (g->>'baseUrl')::text,
           case when p_sync_api_key then (g->>'apiKey')::text else coalesce((select mg.api_key from public.model_groups mg where mg.user_id = uid and mg.name = (g->>'name')::text), (g->>'apiKey')::text) end,
           (g->>'providerUrl')::text
    from jsonb_array_elements(p_data->'modelGroups') as g
    on conflict (user_id, name) do update set
      base_url = excluded.base_url,
      provider_url = excluded.provider_url,
      api_key = excluded.api_key;

    insert into public.models(user_id, group_id, name, model_name, remark)
    select uid,
           (select mg.id from public.model_groups mg where mg.user_id = uid and mg.name = (select (gg->>'name')::text from jsonb_array_elements(p_data->'modelGroups') gg where gg->>'id' = m->>'groupId' limit 1)),
           (m->>'name')::text,
           (m->>'modelName')::text,
           (m->>'remark')::text
    from jsonb_array_elements(p_data->'models') as m
    where (select (gg->>'name')::text from jsonb_array_elements(p_data->'modelGroups') gg where gg->>'id' = m->>'groupId' limit 1) is not null
    on conflict (group_id, name) do update set model_name = excluded.model_name, remark = excluded.remark;
  end if;

  if p_sync_history then
    -- upsert conversations if not exists (by title + last_message_time)
    insert into public.conversations(user_id, title, last_message, last_message_time, message_count)
    select uid, (c->>'title')::text, (c->>'lastMessage')::text,
           to_timestamp((((c->>'lastMessageTime')::numeric)/1000)::double precision),
           ((c->>'messageCount')::int)
    from jsonb_array_elements(p_data->'conversations') as c
    where not exists (
      select 1 from public.conversations conv
      where conv.user_id = uid
        and conv.title = (c->>'title')::text
        and abs(extract(epoch from conv.last_message_time) - (((c->>'lastMessageTime')::numeric)/1000)) < 2
    );

    -- insert messages if not exists (by conv + content + timestamp)
    insert into public.chat_messages(conversation_id, user_id, content, is_from_user, timestamp, is_error)
    select conv.id, uid, (m->>'content')::text, ((m->>'isFromUser')::boolean),
           to_timestamp((((m->>'timestamp')::numeric)/1000)::double precision),
           coalesce((m->>'isError')::boolean, false)
    from jsonb_array_elements(p_data->'conversations') as c
    join public.conversations conv on conv.user_id = uid and conv.title = (c->>'title')::text
    join jsonb_array_elements(c->'messages') as m on true
    where not exists (
      select 1 from public.chat_messages cm
      where cm.conversation_id = conv.id
        and cm.user_id = uid
        and cm.content = (m->>'content')::text
        and abs(extract(epoch from cm.timestamp) - (((m->>'timestamp')::numeric)/1000)) < 2
    );
  end if;

  if p_sync_selected_model then
    select
      (m->>'name')::text as model_name,
      (select (gg->>'name')::text from jsonb_array_elements(p_data->'modelGroups') gg where gg->>'id' = m->>'groupId' limit 1) as group_name
    into sel_model_name, sel_group_name
    from jsonb_array_elements(p_data->'models') as m
    where m->>'id' = (p_data->>'selectedModelId')
    limit 1;

    if sel_model_name is not null and sel_group_name is not null then
      insert into public.user_settings(user_id, selected_group_name, selected_model_name, updated_at)
      values (uid, sel_group_name, sel_model_name, now())
      on conflict (user_id) do update set selected_group_name = excluded.selected_group_name, selected_model_name = excluded.selected_model_name, updated_at = now();
    end if;
  end if;
  return jsonb_build_object('ok', true, 'message', '上传成功');
end;
$$;
grant execute on function public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean) to anon, authenticated;

drop function if exists public.sync_download_backup(text);
create or replace function public.sync_download_backup(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
  result jsonb;
begin
  select user_id into uid from public.account_sessions where token = p_token;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '无效会话');
  end if;
  result := jsonb_build_object(
    'version', 1,
    'exportedAt', extract(epoch from now())*1000,
    'modelGroups', coalesce((select jsonb_agg(jsonb_build_object('id', id::text, 'name', name, 'baseUrl', base_url, 'apiKey', api_key, 'providerUrl', provider_url)) from public.model_groups where user_id = uid), '[]'::jsonb),
    'models', coalesce((select jsonb_agg(jsonb_build_object('id', m.id::text, 'groupId', g.id::text, 'name', m.name, 'modelName', m.model_name, 'remark', m.remark)) from public.models m join public.model_groups g on m.group_id = g.id where g.user_id = uid), '[]'::jsonb),
    'selectedModelId', coalesce((
      select m.id::text from public.models m
      join public.model_groups g on m.group_id = g.id
      where g.user_id = uid
        and g.name = (select selected_group_name from public.user_settings where user_id = uid)
        and m.name = (select selected_model_name from public.user_settings where user_id = uid)
      limit 1
    ), null),
    'conversations', coalesce((select jsonb_agg(jsonb_build_object('title', title, 'lastMessage', last_message, 'lastMessageTime', extract(epoch from last_message_time)*1000, 'messageCount', message_count, 'messages', (select jsonb_agg(jsonb_build_object('content', content, 'isFromUser', is_from_user, 'timestamp', extract(epoch from timestamp)*1000, 'isError', case when is_error then true else null end)) from public.chat_messages where conversation_id = conv.id))) from public.conversations conv where user_id = uid), '[]'::jsonb)
  );
  return result;
end;
$$;
grant execute on function public.sync_download_backup(text) to anon, authenticated;

drop function if exists public.sync_clear_all(text);
create or replace function public.sync_clear_all(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
begin
  select user_id into uid from public.account_sessions where token = p_token;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '无效会话');
  end if;
  delete from public.chat_messages where conversation_id in (select id from public.conversations where user_id = uid);
  delete from public.conversations where user_id = uid;
  delete from public.models where group_id in (select id from public.model_groups where user_id = uid);
  delete from public.model_groups where user_id = uid;
  return jsonb_build_object('ok', true, 'message', '已清空');
end;
$$;
grant execute on function public.sync_clear_all(text) to anon, authenticated;

drop function if exists public.sync_clear_history(text);
create or replace function public.sync_clear_history(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
begin
  select user_id into uid from public.account_sessions where token = p_token;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '无效会话');
  end if;
  delete from public.chat_messages where conversation_id in (select id from public.conversations where user_id = uid);
  delete from public.conversations where user_id = uid;
  return jsonb_build_object('ok', true, 'message', '已清空会话与消息');
end;
$$;
grant execute on function public.sync_clear_history(text) to anon, authenticated;

drop function if exists public.sync_clear_model_config(text);
create or replace function public.sync_clear_model_config(p_token text)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid;
begin
  select user_id into uid from public.account_sessions where token = p_token;
  if uid is null then
    return jsonb_build_object('ok', false, 'message', '无效会话');
  end if;
  delete from public.models where group_id in (select id from public.model_groups where user_id = uid);
  delete from public.model_groups where user_id = uid;
  return jsonb_build_object('ok', true, 'message', '已清空模型配置');
end;
$$;
grant execute on function public.sync_clear_model_config(text) to anon, authenticated;
create table if not exists public.user_settings (
  user_id uuid primary key references public.accounts(id) on delete cascade,
  selected_group_name text,
  selected_model_name text,
  updated_at timestamptz default now()
);

-- 1. 修改 accounts 表，增加安全问题相关字段
alter table public.accounts
add column if not exists security_question text,
add column if not exists security_answer_hash text;

-- 2. 更新注册函数，支持安全问题
drop function if exists public.accounts_register(text, text); -- 删除旧签名
drop function if exists public.accounts_register(text, text, text, text);

create or replace function public.accounts_register(
  p_email text,
  p_password text,
  p_question text,
  p_answer text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  uid uuid;
  tok text;
begin
  select id into uid from public.accounts where email = p_email;
  if uid is not null then
    return jsonb_build_object('ok', false, 'message', '邮箱已存在');
  end if;

  -- 插入数据，同时存储问题的答案哈希
  insert into public.accounts(email, password_hash, security_question, security_answer_hash)
  values (
    p_email,
    extensions.crypt(p_password, extensions.gen_salt('bf')),
    p_question,
    extensions.crypt(p_answer, extensions.gen_salt('bf'))
  )
  returning id into uid;

  tok := encode(gen_random_bytes(32), 'hex');
  insert into public.account_sessions(token, user_id) values (tok, uid);

  return jsonb_build_object('ok', true, 'user_id', uid, 'email', p_email, 'session_token', tok, 'message', '注册并登录成功');
end;
$$;
grant execute on function public.accounts_register(text, text, text, text) to anon, authenticated;

-- 3. 新增函数：根据邮箱获取安全问题
create or replace function public.accounts_get_security_question(p_email text)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  q text;
begin
  select security_question into q from public.accounts where email = p_email;

  if q is null then
    return jsonb_build_object('ok', false, 'message', '账户不存在或未设置安全问题');
  end if;

  return jsonb_build_object('ok', true, 'question', q);
end;
$$;
grant execute on function public.accounts_get_security_question(text) to anon, authenticated;

-- 4. 新增函数：验证安全问题并重置密码
create or replace function public.accounts_reset_password_with_answer(
  p_email text,
  p_answer text,
  p_new_password text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  rec record;
begin
  select id, security_answer_hash into rec from public.accounts where email = p_email;

  if rec.id is null then
    return jsonb_build_object('ok', false, 'message', '账户不存在');
  end if;

  if rec.security_answer_hash is null then
     return jsonb_build_object('ok', false, 'message', '该账户未设置安全问题，无法通过此方式找回');
  end if;

  -- 验证问题的答案
  if rec.security_answer_hash <> extensions.crypt(p_answer, rec.security_answer_hash) then
    return jsonb_build_object('ok', false, 'message', '安全问题回答错误');
  end if;

  -- 验证通过，重置密码
  update public.accounts
  set password_hash = extensions.crypt(p_new_password, extensions.gen_salt('bf'))
  where id = rec.id;

  return jsonb_build_object('ok', true, 'message', '密码已重置，请使用新密码登录');
end;
$$;
grant execute on function public.accounts_reset_password_with_answer(text, text, text) to anon, authenticated;

-- 5. 新增函数：验证密码并更新安全问题（用于老用户设置或修改问题）
create or replace function public.accounts_update_security_question(
  p_email text,
  p_password text,
  p_new_question text,
  p_new_answer text
)
returns jsonb
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  rec record;
begin
  select id, password_hash into rec from public.accounts where email = p_email;

  if rec.id is null then
    return jsonb_build_object('ok', false, 'message', '账户不存在');
  end if;

  -- 验证密码
  if rec.password_hash <> extensions.crypt(p_password, rec.password_hash) then
    return jsonb_build_object('ok', false, 'message', '密码错误，无法修改安全设置');
  end if;

  -- 更新问题和答案
  update public.accounts
  set security_question = p_new_question,
      security_answer_hash = extensions.crypt(p_new_answer, extensions.gen_salt('bf'))
  where id = rec.id;

  return jsonb_build_object('ok', true, 'message', '安全问题已设置成功');
end;
$$;
grant execute on function public.accounts_update_security_question(text, text, text, text) to anon, authenticated;
