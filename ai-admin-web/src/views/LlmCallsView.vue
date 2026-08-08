<template>
  <el-card>
    <div class="toolbar">
      <el-input v-model="filters.userId" placeholder="用户 ID" style="width: 140px" clearable @change="reload" />
      <el-input v-model="filters.conversationId" placeholder="会话 ID" style="width: 140px" clearable @change="reload" />
      <el-input v-model="filters.callId" placeholder="调用 ID" style="width: 140px" clearable @change="reload" />
      <el-select v-model="filters.status" placeholder="状态" style="width: 120px" clearable @change="reload">
        <el-option label="SUCCESS" value="SUCCESS" />
        <el-option label="FAILED" value="FAILED" />
      </el-select>
      <el-select v-model="filters.provider" placeholder="提供商" style="width: 120px" clearable @change="reload">
        <el-option label="OpenAI" value="openai" />
        <el-option label="Aliyun" value="aliyun" />
        <el-option label="DeepSeek" value="deepseek" />
      </el-select>
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
      <el-table-column prop="callId" label="调用 ID" min-width="120" show-overflow-tooltip />
      <el-table-column prop="userId" label="用户" min-width="100" show-overflow-tooltip />
      <el-table-column prop="modelId" label="模型" min-width="120" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'SUCCESS' ? 'success' : 'danger'">
            {{ row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="latencyMs" label="耗时" width="100">
        <template #default="{ row }">
          {{ row.latencyMs }}ms
        </template>
      </el-table-column>
      <el-table-column label="Tokens (P/C)" width="120">
        <template #default="{ row }">
          {{ row.promptTokens }} / {{ row.completionTokens }}
        </template>
      </el-table-column>
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

    <el-drawer v-model="detailVisible" title="调用详情" size="60%">
      <div v-if="detail" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="Call ID">{{ detail.callId }}</el-descriptions-item>
          <el-descriptions-item label="Trace ID">{{ detail.traceId }}</el-descriptions-item>
          <el-descriptions-item label="Conversation ID">{{ detail.conversationId }}</el-descriptions-item>
          <el-descriptions-item label="Message ID">{{ detail.messageId }}</el-descriptions-item>
          <el-descriptions-item label="Model ID">{{ detail.modelId }}</el-descriptions-item>
          <el-descriptions-item label="Provider">{{ detail.provider }}</el-descriptions-item>
          <el-descriptions-item label="Status">{{ detail.status }}</el-descriptions-item>
          <el-descriptions-item label="Is Fallback">{{ detail.isFallback }}</el-descriptions-item>
          <el-descriptions-item label="Latency">{{ detail.latencyMs }}ms</el-descriptions-item>
          <el-descriptions-item label="Tokens">{{ detail.promptTokens }} (Prompt) / {{ detail.completionTokens }} (Completion)</el-descriptions-item>
          <el-descriptions-item label="Create Time" :span="2">{{ formatTime(detail.createTime) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.errorCode" label="Error Code" :span="2">
            <el-tag type="danger">{{ detail.errorCode }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-collapse v-model="activeCollapse" style="margin-top: 20px">
          <el-collapse-item title="Request JSON" name="request">
            <pre class="json-block">{{ formatJson(detail.requestJson) }}</pre>
          </el-collapse-item>
          <el-collapse-item title="Response JSON" name="response">
            <pre class="json-block">{{ formatJson(detail.responseJson) }}</pre>
          </el-collapse-item>
        </el-collapse>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as llmCallsApi from '@/api/llmCalls'
import type { AdminLlmCallSummary, AdminLlmCallDetail, AdminLlmCallQueryParams } from '@/api/llmCalls'

const loading = ref(false)
const rows = ref<AdminLlmCallSummary[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const filters = reactive<AdminLlmCallQueryParams>({
  userId: '',
  conversationId: '',
  callId: '',
  status: '',
  provider: '',
  createTimeFrom: '',
  createTimeTo: '',
})

const timeRange = ref<[Date, Date] | null>(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminLlmCallDetail | null>(null)
const activeCollapse = ref(['request', 'response'])

async function load() {
  loading.value = true
  try {
    const data = await llmCallsApi.listLlmCalls({
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

async function openDetail(row: AdminLlmCallSummary) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await llmCallsApi.getLlmCall(row.callId)
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

function formatJson(obj: any) {
  if (!obj) return ''
  try {
    return JSON.stringify(obj, null, 2)
  } catch (e) {
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
