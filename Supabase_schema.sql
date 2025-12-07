-- ==========================================
-- AIme - Supabase 完整数据库构建脚本
-- ==========================================

-- 1. 初始化扩展 (Extensions)
-- 启用 pgcrypto 用于密码哈希，uuid-ossp 用于生成 UUID
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA extensions;

-- 2. 创建表结构 (Tables)

-- 2.1 用户账户表 (accounts)
CREATE TABLE IF NOT EXISTS public.accounts (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL PRIMARY KEY,
    email text NOT NULL UNIQUE,
    password text NOT NULL, -- 存储加密后的哈希值 ($2a$...)
    security_question text DEFAULT ''::text,
    security_answer text DEFAULT ''::text,
    created_at timestamp with time zone DEFAULT now(),
    updated_at timestamp with time zone DEFAULT now()
);

-- 2.2 用户会话表 (account_sessions)
CREATE TABLE IF NOT EXISTS public.account_sessions (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL PRIMARY KEY,
    user_id uuid REFERENCES public.accounts(id) ON DELETE CASCADE,
    email text NOT NULL,
    token text NOT NULL, -- 简单的会话令牌
    created_at timestamp with time zone DEFAULT now()
);

-- 2.3 用户备份数据表 (user_backups) - 用于云同步
CREATE TABLE IF NOT EXISTS public.user_backups (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL PRIMARY KEY,
    user_id uuid REFERENCES public.accounts(id) ON DELETE CASCADE UNIQUE, -- 确保每个用户只有一条备份记录
    backup_data jsonb NOT NULL, -- 存储所有聊天记录、配置的 JSON 数据
    device_info text DEFAULT '', -- 记录最后上传的设备信息
    app_version text DEFAULT '', -- 记录 App 版本
    updated_at timestamp with time zone DEFAULT now()
);

-- 3. 数据迁移 (Migration)
-- [重要] 如果是现有数据库，将所有旧的明文密码转换为哈希值
-- 只有当密码不以 $2a$ 开头时才进行转换，防止重复哈希
UPDATE public.accounts 
SET password = extensions.crypt(password, extensions.gen_salt('bf'))
WHERE password NOT LIKE '$2a$%';

-- 4. 创建索引 (Indexes)
CREATE INDEX IF NOT EXISTS idx_accounts_email ON public.accounts(email);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON public.account_sessions(token);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON public.account_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_backups_user_id ON public.user_backups(user_id);

-- 5. 核心业务函数 (RPC Functions)

-- ----------------------------------------------------------------
-- 函数 1: 用户注册 (accounts_register)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accounts_register(
    p_email text, 
    p_password text,
    p_question text DEFAULT '',
    p_answer text DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER -- 使用定义者权限运行，绕过 RLS
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

  -- 2. 创建用户 (密码进行哈希加密)
  INSERT INTO public.accounts(email, password, security_question, security_answer)
  VALUES (p_email, crypt(p_password, gen_salt('bf')), p_question, p_answer)
  RETURNING id INTO new_uid;

  -- 3. 创建 Session Token
  new_token := encode(gen_random_bytes(32), 'hex');
  
  INSERT INTO public.account_sessions(user_id, email, token)
  VALUES (new_uid, p_email, new_token);
  
  -- 4. 初始化空的备份记录
  INSERT INTO public.user_backups(user_id, backup_data)
  VALUES (new_uid, '{}'::jsonb);
  
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

-- ----------------------------------------------------------------
-- 函数 2: 用户登录 (accounts_login)
-- ----------------------------------------------------------------
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
  
  -- 验证哈希密码：crypt(输入密码, 存储的哈希) 应该等于 存储的哈希
  IF v_uid IS NOT NULL AND v_stored_pass = crypt(p_password, v_stored_pass) THEN
    v_token := encode(gen_random_bytes(32), 'hex');
    
    -- 刷新 Token
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    INSERT INTO public.account_sessions(user_id, email, token)
    VALUES (v_uid, p_email, v_token);
    
    RETURN jsonb_build_object(
        'ok', true, 
        'message', '登录成功', 
        'user_id', v_uid, 
        'email', p_email, 
        'session_token', v_token
    );
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '登录失败：账号或密码错误');
  END IF;
END;
$$;

