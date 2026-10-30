/** 公共组件共享类型 */

/** 分页查询入参（page 从 1 起），可与业务筛选条件合并 */
export interface PageParams {
  page: number
  size: number
  [key: string]: unknown
}

/** 分页数据：作为后端 Result 的 data（响应拦截器自动解包后的形状） */
export interface PagedData<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/** HifyTable 列配置 */
export interface HifyColumn<T> {
  /** 字段名；自定义列可省略，改用 slot */
  prop?: keyof T & string
  label: string
  width?: string | number
  minWidth?: string | number
  fixed?: true | 'left' | 'right'
  align?: 'left' | 'center' | 'right'
  sortable?: boolean | 'custom'
  /** 文本格式化（纯展示） */
  formatter?: (row: T, column: HifyColumn<T>) => string
  /** 自定义插槽名，插槽作用域为 { row, $index } */
  slot?: string
}

/** 表单弹窗模式 */
export type FormDialogMode = 'create' | 'edit'
