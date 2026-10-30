import { get } from '@/utils/request'

// request 拦截器已自动解包，拿到的直接是 data（string）
export function getHealth() {
  return get<string>('/v1/health')
}
