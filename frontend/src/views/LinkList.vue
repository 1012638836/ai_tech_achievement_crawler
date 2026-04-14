<template>
  <div>
    <!-- 筛选栏 -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input v-model="query.keyword" placeholder="搜索标题" clearable />
        </el-col>
        <el-col :span="4">
          <el-select v-model="query.isAchievement" placeholder="AI判断" clearable @change="loadData">
            <el-option label="是科研成果" :value="1" />
            <el-option label="非科研成果" :value="0" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-input v-model="query.sourceId" placeholder="数据源ID" clearable type="number" />
        </el-col>
        <el-col :span="4">
          <el-button type="primary" @click="loadData">搜索</el-button>
        </el-col>
        <el-col :span="7" style="text-align: right">
          <el-button type="warning" @click="handleBatchStructure" :loading="structuring">
            批量结构化
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 链接表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column label="标题" show-overflow-tooltip min-width="300">
          <template #default="{ row }">
            <a :href="row.url" target="_blank" rel="noopener"
               style="color: #409eff; text-decoration: none;">
              {{ row.title || row.url }}
            </a>
          </template>
        </el-table-column>
        <el-table-column prop="sourceId" label="数据源" width="80" align="center" />
        <el-table-column label="AI判断" width="120" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isAchievement === 1" type="success" size="small">科研成果</el-tag>
            <el-tag v-else-if="row.isAchievement === 0" type="danger" size="small">非科研成果</el-tag>
            <el-tag v-else type="info" size="small">未判断</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="judgeReason" label="判断理由" width="150" show-overflow-tooltip />
        <el-table-column label="采集状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="人工纠正" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.isAchievement !== 1"
              size="small" type="success" plain
              @click="handleJudge(row, 1)">
              标记为科研
            </el-button>
            <el-button
              v-if="row.isAchievement !== 0"
              size="small" type="danger" plain
              @click="handleJudge(row, 0)">
              标记非科研
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="query.page"
        :page-size="query.size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadData"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { linkApi } from '../api'

const query = ref({ keyword: '', isAchievement: null, sourceId: null, page: 1, size: 20 })
const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const structuring = ref(false)

const statusText = (s) => ['待采集', '已采集', '已结构化', '失败'][s] || '未知'
const statusTag = (s) => ['info', '', 'success', 'danger'][s] || 'info'

const loadData = async () => {
  loading.value = true
  try {
    const params = { ...query.value }
    if (params.sourceId === '' || params.sourceId === null) delete params.sourceId
    const res = await linkApi.list(params)
    tableData.value = res.data.data.list || []
    total.value = res.data.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleJudge = async (row, isAchievement) => {
  try {
    await linkApi.judge(row.id, isAchievement, '人工纠正')
    row.isAchievement = isAchievement
    row.judgeReason = '人工纠正'
    ElMessage.success('已更新')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleBatchStructure = async () => {
  structuring.value = true
  try {
    const res = await linkApi.batchStructure()
    ElMessage.success(`已启动批量结构化，待处理 ${res.data.data} 条`)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    structuring.value = false
  }
}

onMounted(loadData)
</script>
