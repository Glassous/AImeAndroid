-- =============================================================================
-- 1. 表结构定义 (Schema Definitions)
-- =============================================================================

-- 1.1 用户会话表 (Account Sessions)
-- 用于自定义 Auth 系统，管理用户的登录 Token
CREATE TABLE IF NOT EXISTS public.account_sessions (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL, -- 关联 Supabase Auth.users.id
    email text,
    token text NOT NULL UNIQUE,
    created_at timestamp with time zone DEFAULT now(),
    expires_at timestamp with time zone
);

-- 1.2 对话表 (Conversations)
-- 存储对话的元数据（标题、最后消息等）
CREATE TABLE IF NOT EXISTS public.conversations (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL,
    title text NOT NULL,
    last_message text,
    last_message_time timestamp with time zone,
    message_count int DEFAULT 0,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- 1.3 消息表 (Chat Messages)
-- 存储具体的聊天内容，级联关联到对话表
CREATE TABLE IF NOT EXISTS public.chat_messages (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    conversation_id uuid NOT NULL REFERENCES public.conversations(id) ON DELETE CASCADE,
    user_id uuid NOT NULL,
    content text,
    is_from_user boolean DEFAULT false,
    timestamp timestamp with time zone, -- 消息产生的时间戳
    is_error boolean DEFAULT false,
    created_at timestamp with time zone DEFAULT now()
);

-- 1.4 模型配置表 (Model Configs)
-- 存储用户自定义的模型参数
CREATE TABLE IF NOT EXISTS public.model_configs (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL,
    model_id text NOT NULL,   -- 客户端生成的唯一ID
    group_name text NOT NULL, -- 厂商分组，如 "OpenAI", "DeepSeek"
    model_name text NOT NULL, -- 具体模型名
    config jsonb,             -- 存储温度、MaxToken等详细配置
    created_at timestamp with time zone DEFAULT now()
);

-- 1.5 用户设置表 (User Settings)
-- 存储用户的全局偏好设置（如当前选中的模型）
CREATE TABLE IF NOT EXISTS public.user_settings (
    user_id uuid PRIMARY KEY, -- 每个用户只有一条记录
    selected_model_group text,
    selected_model_name text,
    updated_at timestamp with time zone DEFAULT now()
);

-- 1.6 API Keys 表 (API Keys)
-- 存储用户配置的第三方 API Key
CREATE TABLE IF NOT EXISTS public.api_keys (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id uuid NOT NULL,
    platform text NOT NULL,
    api_key text NOT NULL,
    created_at timestamp with time zone DEFAULT now()
);

-- =============================================================================
-- 2. 索引优化 (Index Optimization)
-- =============================================================================
-- 为高频查询字段创建索引，显著提升同步速度

-- 加速 Token 验证
CREATE INDEX IF NOT EXISTS idx_sessions_token ON public.account_sessions(token);

-- 加速对话列表查询及同步匹配
CREATE INDEX IF NOT EXISTS idx_conversations_user ON public.conversations(user_id);
CREATE INDEX IF NOT EXISTS idx_conversations_lookup ON public.conversations(user_id, title);

-- 加速消息查询及去重匹配
CREATE INDEX IF NOT EXISTS idx_messages_conv ON public.chat_messages(conversation_id);
CREATE INDEX IF NOT EXISTS idx_messages_lookup ON public.chat_messages(conversation_id, timestamp);

-- 加速配置类查询
CREATE INDEX IF NOT EXISTS idx_model_configs_user ON public.model_configs(user_id);
CREATE INDEX IF NOT EXISTS idx_api_keys_user ON public.api_keys(user_id);

-- =============================================================================
-- 3. 核心同步函数 (Core Sync Function)
-- =============================================================================
-- 函数名：sync_upload_backup
-- 功能：实现客户端与服务端的双向增量同步
-- 逻辑：
-- 1. 验证 Token 有效性
-- 2. 接收客户端数据 -> 执行合并 (Upsert: 存在则更新，不存在则插入)
-- 3. 查询数据库最新全量数据 -> 打包返回给客户端
-- =============================================================================

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
SECURITY DEFINER -- 绕过 RLS 限制，确保函数有足够权限读写数据
SET search_path = public
AS $$
DECLARE
  uid uuid;
  sel_group_name text;
  sel_model_name text;
  conv_record record;
  conv_id uuid;
  msg_record record;
  
  -- 返回结果容器
  res_conversations jsonb := '[]'::jsonb;
  res_model_configs jsonb := '[]'::jsonb;
  res_selected_model jsonb := null;
  res_api_keys jsonb := '[]'::jsonb;
BEGIN
  -- 1. 验证用户 Token
  SELECT user_id INTO uid FROM public.account_sessions WHERE token = p_token;
  IF uid IS NULL THEN
    RETURN jsonb_build_object('ok', false, 'message', '无效会话或Token过期');
  END IF;

  -- ==========================================
  -- A. 执行写入/更新逻辑 (Upload / Upsert)
  -- ==========================================

  -- A.1 同步模型配置 (策略：全量覆盖该用户的配置)
  IF p_sync_model_config THEN
    DELETE FROM public.model_configs WHERE user_id = uid;
    INSERT INTO public.model_configs(user_id, model_id, group_name, model_name, config)
    SELECT uid, (c->>'id')::text, (c->>'groupName')::text, (c->>'modelName')::text, (c->'config')
    FROM jsonb_array_elements(p_data->'modelConfigs') AS c;
  END IF;

  -- A.2 同步选中模型 (策略：Upsert)
  IF p_sync_selected_model THEN
    sel_group_name := (p_data->'selectedModel'->>'group')::text;
    sel_model_name := (p_data->'selectedModel'->>'model')::text;
    
    INSERT INTO public.user_settings(user_id, selected_model_group, selected_model_name)
    VALUES (uid, sel_group_name, sel_model_name)
    ON CONFLICT (user_id) DO UPDATE SET 
      selected_model_group = EXCLUDED.selected_model_group,
      selected_model_name = EXCLUDED.selected_model_name,
      updated_at = now();
  END IF;

  -- A.3 同步 API Keys (策略：全量覆盖)
  IF p_sync_api_key THEN
    DELETE FROM public.api_keys WHERE user_id = uid;
    INSERT INTO public.api_keys(user_id, platform, api_key)
    SELECT uid, (k->>'platform')::text, (k->>'apiKey')::text
    FROM jsonb_array_elements(p_data->'apiKeys') AS k;
  END IF;

  -- A.4 同步聊天记录 (核心合并逻辑)
  IF p_sync_history THEN
    FOR conv_record IN SELECT * FROM jsonb_array_elements(p_data->'conversations') LOOP
        -- 1. 查找或创建对话 (基于 Title)
        conv_id := NULL;
        SELECT id INTO conv_id FROM public.conversations WHERE user_id = uid AND title = (conv_record.value->>'title')::text LIMIT 1;

        IF conv_id IS NOT NULL THEN
            -- 对话存在：更新最后消息状态
            UPDATE public.conversations SET
                last_message = (conv_record.value->>'lastMessage')::text,
                last_message_time = to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision),
                message_count = ((conv_record.value->>'messageCount')::int),
                updated_at = now()
            WHERE id = conv_id;
        ELSE
            -- 对话不存在：插入
            INSERT INTO public.conversations(user_id, title, last_message, last_message_time, message_count)
            VALUES (
                uid, 
                (conv_record.value->>'title')::text, 
                (conv_record.value->>'lastMessage')::text,
                to_timestamp((((conv_record.value->>'lastMessageTime')::numeric)/1000)::double precision), 
                ((conv_record.value->>'messageCount')::int)
            )
            RETURNING id INTO conv_id;
        END IF;

        -- 2. 插入或更新消息 (基于 Timestamp)
        FOR msg_record IN SELECT * FROM jsonb_array_elements(conv_record.value->'messages') LOOP
            -- 尝试更新 (处理内容编辑的情况，容错 2ms)
            UPDATE public.chat_messages SET
                content = (msg_record.value->>'content')::text,
                is_from_user = ((msg_record.value->>'isFromUser')::boolean),
                is_error = COALESCE((msg_record.value->>'isError')::boolean, false)
            WHERE conversation_id = conv_id 
              AND user_id = uid
              AND abs(extract(epoch from timestamp) - (((msg_record.value->>'timestamp')::numeric)/1000)) < 0.002;
            
            -- 如果未找到匹配消息，则插入
            IF NOT FOUND THEN
                INSERT INTO public.chat_messages(conversation_id, user_id, content, is_from_user, timestamp, is_error)
                VALUES (
                    conv_id, 
                    uid, 
                    (msg_record.value->>'content')::text, 
                    ((msg_record.value->>'isFromUser')::boolean),
                    to_timestamp((((msg_record.value->>'timestamp')::numeric)/1000)::double precision), 
                    COALESCE((msg_record.value->>'isError')::boolean, false)
                );
            END IF;
        END LOOP;
    END LOOP;
  END IF;

  -- ==========================================
  -- B. 执行读取/下载逻辑 (Fetch & Return)
  -- 这一步负责返回最新数据，供客户端合并
  -- ==========================================

  -- B.1 获取所有对话及消息
  SELECT COALESCE(jsonb_agg(
       jsonb_build_object(
         'id', c.id,
         'title', c.title,
         'lastMessage', c.last_message,
         'lastMessageTime', (extract(epoch from c.last_message_time) * 1000)::bigint,
         'messageCount', c.message_count,
         'messages', (
            SELECT COALESCE(jsonb_agg(
              jsonb_build_object(
                'content', m.content,
                'isFromUser', m.is_from_user,
                'timestamp', (extract(epoch from m.timestamp) * 1000)::bigint,
                'isError', m.is_error
              ) ORDER BY m.timestamp ASC
            ), '[]'::jsonb)
            FROM public.chat_messages m
            WHERE m.conversation_id = c.id
         )
       ) ORDER BY c.last_message_time DESC
  ), '[]'::jsonb) INTO res_conversations
  FROM public.conversations c
  WHERE c.user_id = uid;

  -- B.2 获取模型配置
  SELECT COALESCE(jsonb_agg(
    jsonb_build_object(
      'id', model_id, 'groupName', group_name, 'modelName', model_name, 'config', config
    )
  ), '[]'::jsonb) INTO res_model_configs
  FROM public.model_configs WHERE user_id = uid;

  -- B.3 获取选中模型
  SELECT jsonb_build_object(
    'group', selected_model_group, 'model', selected_model_name
  ) INTO res_selected_model
  FROM public.user_settings WHERE user_id = uid;

  -- B.4 获取 API Keys
  SELECT COALESCE(jsonb_agg(
    jsonb_build_object('platform', platform, 'apiKey', api_key)
  ), '[]'::jsonb) INTO res_api_keys
  FROM public.api_keys WHERE user_id = uid;

  -- 构造最终返回对象
  RETURN jsonb_build_object(
    'ok', true,
    'message', '同步成功',
    'conversations', res_conversations,
    'modelConfigs', res_model_configs,
    'selectedModel', res_selected_model,
    'apiKeys', res_api_keys
  );

EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('ok', false, 'message', '同步失败: ' || SQLERRM);
END;
$$;

-- =============================================================================
-- 4. 安全策略配置 (RLS & Permissions)
-- =============================================================================

-- 开启行级安全策略 (RLS)
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.model_configs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.api_keys ENABLE ROW LEVEL SECURITY;

-- 授予函数执行权限
GRANT EXECUTE ON FUNCTION public.sync_upload_backup TO authenticated;
GRANT EXECUTE ON FUNCTION public.sync_upload_backup TO anon;
GRANT EXECUTE ON FUNCTION public.sync_upload_backup TO service_role;

-- 创建 RLS 策略 (示例：仅允许用户访问自己的数据)
-- 注意：sync_upload_backup 函数使用了 SECURITY DEFINER，会绕过这些检查，
-- 但直接通过 Supabase Client SDK 访问表时，这些策略会生效。

CREATE POLICY "Users can only access their own conversations" ON public.conversations
  FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "Users can only access their own messages" ON public.chat_messages
  FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "Users can only access their own configs" ON public.model_configs
  FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "Users can only access their own settings" ON public.user_settings
  FOR ALL USING (auth.uid() = user_id);

CREATE POLICY "Users can only access their own keys" ON public.api_keys
  FOR ALL USING (auth.uid() = user_id);