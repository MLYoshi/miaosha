import { Route, Routes } from 'react-router-dom'
import AppLayout from './layouts/AppLayout'
import GoodsListPage from './pages/GoodsListPage'
import GoodsDetailPage from './pages/GoodsDetailPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import ProfilePage from './pages/ProfilePage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/" element={<AppLayout />}>
        <Route index element={<GoodsListPage />} />
        <Route path="goods/:goodsId" element={<GoodsDetailPage />} />
        <Route path="profile" element={<ProfilePage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
