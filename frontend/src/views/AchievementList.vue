<template>
  <div>
    <!-- 搜索栏 -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="5">
          <el-input v-model="query.keyword" placeholder="搜索标题/关键词" clearable />
        </el-col>
        <el-col :span="4">
          <el-input v-model="query.school" placeholder="学校" clearable />
        </el-col>
        <el-col :span="4">
          <el-select v-model="query.domain" placeholder="领域" clearable>
            <el-option v-for="d in domains" :key="d" :label="d" :value="d" />
          </el-select>
        </el-col>
        <el-col :span="4">
          <el-select v-model="query.stage" placeholder="阶段" clearable>
            <el-option v-for="s in stages" :key="s" :label="s" :value="s" />
          </el-select>
        </el-col>
        <el-col :span="3">
          <el-button type="primary" @click="loadData">搜索</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 成果表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" show-overflow-tooltip min-width="250">
          <template #default="{ row }">
            <router-link :to="`/achievements/${row.id}`" class="link">{{ row.title }}</router-link>
          </template>
        </el-table-column>
        <el-table-column prop="school" label="学校" width="120" />
        <el-table-column prop="domain" label="领域" width="140" />
        <el-table-column prop="stage" label="阶段" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.stage }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="publishTime" label="发表时间" width="170" />
        <el-table-column prop="techKeywords" label="关键词" show-overflow-tooltip width="200" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-popconfirm title="确定删除该成果？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
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
import { achievementApi } from '../api'

const domains = [
  '新一代信息技术', '医药健康', '集成电路', '智能网联汽车',
  '智能制造与装备', '绿色能源与节能环保', '区块链与先进计算',
  '科技服务业', '智慧城市', '信息内容消费', '软件信息服务', '新材料'
]
const stages = ['科研进展', '概念前', '概念', '小试', '中试', '产业化', '其他']

const query = ref({ keyword: '', school: '', domain: '', stage: '', page: 1, size: 20 })
const tableData = ref([])
const total = ref(0)
const loading = ref(false)

const loadData = async () => {
  loading.value = true
  try {
    const res = await achievementApi.list(query.value)
    tableData.value = res.data.data.list || []
    total.value = res.data.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await achievementApi.delete(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.link { color: #409eff; text-decoration: none; }
.link:hover { text-decoration: underline; }
</style>
