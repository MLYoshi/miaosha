import { Card, Empty, Typography } from 'antd'

/** 个人信息页骨架（后续票迭代填充用户资料） */
export default function ProfilePage() {
  return (
    <Card>
      <Typography.Title level={4}>个人信息</Typography.Title>
      <Empty description="个人信息建设中（见后续迭代）" />
    </Card>
  )
}
