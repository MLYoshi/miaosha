import { Card, Empty, Typography } from 'antd'

/** 商品列表页骨架（后续票迭代填充秒杀卡片/倒计时） */
export default function GoodsListPage() {
  return (
    <Card>
      <Typography.Title level={4}>商品列表</Typography.Title>
      <Empty description="商品列表建设中（见后续迭代）" />
    </Card>
  )
}
