import { ref, shallowRef, type Ref, type ShallowRef } from 'vue'

export interface UseRequestOptions<T> {
  /** 创建后是否立即执行 */
  immediate?: boolean
  /** immediate 时使用的参数 */
  defaultParams?: unknown[]
  initialData?: T
  onSuccess?: (data: T) => void
  onError?: (error: unknown) => void
}

export interface UseRequestResult<T, A extends unknown[]> {
  data: ShallowRef<T | undefined>
  loading: Ref<boolean>
  error: ShallowRef<unknown>
  execute: (...args: A) => Promise<T | undefined>
}

/**
 * 请求状态管理 composable。
 *
 * const { data, loading, execute } = useRequest((id: string) => getAgent(id), {
 *   immediate: true,
 *   defaultParams: ['123'],
 * })
 *
 * data 用 shallowRef：列表/对象整体替换即可，避免大对象的深度响应式开销。
 */
export function useRequest<T, A extends unknown[] = unknown[]>(
  fn: (...args: A) => Promise<T>,
  options: UseRequestOptions<T> = {},
): UseRequestResult<T, A> {
  const {
    immediate = false,
    defaultParams = [],
    initialData,
    onSuccess,
    onError,
  } = options

  const data = shallowRef<T | undefined>(initialData)
  const loading = ref(false)
  const error = shallowRef<unknown>(null)

  async function execute(...args: A): Promise<T | undefined> {
    loading.value = true
    error.value = null
    try {
      const result = await fn(...args)
      data.value = result
      onSuccess?.(result)
      return result
    } catch (e) {
      error.value = e
      onError?.(e)
      return undefined
    } finally {
      loading.value = false
    }
  }

  if (immediate) {
    execute(...(defaultParams as A))
  }

  return { data, loading, error, execute }
}
