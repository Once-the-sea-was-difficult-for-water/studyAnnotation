<template>
  <div class="skill-cards">
    <div class="cards-header">
      <span class="cards-title">技能卡片</span>
    </div>

    <el-input
      v-model="searchQuery"
      placeholder="搜索 Skill…"
      :prefix-icon="Search"
      size="small"
      clearable
      class="search-input"
    />

    <div class="category-tabs">
      <el-tag
        v-for="cat in categories"
        :key="cat.value"
        :type="activeCategory === cat.value ? '' : 'info'"
        :effect="activeCategory === cat.value ? 'dark' : 'plain'"
        class="category-tag"
        @click="toggleCategory(cat.value)"
      >
        {{ cat.label }}
      </el-tag>
    </div>

    <div class="cards-grid">
      <el-card
        v-for="skill in filteredSkills"
        :key="skill.id"
        shadow="hover"
        class="skill-card"
        @click="$emit('skill-select', skill)"
      >
        <div class="card-icon">{{ skill.icon || '⚡' }}</div>
        <div class="card-info">
          <div class="card-name">{{ skill.name }}</div>
          <div class="card-desc">{{ skill.description }}</div>
        </div>
        <el-tag size="small" :type="categoryTagType(skill.category)" class="card-category">
          {{ skill.category }}
        </el-tag>
      </el-card>
      <div v-if="filteredSkills.length === 0" class="empty-hint">无匹配 Skill</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { Skill, SkillCategory } from '@/types'

const props = defineProps<{
  skills: Skill[]
}>()

defineEmits<{
  'skill-select': [skill: Skill]
}>()

const searchQuery = ref('')
const activeCategory = ref<SkillCategory | null>(null)

const categories: { value: SkillCategory; label: string }[] = [
  { value: 'DIAGNOSE', label: '诊断' },
  { value: 'OPTIMIZE', label: '优化' },
  { value: 'MONITOR', label: '监控' },
  { value: 'SECURITY', label: '安全' },
]

function toggleCategory(cat: SkillCategory) {
  activeCategory.value = activeCategory.value === cat ? null : cat
}

const filteredSkills = computed(() => {
  let list = props.skills
  if (activeCategory.value) {
    list = list.filter((s) => s.category === activeCategory.value)
  }
  const q = searchQuery.value.trim().toLowerCase()
  if (q) {
    list = list.filter(
      (s) => s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q),
    )
  }
  return list
})

function categoryTagType(cat: SkillCategory): '' | 'success' | 'warning' | 'danger' | 'info' {
  const map: Record<SkillCategory, '' | 'success' | 'warning' | 'danger' | 'info'> = {
    DIAGNOSE: '',
    OPTIMIZE: 'success',
    MONITOR: 'warning',
    SECURITY: 'danger',
  }
  return map[cat] ?? 'info'
}
</script>

<style scoped>
.skill-cards {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--el-bg-color);
  border-left: 1px solid var(--el-border-color-lighter);
  padding: 12px;
  gap: 10px;
}

.cards-header {
  font-weight: 600;
  font-size: 14px;
}

.cards-title {
  color: var(--el-text-color-primary);
}

.search-input {
  flex-shrink: 0;
}

.category-tabs {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.category-tag {
  cursor: pointer;
  user-select: none;
}

.cards-grid {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skill-card {
  cursor: pointer;
  transition: transform 0.15s;
}

.skill-card:hover {
  transform: translateY(-1px);
}

.skill-card :deep(.el-card__body) {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px;
}

.card-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.card-info {
  flex: 1;
  min-width: 0;
}

.card-name {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 2px;
}

.card-desc {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.card-category {
  flex-shrink: 0;
  font-size: 10px;
}

.empty-hint {
  text-align: center;
  padding: 24px 0;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
}
</style>
