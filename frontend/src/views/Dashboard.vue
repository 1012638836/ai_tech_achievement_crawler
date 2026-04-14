<template>
  <div>
    <!-- 总览卡片 -->
    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="数据源总数" :value="overview.totalSources || 0" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="成果总数" :value="overview.totalAchievements || 0" />
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <el-statistic title="今日新增" :value="overview.todayAchievements || 0" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <!-- 趋势图 -->
      <el-col :span="16">
        <el-card>
          <template #header>每日采集趋势（近30天）</template>
          <div ref="chartRef" style="height: 350px"></div>
        </el-card>
      </el-col>

      <!-- 更新排行 -->
      <el-col :span="8">
        <el-card>
          <template #header>数据源更新排行 TOP10</template>
          <el-table :data="topSources" size="small" stripe>
            <el-table-column prop="school" label="学校" />
            <el-table-column prop="total" label="新增数" width="80" align="center" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import * as echarts from 'echarts'
import { dashboardApi } from '../api'

const overview = ref({})
const topSources = ref([])
const chartRef = ref()

onMounted(async () => {
  try {
    const [overviewRes, trendRes, topRes] = await Promise.all([
      dashboardApi.overview(),
      dashboardApi.trend(),
      dashboardApi.topSources(),
    ])
    overview.value = overviewRes.data.data || {}
    topSources.value = topRes.data.data || []

    // 渲染趋势图
    const trendData = trendRes.data.data || []
    const chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['新发现链接', '新增成果'] },
      grid: { left: 50, right: 20, bottom: 30, top: 40 },
      xAxis: {
        type: 'category',
        data: trendData.map(d => d.statDate),
        axisLabel: { rotate: 30 },
      },
      yAxis: { type: 'value', minInterval: 1 },
      series: [
        { name: '新发现链接', type: 'line', data: trendData.map(d => d.newLinks || 0), smooth: true, areaStyle: { opacity: 0.1 } },
        { name: '新增成果', type: 'bar', data: trendData.map(d => d.newAchievements || 0) },
      ],
    })

    window.addEventListener('resize', () => chart.resize())
  } catch (e) {
    console.error('加载仪表盘数据失败', e)
  }
})
</script>
