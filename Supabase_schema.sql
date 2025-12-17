-- =============================================================================
-- AIme Android 后端初始化脚本 (v5.2 - 修复同步与密码哈希)
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
    password text NOT NULL, -- 存储哈希后的密码
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
    client_uuid uuid NOT NULL DEFAULT gen_random_uuid(),
    title text NOT NULL,
    last_message text,
    last_message_time timestamp with time zone,
    message_count int DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now(),
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone
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
    model_display_name text,
    created_at timestamp with time zone DEFAULT now(),
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone
);

-- 3.5 模型分组表
CREATE TABLE public.model_groups (
    id text PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    name text NOT NULL,
    base_url text NOT NULL,
    api_key text NOT NULL,
    provider_url text,
    created_at bigint,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone
);

-- 3.6 模型表
CREATE TABLE public.models (
    id text PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES public.accounts(id) ON DELETE CASCADE,
    group_id text NOT NULL,
    name text NOT NULL,
    model_name text NOT NULL,
    remark text,
    created_at bigint,
    is_deleted boolean DEFAULT false,
    deleted_at timestamp with time zone
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
CREATE UNIQUE INDEX idx_conversations_user_client_uuid ON public.conversations(user_id, client_uuid);
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
  hashed_password text;
BEGIN
  -- 1. 查重
  SELECT id INTO existing_id FROM public.accounts WHERE email = p_email LIMIT 1;
  IF existing_id IS NOT NULL THEN
    RETURN jsonb_build_object('ok', false, 'message', '注册失败：该邮箱已被注册');
  END IF;

  -- 2. 密码哈希
  hashed_password := crypt(p_password, gen_salt('bf'));

  -- 3. 创建用户
  INSERT INTO public.accounts(email, password, security_question, security_answer)
  VALUES (p_email, hashed_password, p_question, p_answer)
  RETURNING id INTO new_uid;

  -- 4. 创建 Token
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
  
  -- 验证密码 (兼容明文和哈希)
  -- 如果 v_stored_pass 不是哈希格式（旧数据），crypt 会报错或行为不一，但这里假设是新环境或已迁移
  -- 为了健壮性，这里仅支持哈希验证
  IF v_uid IS NOT NULL AND v_stored_pass = crypt(p_password, v_stored_pass) THEN
    v_token := encode(gen_random_bytes(32), 'hex');
    
    -- 限制每个用户最多同时存在 6 个活跃会话，避免无限增长
    DELETE FROM public.account_sessions 
    WHERE user_id = v_uid 
      AND id NOT IN (
        SELECT id FROM public.account_sessions 
        WHERE user_id = v_uid 
        ORDER BY created_at DESC 
        LIMIT 5
      );

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
    UPDATE public.accounts SET password = crypt(p_new_password, gen_salt('bf')), updated_at = now() WHERE id = v_uid;
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
  v_stored_pass text;
BEGIN
  SELECT id, password INTO v_uid, v_stored_pass FROM public.accounts WHERE email = p_email LIMIT 1;
  
  IF v_uid IS NOT NULL AND v_stored_pass = crypt(p_password, v_stored_pass) THEN
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

-- 清理可能存在的同名重载，避免 PostgREST 选择歧义
DROP FUNCTION IF EXISTS public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean);
DROP FUNCTION IF EXISTS public.sync_upload_backup(text, jsonb, boolean, boolean, boolean, boolean, jsonb);

