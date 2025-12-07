-- ==========================================
-- AIme - Supabase 完整数据库构建脚本
-- ==========================================

-- 1. 初始化扩展
-- 启用 pgcrypto 扩展，用于密码加密 (crypt, gen_salt) 和 UUID 生成
CREATE SCHEMA IF NOT EXISTS extensions;
CREATE EXTENSION IF NOT EXISTS pgcrypto SCHEMA extensions;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" SCHEMA extensions;

-- 2. 创建表结构

-- 2.1 用户账户表 (accounts)
CREATE TABLE IF NOT EXISTS public.accounts (
    id uuid DEFAULT extensions.uuid_generate_v4() NOT NULL PRIMARY KEY,
    email text NOT NULL UNIQUE,
    password text NOT NULL, -- 存储加密后的哈希值
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

-- 3. 创建索引 (优化查询性能)
CREATE INDEX IF NOT EXISTS idx_accounts_email ON public.accounts(email);
CREATE INDEX IF NOT EXISTS idx_sessions_token ON public.account_sessions(token);
CREATE INDEX IF NOT EXISTS idx_sessions_user_id ON public.account_sessions(user_id);

-- 4. 核心业务函数 (RPC)

-- ----------------------------------------------------------------
-- 函数 1: 用户注册 (accounts_register)
-- 逻辑: 检查邮箱是否存在 -> 对密码进行 Blowfish 哈希 -> 插入数据 -> 生成 Token
-- ----------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.accounts_register(
    p_email text, 
    p_password text,
    p_question text DEFAULT '',
    p_answer text DEFAULT ''
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER -- 使用定义者的权限运行，绕过 RLS 限制
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

  -- 2. 创建用户 (核心修改：使用 crypt 对密码进行哈希)
  INSERT INTO public.accounts(email, password, security_question, security_answer)
  VALUES (p_email, crypt(p_password, gen_salt('bf')), p_question, p_answer)
  RETURNING id INTO new_uid;

  -- 3. 创建 Session Token
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

-- ----------------------------------------------------------------
-- 函数 2: 用户登录 (accounts_login)
-- 逻辑: 查找用户 -> 比对哈希密码 -> 刷新 Token
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
  -- 获取用户 ID 和存储的哈希密码
  SELECT id, password INTO v_uid, v_stored_pass 
  FROM public.accounts WHERE email = p_email LIMIT 1;
  
  -- 核心修改：使用 crypt 函数验证密码 (输入的密码 + 存储的盐 = 存储的哈希)
  IF v_uid IS NOT NULL AND v_stored_pass = crypt(p_password, v_stored_pass) THEN
    -- 生成新 Token
    v_token := encode(gen_random_bytes(32), 'hex');
    
    -- 清除旧会话并建立新会话
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
    -- 核心修改：哈希新密码
    UPDATE public.accounts 
    SET password = crypt(p_new_password, gen_salt('bf')), 
        updated_at = now() 
    WHERE id = v_uid;
    
    -- 安全起见，重置密码后使旧 Session 失效
    DELETE FROM public.account_sessions WHERE user_id = v_uid;
    
    RETURN jsonb_build_object('ok', true, 'message', '密码重置成功，请重新登录');
  ELSE
    RETURN jsonb_build_object('ok', false, 'message', '验证失败：答案错误');
  END IF;
END;
$$;

-- ----------------------------------------------------------------
-- 函数 4: 更新安全问题 (accounts_update_security_question)
-- 逻辑: 验证旧密码 -> 更新问题和答案
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

  -- 核心修改：验证哈希密码
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
-- 注意: 通常用于管理员操作或已通过其他方式验证身份后的调用
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
    -- 核心修改：哈希新密码
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

-- 5. 安全设置 (Row Level Security)
-- 即使表是 public 的，启用 RLS 也是个好习惯，防止意外的客户端直接访问
ALTER TABLE public.accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.account_sessions ENABLE ROW LEVEL SECURITY;

-- 默认策略：拒绝所有匿名访问 (因为我们主要通过 RPC 函数操作，所以这里可以设为严格)
-- 如果你需要客户端直接读取数据，需要添加相应的 CREATE POLICY 语句
CREATE POLICY "Deny all public access to accounts" ON public.accounts FOR ALL USING (false);
CREATE POLICY "Deny all public access to sessions" ON public.account_sessions FOR ALL USING (false);

-- 6. 权限赋予
-- 允许匿名用户 (API) 和登录用户调用这些 RPC 函数
GRANT USAGE ON SCHEMA public TO anon, authenticated, service_role;
GRANT ALL ON TABLE public.accounts TO service_role; -- 服务端角色拥有完全控制权
GRANT ALL ON TABLE public.account_sessions TO service_role;

-- 授予函数执行权限
GRANT EXECUTE ON FUNCTION public.accounts_register TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_login TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password_with_answer TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_update_security_question TO anon, authenticated, service_role;
GRANT EXECUTE ON FUNCTION public.accounts_reset_password TO anon, authenticated, service_role;