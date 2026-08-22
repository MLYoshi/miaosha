import { request } from './http'
import type { MiaoshaAcceptVo, MiaoshaResultVo } from './types'

/** 秒杀受理：PROCESSING=排队中（轮询 result），SUCCESS=降级同步落库直接拿单 */
export function doMiaosha(goodsId: number): Promise<MiaoshaAcceptVo> {
  return request<MiaoshaAcceptVo>({
    method: 'POST',
    url: '/miaosha/do_miaosha',
    params: { goodsId },
  })
}

/** 秒杀结果轮询：四态（PROCESSING/SUCCESS/FAILED/NONE） */
export function getMiaoshaResult(goodsId: number): Promise<MiaoshaResultVo> {
  return request<MiaoshaResultVo>({
    method: 'GET',
    url: '/miaosha/result',
    params: { goodsId },
  })
}