-- ----------------------------------------------------------------
-- 函数 3: 通过密保重置密码 (accounts_reset_password_with_answer)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accounts_reset_password_with_answer(
    p_email text, 
    p_answer text, 
    p_new_password text
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
    -- 更新密码为新的哈希值
    UPDATE public.accounts 
    SET password = crypt(p_new_password, gen_salt('bf')), 
        updated_at = now() 
    WHERE id = v_uid;
    
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    
    RETURN jsonb_build_object('ok', true, 'message', '密码重置成功，请重新登录');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '验证失败：答案错误');
  END IF;
END;
$$;

-- ----------------------------------------------------------------
-- 函数 4: 更新安全问题 (accounts_update_security_question)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accounts_update_security_question(
    p_email text, 
    p_password text, 
    p_new_question text, 
    p_new_answer text
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
  SELECT id, password INTO v_uid, v_stored_pass 
  FROM public.accounts WHERE email = p_email LIMIT 1;

  -- 验证当前密码
  IF v_uid IS NOT NULL AND v_stored_pass = crypt(p_password, v_stored_pass) THEN
    UPDATE public.accounts 
    SET security_question = p_new_question, 
        security_answer = p_new_answer, 
        updated_at = now() 
    WHERE id = v_uid;
    RETURN jsonb_build_object('ok', true, 'message', '安全问题已更新');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '验证失败：密码错误');
  END IF;
END;
$$;

-- ----------------------------------------------------------------
-- 函数 5: 直接重置密码 (accounts_reset_password)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accounts_reset_password(
    p_email text, 
    p_new_password text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_uid uuid;
BEGIN
  SELECT id INTO v_uid FROM public.accounts WHERE email = p_email LIMIT 1;
  
  IF v_uid IS NOT NULL THEN
    UPDATE public.accounts 
    SET password = crypt(p_new_password, gen_salt('bf')), 
        updated_at = now() 
    WHERE id = v_uid;
    
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    
    RETURN jsonb_build_object('ok', true, 'message', '密码已重置');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '用户不存在');
  END IF;
END;
$$;

-- ----------------------------------------------------------------
-- 函数 6: 同步 - 上传数据 (sync_upload_data)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.sync_upload_data(
    p_user_id uuid,
    p_backup_data jsonb,
    p_device_info text DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
BEGIN
  -- 如果存在则更新，不存在则插入 (Upsert)
  INSERT INTO public.user_backups (user_id, backup_data, device_info, updated_at)
  VALUES (p_user_id, p_backup_data, p_device_info, now())
  ON CONFLICT (user_id) 
  DO UPDATE SET 
    backup_data = EXCLUDED.backup_data,
    device_info = EXCLUDED.device_info,
    updated_at = now();
    
  RETURN jsonb_build_object('ok', true, 'message', '同步成功', 'timestamp', now());
EXCEPTION WHEN OTHERS THEN
  RETURN jsonb_build_object('ok', false, 'message', '同步失败: ' || SQLERRM);
END;
$$;

-- ----------------------------------------------------------------
-- 函数 7: 同步 - 下载数据 (sync_download_data)
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.sync_download_data(p_user_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_data jsonb;
  v_time timestamp with time zone;
BEGIN
  SELECT backup_data, updated_at INTO v_data, v_time 
  FROM public.user_backups WHERE user_id = p_user_id;
  
  IF v_data IS NOT NULL THEN
    -- 返回数据给客户端
    RETURN jsonb_build_object('ok', true, 'data', v_data, 'updated_at', v_time);
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '无备份数据');
  END IF;
END;
$$;

-- 6. 安全策略 (RLS & Grants)

-- 启用 RLS
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_backups ENABLE ROW LEVEL SECURITY;

-- 默认拒绝所有直接的表访问 (因为我们完全通过 RPC 函数来操作)
CREATE POLICY "Deny all public access to accounts" ON public.accounts FOR ALL USING (false);
CREATE POLICY "Deny all public access to sessions" ON public.account_sessions FOR ALL USING (false);
CREATE POLICY "Deny all public access to backups" ON public.user_backups FOR ALL USING (false);

-- 授予权限
-- 允许匿名(anon)和登录(authenticated)用户调用 RPC 函数
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;

-- 授予表的操作权限给 service_role (RPC 函数以 SECURITY DEFINER 运行时使用此权限)
GRANT ALL ON TABLE public.accounts TO service_role;
GRANT ALL ON TABLE public.account_sessions TO service_role;
GRANT ALL ON TABLE public.user_backups TO service_role;

-- 授予函数执行权限
GRANT EXECUTE ON FUNCTION public.accounts_register TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_login TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password_with_answer TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_update_security_question TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.sync_upload_data TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.sync_download_data TO anon, authenticated, service_role;

-- 脚本结束