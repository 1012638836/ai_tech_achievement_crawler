<template>
  <el-container class="layout">
    <el-aside width="200px" class="aside">
      <div class="logo">科技成果采集</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表盘</span>
        </el-menu-item>
        <el-menu-item index="/sources">
          <el-icon><Collection /></el-icon>
          <span>数据源</span>
        </el-menu-item>
        <el-menu-item index="/achievements">
          <el-icon><Document /></el-icon>
          <span>成果列表</span>
        </el-menu-item>
        <el-menu-item index="/links">
          <el-icon><Filter /></el-icon>
          <span>链接筛选</span>
        </el-menu-item>
        <el-menu-item index="/tasks">
          <el-icon><List /></el-icon>
          <span>任务管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app { height: 100%; }

.layout { height: 100vh; }

.aside {
  background: #304156;
  overflow-y: auto;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 16px;
  font-weight: bold;
  border-bottom: 1px solid #3d4d5e;
}

.menu {
  border-right: none;
  background: #304156;
}

.menu .el-menu-item {
  color: #bfcbd9;
}

.menu .el-menu-item:hover,
.menu .el-menu-item.is-active {
  background: #263445;
  color: #409eff;
}

.main {
  background: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}
</style>
