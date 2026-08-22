import { Layout, Menu, Space, Button } from 'antd'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { clearToken, getToken } from '../auth/token'

const { Header, Content, Footer } = Layout

/** 带顶部导航的页面壳：未登录重定向到 /login */
export default function AppLayout() {
  const navigate = useNavigate()
  const location = useLocation()

  const selectedKey = location.pathname === '/profile' ? 'profile' : 'goods'

  if (!getToken()) {
    return <Navigate to="/login" replace />
  }

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center' }}>
        <div style={{ color: '#fff', fontSize: 18, fontWeight: 600, marginRight: 32 }}>秒杀商城</div>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[selectedKey]}
          onClick={({ key }) => navigate(key === 'goods' ? '/' : `/${key}`)}
          items={[
            { key: 'goods', label: '商品列表' },
            { key: 'profile', label: '个人信息' },
          ]}
          style={{ flex: 1, minWidth: 0 }}
        />
        <Space>
          <Button
            type="text"
            style={{ color: 'rgba(255,255,255,0.85)' }}
            onClick={() => {
              clearToken()
              navigate('/login')
            }}
          >
            退出登录
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>
        <Outlet />
      </Content>
      <Footer style={{ textAlign: 'center' }}>Seckill Demo ©2026</Footer>
    </Layout>
  )
}
