import { useState } from 'react'
import { Button, Card, Form, Input, message, Tabs, Typography } from 'antd'
import { useNavigate } from 'react-router-dom'
import { login, register } from '../api/auth'
import type { LoginParams } from '../api/types'
import { ApiError } from '../api/errorCode'
import { setToken } from '../auth/token'

/** 登录/注册页：成功拿 JWT 存本地并跳首页，业务错误经拦截器 reject 后在此提示 */
export default function LoginPage() {
  const navigate = useNavigate()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (values: LoginParams, mode: 'login' | 'register') => {
    setLoading(true)
    try {
      const token = mode === 'login' ? await login(values) : await register(values)
      setToken(token)
      message.success(mode === 'login' ? '登录成功' : '注册成功')
      navigate('/')
    } catch (error) {
      // 拦截器已归一为 ApiError（业务失败 / 参数校验失败 / 网络异常）
      message.error(error instanceof ApiError ? error.message : '请求失败，请稍后重试')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f5f5',
      }}
    >
      <Card style={{ width: 380 }}>
        <Typography.Title level={3} style={{ textAlign: 'center' }}>
          秒杀商城
        </Typography.Title>
        <Tabs
          centered
          items={[
            {
              key: 'login',
              label: '登录',
              children: (
                <LoginForm loading={loading} submitText="登录" onFinish={(v) => handleSubmit(v, 'login')} />
              ),
            },
            {
              key: 'register',
              label: '注册',
              children: (
                <LoginForm loading={loading} submitText="注册" onFinish={(v) => handleSubmit(v, 'register')} />
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}

function LoginForm({
  loading,
  submitText,
  onFinish,
}: {
  loading: boolean
  submitText: string
  onFinish: (values: LoginParams) => void
}) {
  return (
    <Form<LoginParams> layout="vertical" onFinish={onFinish} requiredMark={false}>
      <Form.Item
        name="mobile"
        label="手机号"
        rules={[
          { required: true, message: '请输入手机号' },
          { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误' },
        ]}
      >
        <Input placeholder="请输入手机号" maxLength={11} autoComplete="username" />
      </Form.Item>
      <Form.Item
        name="password"
        label="密码"
        rules={[
          { required: true, message: '请输入密码' },
          { min: 6, max: 32, message: '密码长度必须在6-32位之间' },
        ]}
      >
        <Input.Password placeholder="请输入密码" autoComplete="current-password" />
      </Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>
        {submitText}
      </Button>
    </Form>
  )
}
