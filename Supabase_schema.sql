-- =============================================================================
-- AIme Android 后端初始化脚本 (v5.1 - 修复函数重载与权限签名问题)
-- =============================================================================

-- 1. 创建 extensions 架构并启用 pgcrypto
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA extensions;

-- 2. 清理旧表 (⚠️ 警告：这将清除所有数据！)
DROP TABLE IF EXISTS public.chat_messages CASCADE;
DROP TABLE IF EXISTS public.conversations CASCADE;
DROP TABLE IF EXISTS public.models CASCADE;
DROP TABLE IF EXISTS public.model_groups CASCADE;
DROP TABLE IF EXISTS public.user_settings CASCADE;
DROP TABLE IF EXISTS public.api_keys CASCADE;
DROP TABLE IF EXISTS public.account_sessions CASCADE;
DROP TABLE IF EXISTS public.accounts CASCADE;

-- =============================================================================
-- 3. 表结构定义
-- =============================================================================

-- 3.1 用户账户表
CREATE TABLE public.accounts (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    email text NOT NULL UNIQUE,
    password text NOT NULL,
    security_question text,
    security_answer text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- 3.2 用户会话表
CREATE TABLE public.account_sessions (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    email text,
    token text NOT NULL UNIQUE,
    created_at timestamp with time zone DEFAULT now(),
    expires_at timestamp with time zone
);

-- 3.3 对话表
CREATE TABLE public.conversations (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    title text NOT NULL,
    last_message text,
    last_message_time timestamp with time zone,
    message_count int DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- 3.4 消息表
CREATE TABLE public.chat_messages (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    content text,
    is_from_user boolean DEFAULT false,
    timestamp timestamp with time zone,
    is_error boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);

-- 3.5 模型分组表
CREATE TABLE public.model_groups (
    id text PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    name text NOT NULL,
    base_url text NOT NULL,
    api_key text NOT NULL,
    provider_url text,
    created_at bigint
);

-- 3.6 模型表
CREATE TABLE public.models (
    id text PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    group_id text NOT NULL,
    name text NOT NULL,
    model_name text NOT NULL,
    remark text,
    created_at bigint
);

-- 3.7 用户设置表
CREATE TABLE public.user_settings (
    user_id uuid PRIMARY KEY REFERENCES public.accounts(id) ON DELETE CASCADE,
    selected_model_id text,
    updated_at timestamp with time zone DEFAULT now()
);

-- 3.8 API Keys 表
CREATE TABLE public.api_keys (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    platform text NOT NULL,
    api_key text NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);

-- =============================================================================
-- 4. 索引优化
-- =============================================================================

CREATE INDEX idx_accounts_email ON public.accounts(email);
CREATE INDEX idx_sessions_token ON public.account_sessions(token);
CREATE INDEX idx_conversations_user ON public.conversations(user_id);
CREATE INDEX idx_messages_conv ON public.chat_messages(conversation_id);
CREATE INDEX idx_model_groups_user ON public.model_groups(user_id);
CREATE INDEX idx_models_user ON public.models(user_id);
CREATE INDEX idx_user_settings_uid ON public.user_settings(user_id);

-- =============================================================================
-- 5. 权限与 RLS
-- =============================================================================

ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.model_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.models ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.api_keys ENABLE ROW LEVEL SECURITY;

GRANT ALL ON ALL TABLES IN SCHEMA public TO service_role;
GRANT ALL ON ALL TABLES IN SCHEMA public TO postgres;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO service_role;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO postgres;

-- =============================================================================
-- 6. 核心业务函数 (Accounts & Auth)
-- =============================================================================

-- 6.1 注册函数
-- 清理旧函数以防止重载冲突
DROP FUNCTION IF EXISTS public.accounts_register(text, text, text, text);

CREATE OR REPLACE FUNCTION public.accounts_register(
    p_email text, 
    p_password text,
    p_question text DEFAULT '',
    p_answer text DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  new_uid uuid;
  new_token text;
  existing_id uuid;
BEGIN
  -- 1. 查重
  SELECT id INTO existing_id FROM public.accounts WHERE email = p_email LIMIT 1;
  IF existing_id IS NOT NULL THEN
    RETURN jsonb_build_object('ok', false, 'message', '注册失败：该邮箱已被注册');
  END IF;

  -- 2. 创建用户
  INSERT INTO public.accounts(email, password, security_question, security_answer)
  VALUES (p_email, p_password, p_question, p_answer)
  RETURNING id INTO new_uid;

  -- 3. 创建 Token
  new_token := encode(gen_random_bytes(32), 'hex');
  
  INSERT INTO public.account_sessions(user_id, email, token)
  VALUES (new_uid, p_email, new_token);
  
  RETURN jsonb_build_object(
    'ok', true, 
    'message', '注册成功',
    'user_id', new_uid,
    'email', p_email,
    'session_token', new_token
  );
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('ok', false, 'message', '注册异常: ' || SQLERRM);
END;
$$;

-- 6.2 登录函数
DROP FUNCTION IF EXISTS public.accounts_login(text, text);

CREATE OR REPLACE FUNCTION public.accounts_login(p_email text, p_password text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_uid uuid;
  v_stored_pass text;
  v_token text;
BEGIN
  SELECT id, password INTO v_uid, v_stored_pass 
  FROM public.accounts WHERE email = p_email LIMIT 1;
  
  IF v_uid IS NOT NULL AND v_stored_pass = p_password THEN
    v_token := encode(gen_random_bytes(32), 'hex');
    
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    INSERT INTO public.account_sessions(user_id, email, token)
    VALUES (v_uid, p_email, v_token);
    
    RETURN jsonb_build_object(
        'ok', true, 'message', '登录成功', 'user_id', v_uid, 'email', p_email, 'session_token', v_token
    );
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '登录失败：账号或密码错误');
  END IF;
END;
$$;

-- 6.3 获取密保
DROP FUNCTION IF EXISTS public.accounts_get_security_question(text);

CREATE OR REPLACE FUNCTION public.accounts_get_security_question(p_email text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_question text;
BEGIN
  SELECT security_question INTO v_question FROM public.accounts WHERE email = p_email LIMIT 1;
  IF v_question IS NOT NULL AND v_question != '' THEN
    RETURN jsonb_build_object('ok', true, 'question', v_question);
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '未找到该用户的安全问题');
  END IF;
END;
$$;

-- 6.4 重置密码
DROP FUNCTION IF EXISTS public.accounts_reset_password_with_answer(text, text, text);

CREATE OR REPLACE FUNCTION public.accounts_reset_password_with_answer(
    p_email text, p_answer text, p_new_password text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_uid uuid;
  v_real_answer text;
BEGIN
  SELECT id, security_answer INTO v_uid, v_real_answer FROM public.accounts WHERE email = p_email LIMIT 1;
  
  IF v_uid IS NOT NULL AND v_real_answer = p_answer THEN
    UPDATE public.accounts SET password = p_new_password, updated_at = now() WHERE id = v_uid;
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    RETURN jsonb_build_object('ok', true, 'message', '密码重置成功，请重新登录');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '验证失败：答案错误');
  END IF;
END;
$$;

-- 6.5 更新密保
DROP FUNCTION IF EXISTS public.accounts_update_security_question(text, text, text, text);

CREATE OR REPLACE FUNCTION public.accounts_update_security_question(
    p_email text, p_password text, p_new_question text, p_new_answer text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_uid uuid;
BEGIN
  SELECT id INTO v_uid FROM public.accounts WHERE email = p_email AND password = p_password LIMIT 1;
  IF v_uid IS NOT NULL THEN
    UPDATE public.accounts SET security_question = p_new_question, security_answer = p_new_answer, updated_at = now() WHERE id = v_uid;
    RETURN jsonb_build_object('ok', true, 'message', '安全问题已更新');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '验证失败：密码错误');
  END IF;
END;
$$;

-- =============================================================================
-- 7. 核心同步函数 (Sync)
-- =============================================================================

-- 7.1 上传备份函数
-- 显式清理，防止重载错误
DROP FUNCTION IF EXISTS public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean);

CREATE OR REPLACE FUNCTION public.sync_upload_backup(
    p_token text, 
    p_data jsonb, 
    p_sync_history boolean, 
    p_sync_model_config boolean, 
    p_sync_selected_model boolean, 
    p_sync_api_key boolean
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  uid uuid;
  res_conversations jsonb := '[]'::jsonb;
  res_model_groups jsonb := '[]'::jsonb;
  res_models jsonb := '[]'::jsonb;
  res_selected_model_id text := null;
  res_api_keys jsonb := '[]'::jsonb;
  conv_record record;
  conv_id uuid;
BEGIN
  -- 1. 验证 Token
  SELECT user_id INTO uid FROM public.account_sessions WHERE token = p_token;
  IF uid IS NULL THEN
    RETURN jsonb_build_object('ok', false, 'message', '无效会话或Token过期');
  END IF;

  -- A. 写入
  IF p_sync_model_config THEN
    DELETE FROM public.model_groups WHERE user_id = uid;
    INSERT INTO public.model_groups(user_id, id, name, base_url, api_key, provider_url, created_at)
    SELECT uid, (g->>'id'), (g->>'name'), (g->>'baseUrl'), (g->>'apiKey'), (g->>'providerUrl'), (g->>'createdAt')::bigint
    FROM jsonb_array_elements(COALESCE(p_data->'modelGroups', '[]'::jsonb)) AS g;

    DELETE FROM public.models WHERE user_id = uid;
    INSERT INTO public.models(user_id, id, group_id, name, model_name, remark, created_at)
    SELECT uid, (m->>'id'), (m->>'groupId'), (m->>'name'), (m->>'modelName'), (m->>'remark'), (m->>'createdAt')::bigint
    FROM jsonb_array_elements(COALESCE(p_data->'models', '[]'::jsonb)) AS m;
  END IF;

  IF p_sync_selected_model THEN
    INSERT INTO public.user_settings(user_id, selected_model_id)
    VALUES (uid, (p_data->>'selectedModelId'))
    ON CONFLICT (user_id) DO UPDATE SET selected_model_id = EXCLUDED.selected_model_id, updated_at = now();
  END IF;

  IF p_sync_api_key THEN
    DELETE FROM public.api_keys WHERE user_id = uid;
    INSERT INTO public.api_keys(user_id, platform, api_key)
    SELECT uid, (k->>'platform'), (k->>'apiKey')
    FROM jsonb_array_elements(COALESCE(p_data->'apiKeys', '[]'::jsonb)) AS k;
  END IF;

  IF p_sync_history THEN
    FOR conv_record IN SELECT * FROM jsonb_array_elements(COALESCE(p_data->'conversations', '[]'::jsonb)) LOOP
        conv_id := NULL;
        SELECT id INTO conv_id FROM public.conversations WHERE user_id = uid AND title = (conv_record.value->>'title')::text LIMIT 1;
        
        IF conv_id IS NOT NULL THEN
            UPDATE public.conversations SET
                last_message = (conv_record.value->>'lastMessage'),
                last_message_time = to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision),
                message_count = ((conv_record.value->>'messageCount')::int),
                updated_at = now()
            WHERE id = conv_id;
        ELSE
            INSERT INTO public.conversations(user_id, title, last_message, last_message_time, message_count)
            VALUES (uid, (conv_record.value->>'title'), (conv_record.value->>'lastMessage'),
                to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision), ((conv_record.value->>'messageCount')::int))
            RETURNING id INTO conv_id;
        END IF;

        INSERT INTO public.chat_messages(conversation_id, user_id, content, is_from_user, timestamp, is_error)
        SELECT conv_id, uid, (m->>'content'), ((m->>'isFromUser')::boolean),
               to_timestamp((((m->>'timestamp')::numeric)/1000)::double precision), COALESCE((m->>'isError')::boolean, false)
        FROM jsonb_array_elements(conv_record.value->'messages') AS m
        WHERE NOT EXISTS (
            SELECT 1 FROM public.chat_messages cm 
            WHERE cm.conversation_id = conv_id AND cm.user_id = uid 
            AND abs(extract(epoch from cm.timestamp) - (((m->>'timestamp')::numeric)/1000)) < 0.002
        );
    END LOOP;
  END IF;

  -- B. 读取
  SELECT COALESCE(jsonb_agg(
       jsonb_build_object(
         'id', c.id, 'title', c.title, 'lastMessage', c.last_message,
         'lastMessageTime', (extract(epoch from c.last_message_time) * 1000)::bigint,
         'messageCount', c.message_count,
         'messages', (
           SELECT COALESCE(jsonb_agg(
             jsonb_build_object('content', m.content, 'isFromUser', m.is_from_user,
               'timestamp', (extract(epoch from m.timestamp) * 1000)::bigint, 'isError', m.is_error) ORDER BY m.timestamp ASC
           ), '[]'::jsonb) FROM public.chat_messages m WHERE m.conversation_id = c.id
         )
       ) ORDER BY c.last_message_time DESC
  ), '[]'::jsonb) INTO res_conversations FROM public.conversations c WHERE c.user_id = uid;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'id', id, 'name', name, 'baseUrl', base_url, 'apiKey', api_key, 'providerUrl', provider_url, 'createdAt', created_at
  )), '[]'::jsonb) INTO res_model_groups FROM public.model_groups WHERE user_id = uid;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'id', id, 'groupId', group_id, 'name', name, 'modelName', model_name, 'remark', remark, 'createdAt', created_at
  )), '[]'::jsonb) INTO res_models FROM public.models WHERE user_id = uid;

  SELECT selected_model_id INTO res_selected_model_id FROM public.user_settings WHERE user_id = uid;

  SELECT COALESCE(jsonb_agg(jsonb_build_object('platform', platform, 'apiKey', api_key)), '[]'::jsonb) INTO res_api_keys FROM public.api_keys WHERE user_id = uid;

  RETURN jsonb_build_object(
    'ok', true, 'message', '同步成功',
    'conversations', res_conversations, 'modelGroups', res_model_groups,
    'models', res_models, 'selectedModelId', res_selected_model_id, 'apiKeys', res_api_keys
  );
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('ok', false, 'message', '同步失败: ' || SQLERRM);
END;
$$;

