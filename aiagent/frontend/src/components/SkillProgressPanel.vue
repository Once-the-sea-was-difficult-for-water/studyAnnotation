<template>
  <div v-if="steps.length > 0" class="skill-progress-panel">
    <div class="panel-header">
      <el-icon><Loading v-if="isRunning" /><CircleCheckFilled v-else /></el-icon>
      <span class="panel-title">Skill 执行进度</span>
    </div>

    <el-steps :active="currentStep" direction="vertical" finish-status="success">
      <el-step
        v-for="(step, index) in steps"
        :key="step.id"
        :title="step.title"
        :description="step.description"
        :status="stepStatus(index)"
      >
        <template #description>
          <div class="step-detail">
            <span v-if="step.description" class="step-desc">{{ step.description }}</span>
            <span class="step-type-badge">{{ step.type }}</span>

            <!-- HUMAN_CONFIRM interaction -->
            <div
              v-if="step.type === 'HUMAN_CONFIRM' && index === currentStep && !step.confirmed"
              class="confirm-action"
            >
              <el-alert type="warning" :closable="false" show-icon>
                <template #title>需要您的确认才能继续执行</template>
              </el-alert>
              <div class="confirm-buttons">
                <el-button type="primary" size="small" @click="handleConfirm(step.id, true)">
                  确认继续
                </el-button>
                <el-button size="small" @click="handleConfirm(step.id, false)">
                  取消执行
                </el-button>
              </div>
            </div>

            <!-- Confirmed badge -->
            <el-tag v-if="step.confirmed" type="success" size="small">已确认</el-tag>
          </div>
        </template>
      </el-step>
    </el-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading, CircleCheckFilled } from '@element-plus/icons-vue'

export interface ProgressStep {
  id: string
  title: string
  description?: string
  type: string // TOOL_CALL | LLM_CALL | CONDITION | PARALLEL | HUMAN_CONFIRM
  status?: 'pending' | 'running' | 'success' | 'error'
  confirmed?: boolean
}

const props = defineProps<{
  steps: ProgressStep[]
  currentStep: number
}>()

const emit = defineEmits<{
  confirm: [stepId: string, approved: boolean]
}>()

const isRunning = computed(() => props.currentStep < props.steps.length)

function stepStatus(index: number): '' | 'wait' | 'process' | 'finish' | 'error' | 'success' {
  const step = props.steps[index]
  if (step?.status === 'error') return 'error'
  if (index < props.currentStep) return 'success'
  if (index === props.currentStep) return 'process'
  return 'wait'
}

function handleConfirm(stepId: string, approved: boolean) {
  emit('confirm', stepId, approved)
}
</script>

<style scoped>
.skill-progress-panel {
  padding: 12px 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-bg-color);
  margin: 8px 0;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  font-weight: 600;
  font-size: 14px;
}

.step-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 4px;
}

.step-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.step-type-badge {
  display: inline-block;
  width: fit-content;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--el-fill-color);
  color: var(--el-text-color-regular);
}

.confirm-action {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 4px;
}

.confirm-buttons {
  display: flex;
  gap: 8px;
}
</style>
