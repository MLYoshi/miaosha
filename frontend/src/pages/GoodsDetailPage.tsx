import { Card, Empty, Typography } from 'antd'
import { useParams } from 'react-router-dom'

/** 商品详情页骨架（后续票迭代填充详情/秒杀按钮/倒计时） */
export default function GoodsDetailPage() {
  const { goodsId } = useParams<{ goodsId: string }>()

  return (
    <Card>
      <Typography.Title level={4}>商品详情</Typography.Title>
      <Empty description={`商品 ${goodsId ?? ''} 详情建设中（见后续迭代）`} />
    </Card>
  )
}
