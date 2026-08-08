<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="filters.adminId" placeholder="管理员 ID" style="width: 140px" clearable @change="reload" />
      <el-input v-model="filters.action" placeholder="动作" style="width: 140px" clearable @change="reload" />
      <el-select
        v-model="filters.resourceType"
        placeholder="资源类型"
        style="width: 180px"
        clearable
        @change="reload"
      >
        <el-option v-for="t in resourceTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-input
        v-model="filters.resourceId"
        placeholder="资源 ID"
        style="width: 160px"
        clearable
        @change="reload"
      />
      <el-date-picker
        v-model="timeRange"
        type="datetimerange"
        range-separator="至"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
        style="width: 360px"
        @change="onTimeRangeChange"
      />
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe @row-click="openDetail">
      <el-table-column prop="id" label="ID" min-width="120" show-overflow-tooltip />
      <el-table-column label="管理员" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.adminUsername || row.adminId }}
        </template>
      </el-table-column>
      <el-table-column prop="action" label="动作" width="140" show-overflow-tooltip />
      <el-table-column prop="resourceType" label="资源类型" width="160" show-overflow-tooltip />
      <el-table-column prop="resourceId" label="资源 ID" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.createTime) }}
        </template>
      </el-table-column>
    </el-table>

    <div class="pager">
      <el-pagination
        background
        layout="total, prev, pager, next"
        :total="total"
        :page-size="size"
        :current-page="page"
        @current-change="onPage"
      />
    </div>

    <el-drawer v-model="detailVisible" title="操作详情" size="60%">
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
          <el-descriptions-item label="管理员 ID">{{ detail.adminId }}</el-descriptions-item>
          <el-descriptions-item label="用户名">{{ detail.adminUsername || '-' }}</el-descriptions-item>
          <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
          <el-descriptions-item label="资源类型">{{ detail.resourceType }}</el-descriptions-item>
          <el-descriptions-item label="资源 ID">{{ detail.resourceId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="时间" :span="2">{{ formatTime(detail.createTime) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="section-title">字段变更</h4>
        <el-table :data="changes" stripe empty-text="无变更记录" size="small">
          <el-table-column prop="field" label="字段" width="160" />
          <el-table-column label="变更前" min-width="160">
            <template #default="{ row }">
              {{ formatValue(row.from) }}
            </template>
          </el-table-column>
          <el-table-column label="变更后" min-width="160">
            <template #default="{ row }">
              {{ formatValue(row.to) }}
            </template>
          </el-table-column>
        </el-table>

        <h4 class="section-title">Meta</h4>
        <pre class="json-block">{{ formatJson(detail.detail?.meta ?? null) }}</pre>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import * as auditLogsApi from '@/api/auditLogs'
import type {
  AdminAuditLogSummary,
  AdminAuditLogDetail,
  AdminAuditLogQueryParams,
  AdminAuditChange,
} from '@/api/auditLogs'

const resourceTypes = [
  'admin_user',
  'llm_config',
  'PROMPT_TEMPLATE',
  'KB_DOCUMENT',
  'KB_CHUNK',
  'USER_PROFILE',
  'USER_SEMANTIC',
  'MCP_TOOL_BINDING',
]

const loading = ref(false)
const rows = ref<AdminAuditLogSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive<AdminAuditLogQueryParams>({
  adminId: '',
  action: '',
  resourceType: '',
  resourceId: '',
  createTimeFrom: '',
  createTimeTo: '',
})

const timeRange = ref<[Date, Date] | null>(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminAuditLogDetail | null>(null)

const changes = computed<AdminAuditChange[]>(() => detail.value?.detail?.changes ?? [])

async function load() {
  loading.value = true
  try {
    const data = await auditLogsApi.listAuditLogs({
      ...filters,
      page: page.value,
      size: size.value,
    })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function reload() {
  page.value = 1
  void load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

function onTimeRangeChange(val: [Date, Date] | null) {
  if (val) {
    filters.createTimeFrom = val[0].toISOString()
    filters.createTimeTo = val[1].toISOString()
  } else {
    filters.createTimeFrom = ''
    filters.createTimeTo = ''
  }
  reload()
}

async function openDetail(row: AdminAuditLogSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await auditLogsApi.getAuditLog(row.id)
  } finally {
    detailLoading.value = false
  }
}

function formatTime(t: string | null | undefined) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatValue(v: unknown) {
  if (v === null || v === undefined) return '-'
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

function formatJson(obj: unknown) {
  if (obj === null || obj === undefined) return '（无）'
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

onMounted(() => {
  void load()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
.section-title {
  margin: 20px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.json-block {
  background: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
  font-size: 12px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
