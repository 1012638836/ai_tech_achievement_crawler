<template>
  <div>
    <!-- 搜索栏 -->
    <el-card style="margin-bottom: 20px">
      <el-row :gutter="16" align="middle">
        <el-col :span="6">
          <el-input v-model="query.school" placeholder="按学校搜索" clearable @clear="loadData" />
        </el-col>
        <el-col :span="4">
          <el-select v-model="query.status" placeholder="状态" clearable @change="loadData">
            <el-option label="待分析" :value="0" />
            <el-option label="规则就绪" :value="1" />
            <el-option label="采集中" :value="2" />
            <el-option label="异常" :value="3" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-button type="primary" @click="loadData">搜索</el-button>
          <el-button type="success" @click="showImport = true">Excel导入</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card>
      <el-table :data="tableData" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="school" label="学校/机构" width="150" />
        <el-table-column prop="listUrl" label="列表页URL" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalCrawled" label="已采集" width="80" align="center" />
        <el-table-column prop="lastCrawlTime" label="最后采集" width="170" />
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="handleAnalyze(row)" :loading="row._analyzing"
                       :disabled="row.status === 2">分析规则</el-button>
            <el-button v-if="row.status !== 2" size="small" type="primary" @click="handleCrawl(row)"
                       :loading="row._crawling" :disabled="row.status !== 1">采集</el-button>
            <el-button v-else size="small" type="danger" @click="handleStopCrawl(row)"
                       :loading="row._stopping">停止采集</el-button>
            <el-button size="small" @click="handleViewRule(row)">规则</el-button>
            <el-popconfirm title="确定删除该数据源吗？" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger" :disabled="row.status === 2">删除</el-button>
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

    <!-- Excel导入对话框 -->
    <el-dialog v-model="showImport" title="Excel导入数据源" width="500px">
      <p style="margin-bottom: 12px; color: #666">
        Excel格式：第1列 学校/机构 | 第2列 列表页URL | 第3列 备注(可选)，第一行为表头
      </p>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
      >
        <template #trigger>
          <el-button type="primary">选择文件</el-button>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImport = false">取消</el-button>
        <el-button type="primary" @click="handleImport" :loading="importing">导入</el-button>
      </template>
    </el-dialog>

    <!-- 规则编辑对话框 -->
    <el-dialog v-model="showRule" title="查看/编辑采集规则" width="620px">
      <el-form v-if="currentRule" label-width="120px" size="small">
        <el-form-item label="链接选择器">
          <el-input v-model="currentRule.articleSelector" placeholder="选中a标签，用于提取URL" />
        </el-form-item>
        <el-form-item label="链接属性">
          <el-input v-model="currentRule.articleUrlAttr" placeholder="href" />
        </el-form-item>
        <el-form-item label="标题选择器">
          <el-input v-model="currentRule.titleSelector" placeholder="选中标题文本元素，留空则从链接取文本" />
        </el-form-item>
        <el-form-item label="正文选择器">
          <el-input v-model="currentRule.contentSelector" placeholder="可选" />
        </el-form-item>
        <el-divider content-position="left">翻页设置</el-divider>
        <el-form-item label="翻页类型">
          <el-select v-model="currentRule.paginationType" placeholder="选择翻页方式">
            <el-option label="URL模板翻页" value="URL_PATTERN" />
            <el-option label="CSS选择器翻页" value="CSS_SELECTOR" />
            <el-option label="API接口翻页" value="API" />
            <el-option label="浏览器点击翻页" value="BROWSER" />
          </el-select>
        </el-form-item>
        <!-- URL_PATTERN -->
        <el-form-item v-if="!currentRule.paginationType || currentRule.paginationType === 'URL_PATTERN'" label="翻页URL模板">
          <el-input v-model="currentRule.urlPattern" placeholder="如 /1020/list{page}.htm" />
        </el-form-item>
        <!-- CSS_SELECTOR -->
        <template v-if="currentRule.paginationType === 'CSS_SELECTOR'">
          <el-form-item label="下一页选择器">
            <el-input v-model="currentRule.nextPageSelector" placeholder="下一页链接的CSS选择器" />
          </el-form-item>
          <el-form-item label="下一页链接属性">
            <el-input v-model="currentRule.nextPageUrlAttr" placeholder="href" />
          </el-form-item>
        </template>
        <!-- API -->
        <template v-if="currentRule.paginationType === 'API'">
          <el-form-item label="API地址">
            <el-input v-model="currentRule.apiUrl" placeholder="数据接口URL，可含{page}占位符" />
          </el-form-item>
          <el-form-item label="请求方法">
            <el-select v-model="currentRule.apiMethod" placeholder="GET" style="width: 120px">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
            </el-select>
          </el-form-item>
          <el-form-item label="页码参数名">
            <el-input v-model="currentRule.apiPageParam" placeholder="如 pageNum、page" />
          </el-form-item>
          <el-form-item label="数据路径">
            <el-input v-model="currentRule.apiDataPath" placeholder="JSON中列表路径，如 data.rows" />
          </el-form-item>
          <el-form-item label="标题字段">
            <el-input v-model="currentRule.apiTitleField" placeholder="如 title" />
          </el-form-item>
          <el-form-item label="链接字段">
            <el-input v-model="currentRule.apiUrlField" placeholder="如 url、link" />
          </el-form-item>
        </template>
        <!-- BROWSER -->
        <el-form-item v-if="currentRule.paginationType === 'BROWSER'" label="翻页按钮选择器">
          <el-input v-model="currentRule.browserNextBtn" placeholder="下一页按钮的CSS选择器" />
        </el-form-item>
      </el-form>

      <!-- 测试结果 -->
      <div v-if="testResult" style="margin-top: 16px">
        <el-divider content-position="left">测试结果</el-divider>
        <h4 style="margin: 8px 0">识别到的文章（共 {{ testResult.articles?.length || 0 }} 条）</h4>
        <el-table :data="testResult.articles" max-height="250" border size="small">
          <el-table-column type="index" width="50" />
          <el-table-column label="标题" prop="title" show-overflow-tooltip />
          <el-table-column label="链接" width="80" align="center">
            <template #default="{ row }">
              <a :href="row.url" target="_blank" style="color: #409eff">打开</a>
            </template>
          </el-table-column>
        </el-table>
        <div style="margin: 8px 0; color: #666">
          <strong>翻页信息：</strong>
          <span v-if="testResult.nextPageUrl">{{ testResult.nextPageUrl }}</span>
          <span v-else style="color: #999">未识别到翻页</span>
        </div>
        <div v-if="testResult.sampleContent">
          <h4 style="margin: 8px 0">正文预览（{{ testResult.sampleTitle }}）</h4>
          <el-card shadow="never" style="max-height: 150px; overflow-y: auto">
            <p style="font-size: 13px; line-height: 1.6; color: #333">{{ testResult.sampleContent }}</p>
          </el-card>
        </div>
      </div>

      <template #footer>
        <el-button @click="showRule = false">取消</el-button>
        <el-button type="warning" @click="handleTestRule" :loading="testingRule" :disabled="!currentRule">测试规则</el-button>
        <el-button type="primary" @click="handleSaveRule" :loading="savingRule" :disabled="!currentRule">保存规则</el-button>
      </template>
    </el-dialog>

    <!-- 规则预览对话框 -->
    <el-dialog v-model="showPreview" title="规则预览 - 请确认识别结果" width="900px" :close-on-click-modal="false">
      <div v-if="previewData" v-loading="previewLoading">
        <!-- 规则信息 -->
        <el-descriptions title="AI生成的规则" :column="2" border size="small" style="margin-bottom: 16px">
          <el-descriptions-item label="链接选择器">{{ previewData.rule.articleSelector }}</el-descriptions-item>
          <el-descriptions-item label="链接属性">{{ previewData.rule.articleUrlAttr }}</el-descriptions-item>
          <el-descriptions-item label="标题选择器">{{ previewData.rule.titleSelector || '同链接选择器' }}</el-descriptions-item>
          <el-descriptions-item label="翻页类型">
            <el-tag size="small">{{ paginationTypeText(previewData.rule.paginationType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="翻页URL模板">{{ previewData.rule.urlPattern || '无' }}</el-descriptions-item>
          <el-descriptions-item label="下一页选择器">{{ previewData.rule.nextPageSelector || '无' }}</el-descriptions-item>
          <el-descriptions-item label="正文选择器">{{ previewData.rule.contentSelector || '无' }}</el-descriptions-item>
          <el-descriptions-item v-if="previewData.rule.browserNextBtn" label="翻页按钮">{{ previewData.rule.browserNextBtn }}</el-descriptions-item>
        </el-descriptions>

        <!-- 文章列表预览 -->
        <h4 style="margin: 12px 0 8px">识别到的文章列表（共 {{ previewData.articles?.length || 0 }} 条）</h4>
        <el-table :data="previewData.articles" max-height="300" border size="small">
          <el-table-column type="index" width="50" />
          <el-table-column label="标题" prop="title" show-overflow-tooltip />
          <el-table-column label="链接" width="80" align="center">
            <template #default="{ row }">
              <a :href="row.url" target="_blank" style="color: #409eff">打开</a>
            </template>
          </el-table-column>
        </el-table>

        <!-- 下一页URL -->
        <div style="margin: 12px 0; color: #666">
          <strong>下一页URL：</strong>
          <span v-if="previewData.nextPageUrl">{{ previewData.nextPageUrl }}</span>
          <span v-else style="color: #999">未识别到翻页</span>
        </div>

        <!-- 正文预览 -->
        <h4 style="margin: 12px 0 8px">正文预览（{{ previewData.sampleTitle }}）</h4>
        <el-card shadow="never" style="max-height: 200px; overflow-y: auto">
          <p style="font-size: 13px; line-height: 1.6; color: #333">{{ previewData.sampleContent || '无法获取正文内容' }}</p>
        </el-card>
      </div>
      <div v-else-if="previewLoading" v-loading="true" style="height: 200px"></div>

      <template #footer>
        <el-button @click="showPreview = false">取消</el-button>
        <el-button type="warning" @click="handleReAnalyze" :loading="previewLoading">重新分析</el-button>
        <el-button type="primary" @click="handleConfirmRule" :loading="confirmLoading">确认规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { sourceApi, taskApi } from '../api'

const query = ref({ school: '', status: null, page: 1, size: 20 })
const tableData = ref([])
const total = ref(0)
const loading = ref(false)
const showImport = ref(false)
const importing = ref(false)
const uploadFile = ref(null)
const showRule = ref(false)
const currentRule = ref(null)
const ruleSourceId = ref(null)
const savingRule = ref(false)
const testingRule = ref(false)
const testResult = ref(null)
const showPreview = ref(false)
const previewData = ref(null)
const previewLoading = ref(false)
const confirmLoading = ref(false)
const previewSourceId = ref(null)

const statusText = (s) => ['待分析', '规则就绪', '采集中', '异常'][s] || '未知'
const statusTag = (s) => ['info', 'success', 'warning', 'danger'][s] || 'info'
const paginationTypeText = (t) => ({ URL_PATTERN: 'URL模板', CSS_SELECTOR: 'CSS选择器', API: 'API接口', BROWSER: '浏览器点击' }[t] || t || 'URL模板')

const loadData = async () => {
  loading.value = true
  try {
    const res = await sourceApi.list(query.value)
    tableData.value = res.data.data.list || []
    total.value = res.data.data.total || 0
  } finally {
    loading.value = false
  }
}

const handleFileChange = (file) => { uploadFile.value = file.raw }

const handleImport = async () => {
  if (!uploadFile.value) { ElMessage.warning('请选择文件'); return }
  importing.value = true
  try {
    const res = await sourceApi.importExcel(uploadFile.value)
    ElMessage.success(`成功导入 ${res.data.data} 条数据源`)
    showImport.value = false
    loadData()
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

const handleAnalyze = async (row) => {
  row._analyzing = true
  previewSourceId.value = row.id
  previewLoading.value = true
  previewData.value = null
  showPreview.value = true
  try {
    const res = await sourceApi.previewRule(row.id)
    previewData.value = res.data.data
  } catch (e) {
    ElMessage.error('规则分析失败')
    showPreview.value = false
  } finally {
    row._analyzing = false
    previewLoading.value = false
  }
}

const handleCrawl = async (row) => {
  row._crawling = true
  try {
    const res = await sourceApi.crawl(row.id)
    ElMessage.success(`采集任务已创建，任务ID: ${res.data.data}`)
    loadData()
  } catch (e) {
    ElMessage.error('启动采集失败')
  } finally {
    row._crawling = false
  }
}

const handleStopCrawl = async (row) => {
  row._stopping = true
  try {
    // 获取该数据源最近的RUNNING任务并停止
    const res = await taskApi.list({ page: 1, size: 50 })
    const tasks = res.data.data.list || []
    const runningTask = tasks.find(t => t.sourceId === row.id && t.status === 'RUNNING')
    if (runningTask) {
      await taskApi.stop(runningTask.id)
      ElMessage.success('已发送停止指令')
      setTimeout(loadData, 2000)
    } else {
      ElMessage.warning('未找到运行中的任务')
      loadData()
    }
  } catch (e) {
    ElMessage.error('停止失败')
  } finally {
    row._stopping = false
  }
}

const handleDelete = async (row) => {
  try {
    await sourceApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e) {
    ElMessage.error('删除失败')
  }
}

const handleViewRule = async (row) => {
  try {
    const res = await sourceApi.getRule(row.id)
    currentRule.value = res.data.data ? { ...res.data.data } : {
      articleSelector: '',
      articleUrlAttr: 'href',
      titleSelector: '',
      contentSelector: '',
      paginationType: 'URL_PATTERN',
      urlPattern: ''
    }
    ruleSourceId.value = row.id
    testResult.value = null
    showRule.value = true
  } catch (e) {
    ElMessage.error('获取规则失败')
  }
}

const handleSaveRule = async () => {
  if (!currentRule.value) return
  savingRule.value = true
  try {
    await sourceApi.confirmRule(ruleSourceId.value, currentRule.value)
    ElMessage.success('规则已保存')
    showRule.value = false
    testResult.value = null
    loadData()
  } catch (e) {
    ElMessage.error('保存规则失败')
  } finally {
    savingRule.value = false
  }
}

const handleTestRule = async () => {
  if (!currentRule.value) return
  testingRule.value = true
  testResult.value = null
  try {
    const res = await sourceApi.testRule(ruleSourceId.value, currentRule.value)
    testResult.value = res.data.data
  } catch (e) {
    ElMessage.error('测试规则失败: ' + (e.response?.data?.message || e.message))
  } finally {
    testingRule.value = false
  }
}

const handleReAnalyze = async () => {
  previewLoading.value = true
  previewData.value = null
  try {
    const res = await sourceApi.previewRule(previewSourceId.value)
    previewData.value = res.data.data
  } catch (e) {
    ElMessage.error('重新分析失败')
  } finally {
    previewLoading.value = false
  }
}

const handleConfirmRule = async () => {
  if (!previewData.value?.rule) return
  confirmLoading.value = true
  try {
    await sourceApi.confirmRule(previewSourceId.value, previewData.value.rule)
    ElMessage.success('规则已确认保存')
    showPreview.value = false
    loadData()
  } catch (e) {
    ElMessage.error('规则确认失败')
  } finally {
    confirmLoading.value = false
  }
}

onMounted(loadData)
</script>