CREATE OR REPLACE FUNCTION public.sync_upload_backup_v1(
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
    INSERT INTO public.model_groups(user_id, id, name, base_url, api_key, provider_url, created_at, is_deleted, deleted_at)
    SELECT 
      uid, 
      (g->>'id'), 
      COALESCE(g->>'name', 'Unknown'), 
      COALESCE(g->>'baseUrl', ''), 
      COALESCE(g->>'apiKey', ''), 
      (g->>'providerUrl'), 
      COALESCE((g->>'createdAt')::bigint, 0),
      COALESCE((g->>'isDeleted')::boolean, false),
      CASE WHEN (g ? 'deletedAt') THEN to_timestamp((((g->>'deletedAt')::numeric)/1000)::double precision) ELSE NULL END
    FROM jsonb_array_elements(CASE WHEN jsonb_typeof(p_data->'modelGroups') = 'array' THEN p_data->'modelGroups' ELSE '[]'::jsonb END) AS g
    WHERE (g->>'id') IS NOT NULL
    ON CONFLICT (id) DO UPDATE SET
      user_id = EXCLUDED.user_id,
      name = EXCLUDED.name,
      base_url = EXCLUDED.base_url,
      api_key = EXCLUDED.api_key,
      provider_url = EXCLUDED.provider_url,
      created_at = EXCLUDED.created_at,
      is_deleted = EXCLUDED.is_deleted,
      deleted_at = EXCLUDED.deleted_at;

    INSERT INTO public.models(user_id, id, group_id, name, model_name, remark, created_at, is_deleted, deleted_at)
    SELECT 
      uid, 
      (m->>'id'), 
      (m->>'groupId'), 
      COALESCE(m->>'name', 'Unknown'), 
      COALESCE(m->>'modelName', ''), 
      (m->>'remark'), 
      COALESCE((m->>'createdAt')::bigint, 0),
      COALESCE((m->>'isDeleted')::boolean, false),
      CASE WHEN (m ? 'deletedAt') THEN to_timestamp((((m->>'deletedAt')::numeric)/1000)::double precision) ELSE NULL END
    FROM jsonb_array_elements(CASE WHEN jsonb_typeof(p_data->'models') = 'array' THEN p_data->'models' ELSE '[]'::jsonb END) AS m
    WHERE (m->>'id') IS NOT NULL
    ON CONFLICT (id) DO UPDATE SET
      user_id = EXCLUDED.user_id,
      group_id = EXCLUDED.group_id,
      name = EXCLUDED.name,
      model_name = EXCLUDED.model_name,
      remark = EXCLUDED.remark,
      created_at = EXCLUDED.created_at,
      is_deleted = EXCLUDED.is_deleted,
      deleted_at = EXCLUDED.deleted_at;
  END IF;

  IF p_sync_selected_model THEN
    INSERT INTO public.user_settings(user_id, selected_model_id)
    VALUES (uid, (p_data->>'selectedModelId'))
    ON CONFLICT (user_id) DO UPDATE SET selected_model_id = EXCLUDED.selected_model_id, updated_at = now();
  END IF;

  IF p_sync_api_key THEN
    IF (p_data ? 'apiKeys') AND jsonb_typeof(p_data->'apiKeys') = 'array' THEN
      DELETE FROM public.api_keys WHERE user_id = uid;
      INSERT INTO public.api_keys(user_id, platform, api_key)
      SELECT uid, (k->>'platform'), (k->>'apiKey')
      FROM jsonb_array_elements(p_data->'apiKeys') AS k;
    END IF;
  END IF;

  IF p_sync_history THEN
    FOR conv_record IN SELECT * FROM jsonb_array_elements(CASE WHEN jsonb_typeof(p_data->'conversations') = 'array' THEN p_data->'conversations' ELSE '[]'::jsonb END) LOOP
        conv_id := NULL;
        
        -- 仅根据 client_uuid 查找现有会话，避免因标题相似而错误合并
        IF (conv_record.value ? 'uuid') AND (conv_record.value->>'uuid') IS NOT NULL THEN
            SELECT id INTO conv_id FROM public.conversations WHERE user_id = uid AND client_uuid = (conv_record.value->>'uuid')::uuid LIMIT 1;
        END IF;
        
        IF conv_id IS NOT NULL THEN
            UPDATE public.conversations SET
                client_uuid = COALESCE((conv_record.value->>'uuid')::uuid, client_uuid),
                title = (conv_record.value->>'title'),
                last_message = (conv_record.value->>'lastMessage'),
                last_message_time = to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision),
                message_count = ((conv_record.value->>'messageCount')::int),
                updated_at = now(),
                is_deleted = COALESCE((conv_record.value->>'isDeleted')::boolean, false),
                deleted_at = CASE WHEN (conv_record.value ? 'deletedAt') THEN to_timestamp((((conv_record.value->>'deletedAt')::numeric)/1000)::double precision) ELSE deleted_at END
            WHERE id = conv_id;
        ELSE
            INSERT INTO public.conversations(user_id, client_uuid, title, last_message, last_message_time, message_count, is_deleted, deleted_at)
            VALUES (uid, COALESCE((conv_record.value->>'uuid')::uuid, gen_random_uuid()), (conv_record.value->>'title'), (conv_record.value->>'lastMessage'),
                to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision), ((conv_record.value->>'messageCount')::int),
                COALESCE((conv_record.value->>'isDeleted')::boolean, false),
                CASE WHEN (conv_record.value ? 'deletedAt') THEN to_timestamp((((conv_record.value->>'deletedAt')::numeric)/1000)::double precision) ELSE NULL END)
            RETURNING id INTO conv_id;
        END IF;

        IF COALESCE((conv_record.value->>'isDeleted')::boolean, false) THEN
            CONTINUE;
        END IF;

        -- 插入消息 (避免重复)
        -- 使用更宽松的去重条件，防止浮点数精度问题
        INSERT INTO public.chat_messages(conversation_id, user_id, content, is_from_user, timestamp, is_error, model_display_name)
        SELECT 
          conv_id, 
          uid, 
          COALESCE(m->>'content', ''), 
          COALESCE((m->>'isFromUser')::boolean, false),
          to_timestamp((((COALESCE(m->>'timestamp', '0'))::numeric)/1000)::double precision), 
          COALESCE((m->>'isError')::boolean, false),
          CASE WHEN (m ? 'modelDisplayName') THEN (m->>'modelDisplayName') ELSE NULL END
        FROM jsonb_array_elements(CASE WHEN jsonb_typeof(conv_record.value->'messages') = 'array' THEN conv_record.value->'messages' ELSE '[]'::jsonb END) AS m
        WHERE NOT EXISTS (
            SELECT 1 FROM public.chat_messages cm 
            WHERE cm.conversation_id = conv_id AND cm.user_id = uid 
            -- 比较时间戳 (误差 10ms) 和 内容
            AND abs(extract(epoch from cm.timestamp) - (((COALESCE(m->>'timestamp', '0'))::numeric)/1000)) < 0.01
            AND cm.content = COALESCE(m->>'content', '')
        );

        -- 更新已有消息的模型外显名称（如果之前为NULL且本次上传提供了值）
        UPDATE public.chat_messages cm
        SET model_display_name = (m->>'modelDisplayName')
        FROM jsonb_array_elements(CASE WHEN jsonb_typeof(conv_record.value->'messages') = 'array' THEN conv_record.value->'messages' ELSE '[]'::jsonb END) AS m
        WHERE cm.conversation_id = conv_id
          AND cm.user_id = uid
          AND cm.model_display_name IS NULL
          AND (m ? 'modelDisplayName')
          AND cm.content = COALESCE(m->>'content', '')
          AND cm.is_from_user = COALESCE((m->>'isFromUser')::boolean, false)
          AND abs(extract(epoch from cm.timestamp) - (((COALESCE(m->>'timestamp', '0'))::numeric)/1000)) < 0.01;
    END LOOP;
  END IF;

  -- B. 读取
  SELECT COALESCE(jsonb_agg(
       jsonb_build_object(
         'id', c.id, 'title', c.title, 'uuid', c.client_uuid,
         'lastMessage', c.last_message,
         'lastMessageTime', (extract(epoch from c.last_message_time) * 1000)::bigint,
         'messageCount', c.message_count,
         'messages', (
            SELECT COALESCE(jsonb_agg(
              jsonb_build_object(
                'content', m.content,
                'isFromUser', m.is_from_user,
                'timestamp', (extract(epoch from m.timestamp) * 1000)::bigint,
                'isError', m.is_error,
                'modelDisplayName', m.model_display_name
              ) ORDER BY m.timestamp ASC
            ), '[]'::jsonb) FROM public.chat_messages m WHERE m.conversation_id = c.id AND COALESCE(m.is_deleted, false) = false
        )
       ) ORDER BY c.last_message_time DESC
  ), '[]'::jsonb) INTO res_conversations FROM public.conversations c WHERE c.user_id = uid AND COALESCE(c.is_deleted, false) = false;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'id', id, 'name', name, 'baseUrl', base_url, 'apiKey', api_key, 'providerUrl', provider_url, 'createdAt', created_at,
      'isDeleted', is_deleted, 'deletedAt', CASE WHEN deleted_at IS NOT NULL THEN (extract(epoch from deleted_at) * 1000)::bigint ELSE NULL END
  )), '[]'::jsonb) INTO res_model_groups FROM public.model_groups WHERE user_id = uid AND COALESCE(is_deleted, false) = false;

  SELECT COALESCE(jsonb_agg(jsonb_build_object(
      'id', id, 'groupId', group_id, 'name', name, 'modelName', model_name, 'remark', remark, 'createdAt', created_at,
      'isDeleted', is_deleted, 'deletedAt', CASE WHEN deleted_at IS NOT NULL THEN (extract(epoch from deleted_at) * 1000)::bigint ELSE NULL END
  )), '[]'::jsonb) INTO res_models FROM public.models WHERE user_id = uid AND COALESCE(is_deleted, false) = false;

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

