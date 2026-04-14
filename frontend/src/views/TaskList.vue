<template>
  <div>
    <el-card>
      <template #header>采集任务列表</template>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="sourceId" label="数据源ID" width="90" />
        <el-table-column prop="type" label="类型" width="110">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.type)" size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalFound" label="发现" width="70" align="center" />
        <el-table-column prop="totalCrawled" label="采集" width="70" align="center" />
        <el-table-column prop="totalStructured" label="结构化" width="80" align="center" />
        <el-table-column prop="failReason" label="失败原因" show-overflow-tooltip />
        <el-table-column prop="startedAt" label="开始时间" width="170" />
        <el-table-column prop="finishedAt" label="完成时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'RUNNING'" size="small" type="danger" plain
                       @click="handleStop(row)" :loading="row._stopping">
              停止
            </el-button>
            <el-button v-if="row.status === 'FAILED' || row.status === 'STOPPED'" size="small" type="warning" plain
                       @click="handleRetry(row)" :loading="row._retrying">
              重试
            </el-button>
            <el-popconfirm title="确定删除该任务记录？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button v-if="row.status !== 'RUNNING'" size="small" type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        :page-size="size"
        :total="total"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadData"
      />
    </el-card>

    <!-- 错误日志 -->
    <el-card style="margin-top: 20px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>错误日志</span>
          <el-button size="small" @click="loadErrorLogs">刷新</el-button>
        </div>
      </template>
      <el-table :data="errorLogs" v-loading="errorLoading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="errorType" label="错误类型" width="150">
          <template #default="{ row }">
            <el-tag type="danger" size="small">{{ row.errorType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="errorDetail" label="具体错误" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="发生时间" width="170" />
      </el-table>

      <el-pagination
        v-model:current-page="errorPage"
        :page-size="errorSize"
        :total="errorTotal"
        layout="total, prev, pager, next"
        style="margin-top: 16px; justify-content: flex-end"
        @current-change="loadErrorLogs"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { taskApi, errorLogApi } from '../api'

const tableData = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

const typeText = (t) => ({ FULL: '全量采集', INCREMENTAL: '增量扫描', RULE_GEN: '规则生成' }[t] || t)
const typeTag = (t) => ({ FULL: '', INCREMENTAL: 'success', RULE_GEN: 'warning' }[t] || 'info')
const statusTag = (s) => ({ PENDING: 'info', RUNNING: 'warning', SUCCESS: 'success', FAILED: 'danger', STOPPED: '' }[s] || 'info')

const loadData = async () => {
  loading.value = true
  try {
    const res = await taskApi.list({ page: page.value, size: size.value })
    tableData.value = res.data.data.list || []
    total.value = res.data.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleStop = async (row) => {
  row._stopping = true
  try {
    await taskApi.stop(row.id)
    ElMessage.success('已发送停止指令')
    setTimeout(loadData, 2000)
  } catch (e) {
    ElMessage.error(e.message || '停止失败')
  } finally {
    row._stopping = false
  }
}

const handleRetry = async (row) => {
  row._retrying = true
  try {
    const res = await taskApi.retry(row.id)
    ElMessage.success(`重试任务已创建，新任务ID: ${res.data.data}`)
    setTimeout(loadData, 1000)
  } catch (e) {
    ElMessage.error(e.message || '重试失败')
  } finally {
    row._retrying = false
  }
}

const handleDelete = async (row) => {
  try {
    await taskApi.delete(row.id)
    ElMessage.success('已删除')
    loadData()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

// 错误日志
const errorLogs = ref([])
const errorTotal = ref(0)
const errorPage = ref(1)
const errorSize = ref(20)
const errorLoading = ref(false)

const loadErrorLogs = async () => {
  errorLoading.value = true
  try {
    const res = await errorLogApi.list({ page: errorPage.value, size: errorSize.value })
    errorLogs.value = res.data.data.list || []
    errorTotal.value = res.data.data.total || 0
  } finally {
    errorLoading.value = false
  }
}

onMounted(() => {
  loadData()
  loadErrorLogs()
})
</script>