-- 7.2 下载备份 (辅助函数)
DROP FUNCTION IF EXISTS public.sync_download_backup(text);

CREATE OR REPLACE FUNCTION public.sync_download_backup(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
BEGIN
  RETURN public.sync_upload_backup(p_token, '{}'::jsonb, false, false, false, false);
END;
$$;

-- =============================================================================
-- 8. 授权 (Grant Executions)
-- =============================================================================
-- 关键修改：在 GRANT 语句中必须指定参数列表，以消除函数重载带来的歧义

-- 设置所有者 (Owner)
ALTER FUNCTION public.accounts_register(text, text, text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_login(text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_get_security_question(text) OWNER TO postgres;
ALTER FUNCTION public.accounts_reset_password_with_answer(text, text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_update_security_question(text, text, text, text) OWNER TO postgres;
ALTER FUNCTION public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean) OWNER TO postgres;
ALTER FUNCTION public.sync_download_backup(text) OWNER TO postgres;

-- 授权执行 (Grant Execute)
GRANT EXECUTE ON FUNCTION public.accounts_register(text, text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_login(text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_get_security_question(text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password_with_answer(text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_update_security_question(text, text, text, text) TO anon, authenticated, service_role;
-- 下面这一行就是之前报错的地方，现在加上了参数列表：
GRANT EXECUTE ON FUNCTION public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.sync_download_backup(text) TO anon, authenticated, service_role;