CREATE OR REPLACE FUNCTION public.sync_download_backup_v1(p_token text)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
BEGIN
  RETURN public.sync_upload_backup_v1(p_token, '{}'::jsonb, false, false, false, false);
END;
$$;

-- =============================================================================
-- 8. 授权 (Grant Executions)
-- =============================================================================

-- 设置所有者 (Owner)
ALTER FUNCTION public.accounts_register(text, text, text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_login(text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_get_security_question(text) OWNER TO postgres;
ALTER FUNCTION public.accounts_reset_password_with_answer(text, text, text) OWNER TO postgres;
ALTER FUNCTION public.accounts_update_security_question(text, text, text, text) OWNER TO postgres;
ALTER FUNCTION public.sync_upload_backup_v1(text, jsonb, boolean, boolean, boolean, boolean) OWNER TO postgres;
ALTER FUNCTION public.sync_download_backup_v1(text) OWNER TO postgres;

-- 授权执行 (Grant Execute)
GRANT EXECUTE ON FUNCTION public.accounts_register(text, text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_login(text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_get_security_question(text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password_with_answer(text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_update_security_question(text, text, text, text) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.sync_upload_backup_v1(text, jsonb, boolean, boolean, boolean, boolean) TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.sync_download_backup_v1(text) TO anon, authenticated, service_role;
