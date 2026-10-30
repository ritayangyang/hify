import axios, {
  type AxiosRequestConfig,
  type AxiosResponse,
} from 'axios'
import { ElMessage } from 'element-plus'

/** 后端统一响应结构，与 com.hify.common.web.Result 对应 */
export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

// baseURL 走 Vite 代理 /api → http://localhost:8080
const service = axios.create({
  baseURL: '/api',
  timeout: 15_000,
})

// 响应拦截器：业务 code === 200 自动解包返回 data；非 200 提示并 reject。
// 解包会改变响应形状（AxiosResponse → data），与 axios 拦截器类型签名不符，故做一次局部断言。
service.interceptors.response.use(
  ((response: AxiosResponse<ApiResult<unknown>>) => {
    const res = response.data
    if (res.code === 200) {
      return res.data
    }
    ElMessage.error(res.message)
    return Promise.reject(new Error(res.message || '请求失败'))
  }) as (value: AxiosResponse) => AxiosResponse | Promise<AxiosResponse>,
  (error) => {
    // HTTP 层错误（4xx/5xx），后端 GlobalExceptionHandler 返回体仍带 message
    const message: string =
      error?.response?.data?.message ?? error?.message ?? '网络异常，请稍后重试'
    ElMessage.error(message)
    return Promise.reject(error instanceof Error ? error : new Error(message))
  },
)

// 拦截器已将响应解包为 T，故把返回值断言为 Promise<T>
export function get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return service.get(url, config) as Promise<T>
}

export function post<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return service.post(url, data, config) as Promise<T>
}

export function put<T>(
  url: string,
  data?: unknown,
  config?: AxiosRequestConfig,
): Promise<T> {
  return service.put(url, data, config) as Promise<T>
}

export function del<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  return service.delete(url, config) as Promise<T>
}

export default service
