<template>
  <div v-loading="loading">
    <el-page-header @back="$router.back()" style="margin-bottom: 20px">
      <template #content>{{ achievement?.title }}</template>
    </el-page-header>

    <el-row :gutter="20" v-if="achievement">
      <!-- 左侧：结构化字段 -->
      <el-col :span="10">
        <el-card>
          <template #header>结构化信息</template>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="学校">{{ achievement.school }}</el-descriptions-item>
            <el-descriptions-item label="领域">{{ achievement.field }}</el-descriptions-item>
            <el-descriptions-item label="所处领域">
              <el-tag>{{ achievement.domain }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="阶段">
              <el-tag type="info">{{ achievement.stage }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="发表时间">{{ achievement.publishTime }}</el-descriptions-item>
            <el-descriptions-item label="期刊">{{ achievement.journals }}</el-descriptions-item>
            <el-descriptions-item label="资助方">{{ achievement.funders }}</el-descriptions-item>
            <el-descriptions-item label="应用场景">{{ achievement.applicationScenario }}</el-descriptions-item>
            <el-descriptions-item label="技术关键词">
              <el-tag v-for="kw in keywords" :key="kw" size="small" style="margin: 2px">{{ kw }}</el-tag>
            </el-descriptions-item>
          </el-descriptions>

          <!-- 研究人员 -->
          <h4 style="margin: 16px 0 8px">研究人员</h4>
          <el-table :data="researchers" size="small" stripe>
            <el-table-column prop="name" label="姓名" width="100" />
            <el-table-column label="单位" prop="school" width="150" />
            <el-table-column label="院系" prop="college" />
          </el-table>

          <div style="margin-top: 12px">
            <el-link :href="achievement.url" target="_blank" type="primary">查看原文</el-link>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧：原始内容 -->
      <el-col :span="14">
        <el-card>
          <template #header>原始内容</template>
          <div class="content-html" v-html="achievement.content"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { achievementApi } from '../api'

const route = useRoute()
const achievement = ref(null)
const researchers = ref([])
const loading = ref(false)

const keywords = computed(() => {
  if (!achievement.value?.techKeywords) return []
  return achievement.value.techKeywords.split(',').map(s => s.trim()).filter(Boolean)
})

onMounted(async () => {
  loading.value = true
  try {
    const res = await achievementApi.getById(route.params.id)
    const data = res.data.data
    achievement.value = data.achievement
    researchers.value = data.researchers || []
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.content-html { line-height: 1.8; max-height: 70vh; overflow-y: auto; }
.content-html :deep(img) { max-width: 100%; height: auto; }
</style>
