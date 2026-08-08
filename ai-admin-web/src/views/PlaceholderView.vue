<template>
  <el-card>
    <h2>{{ title }}</h2>
    <p class="desc">本期未实现，规划于 {{ phase }} 交付。详见 docs/admin-design.md §10。</p>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const title = computed(() => (route.meta.title as string) || '功能占位')
const phase = computed(() => (route.meta.phase as string) || '后续分期')
</script>

<style scoped>
h2 {
  margin: 0 0 8px;
}
.desc {
  margin: 0;
  color: #6b7280;
}
</style>
