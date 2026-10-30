<script setup lang="ts" generic="T">
import { onMounted, reactive, ref } from 'vue'

import type { HifyColumn, PageParams, PagedData } from './types'

const props = withDefaults(
  defineProps<{
    columns: HifyColumn<T>[]
    /** 分页查询方法，接收 PageParams，返回分页数据 */
    fetchApi: (params: PageParams) => Promise<PagedData<T>>
    /** 额外查询条件（筛选表单等），随分页参数一起提交 */
    queryParams?: Record<string, unknown>
    pageSize?: number
    /** 是否显示多选列 */
    selection?: boolean
    /** 行数据 key（建议传主键字段名） */
    rowKey?: string
  }>(),
  {
    queryParams: () => ({}),
    pageSize: 20,
    selection: false,
    rowKey: 'id',
  },
)

const loading = ref(false)
const tableData = ref<T[]>([])
const pagination = reactive({
  page: 1,
  size: props.pageSize,
  total: 0,
})

async function fetchData() {
  loading.value = true
  try {
    const params: PageParams = {
      ...props.queryParams,
      page: pagination.page,
      size: pagination.size,
    }
    const res = await props.fetchApi(params)
    tableData.value = res.records ?? []
    pagination.total = res.total ?? 0
  } finally {
    loading.value = false
  }
}

/** 重新加载当前页（删除/修改后调用） */
function refresh() {
  return fetchData()
}

/** 回到第 1 页并重新加载（筛选条件变化后调用） */
function reset() {
  pagination.page = 1
  return fetchData()
}

function handlePageChange(page: number) {
  pagination.page = page
  fetchData()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  fetchData()
}

function getCellText(row: T, column: HifyColumn<T>) {
  if (column.formatter) {
    return column.formatter(row, column)
  }
  if (column.prop) {
    return String(row[column.prop] ?? '')
  }
  return ''
}

defineExpose({ refresh, reset, loading })

onMounted(fetchData)
</script>

<template>
  <div class="hify-table">
    <el-table
      v-loading="loading"
      :data="tableData"
      :row-key="rowKey"
      border
      stripe
    >
      <el-table-column
        v-if="selection"
        type="selection"
        width="48"
        align="center"
      />

      <el-table-column
        v-for="column in columns"
        :key="column.prop ?? column.slot ?? column.label"
        :prop="column.prop"
        :label="column.label"
        :width="column.width"
        :min-width="column.minWidth"
        :fixed="column.fixed"
        :align="column.align ?? 'left'"
        :sortable="column.sortable"
        show-overflow-tooltip
      >
        <template v-if="column.slot" #default="scope">
          <slot
            :name="column.slot"
            :row="scope.row as T"
            :$index="scope.$index"
          />
        </template>
        <template v-else-if="column.formatter" #default="scope">
          {{ getCellText(scope.row as T, column) }}
        </template>
      </el-table-column>

      <!-- 表格级扩展插槽：不便于配置的附加内容 -->
      <slot name="append" :rows="tableData" />
    </el-table>

    <div class="hify-table__pagination">
      <el-pagination
        background
        :current-page="pagination.page"
        :page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped>
.hify-table__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
