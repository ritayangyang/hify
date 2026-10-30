<script setup lang="ts" generic="D">
import { ref } from 'vue'

import type { FormDialogMode } from './types'

const props = withDefaults(
  defineProps<{
    /** v-model：弹窗显隐 */
    modelValue: boolean
    createTitle?: string
    editTitle?: string
    width?: string | number
    /** 提交中状态（父组件保存为异步时传入，用于禁用按钮） */
    submitting?: boolean
  }>(),
  {
    createTitle: '新建',
    editTitle: '编辑',
    width: 520,
    submitting: false,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  /**
   * 弹窗打开：open() 无参为 create，open(data) 为 edit。
   * 父组件据此重置/回填表单。
   */
  open: [payload: { mode: FormDialogMode; data?: D }]
  /** 点击确定：父组件校验并保存，成功后调用 close() */
  submit: [payload: { mode: FormDialogMode; data?: D }]
}>()

const mode = ref<FormDialogMode>('create')
const editingData = ref<D>()

/** 打开弹窗；不传 data 为新增模式，传 data 为编辑模式 */
function open(data?: D) {
  mode.value = data === undefined ? 'create' : 'edit'
  editingData.value = data
  emit('update:modelValue', true)
  emit('open', { mode: mode.value, data })
}

function close() {
  emit('update:modelValue', false)
}

function handleSubmit() {
  emit('submit', { mode: mode.value, data: editingData.value })
}

defineExpose({ open, close })
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="mode === 'create' ? props.createTitle : props.editTitle"
    :width="width"
    :close-on-click-modal="false"
    destroy-on-close
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <!-- 表单内容由父组件提供（el-form + 表单项） -->
    <slot :mode="mode" :data="editingData" />

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>
