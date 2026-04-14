<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="480px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-position="top"
      @submit.prevent="handleSubmit"
    >
      <el-form-item
        v-for="param in params"
        :key="param.name"
        :label="param.description || param.name"
        :prop="param.name"
      >
        <!-- STRING -->
        <el-input
          v-if="param.type === 'STRING'"
          v-model="formData[param.name]"
          :placeholder="`请输入 ${param.name}`"
        />

        <!-- NUMBER -->
        <el-input-number
          v-else-if="param.type === 'NUMBER'"
          v-model="formData[param.name]"
          :placeholder="`请输入 ${param.name}`"
          controls-position="right"
          style="width: 100%"
        />

        <!-- ENUM -->
        <el-select
          v-else-if="param.type === 'ENUM'"
          v-model="formData[param.name]"
          :placeholder="`请选择 ${param.name}`"
          style="width: 100%"
        >
          <el-option
            v-for="val in param.enumValues ?? []"
            :key="val"
            :label="val"
            :value="val"
          />
        </el-select>

        <!-- INSTANCE_SELECTOR -->
        <el-input
          v-else-if="param.type === 'INSTANCE_SELECTOR'"
          v-model="formData[param.name]"
          :placeholder="`请输入实例 ID，如 rm-bp1234`"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" @click="handleSubmit">提交执行</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { SkillParam } from '@/types'

const props = defineProps<{
  modelValue: boolean
  params: SkillParam[]
  skillName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  submit: [params: Record<string, unknown>]
}>()

const formRef = ref<FormInstance>()
const formData = ref<Record<string, unknown>>({})

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const dialogTitle = computed(() =>
  props.skillName ? `${props.skillName} — 参数填写` : '参数填写',
)

// Build validation rules from params
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  for (const param of props.params) {
    if (param.required) {
      rules[param.name] = [
        {
          required: true,
          message: `${param.description || param.name} 为必填项`,
          trigger: param.type === 'ENUM' ? 'change' : 'blur',
        },
      ]
    }
  }
  return rules
})

// Initialize form data with defaults when params change
watch(
  () => props.params,
  (newParams) => {
    const data: Record<string, unknown> = {}
    for (const param of newParams) {
      data[param.name] = param.defaultValue ?? (param.type === 'NUMBER' ? undefined : '')
    }
    formData.value = data
  },
  { immediate: true },
)

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit', { ...formData.value })
    handleClose()
  } catch {
    // Validation failed — form shows inline errors
  }
}

function handleClose() {
  visible.value = false
  formRef.value?.resetFields()
}
</script>
