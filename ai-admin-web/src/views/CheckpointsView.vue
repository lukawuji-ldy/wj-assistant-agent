<template>
  <div class="checkpoints-container">
    <div class="pane left-pane">
      <div class="pane-header filters">
        <el-input v-model="threadFilters.threadName" placeholder="threadName" clearable @change="reloadThreads" />
        <el-input v-model="threadFilters.userId" placeholder="userId" clearable @change="reloadThreads" />
        <el-input
          v-model="threadFilters.conversationId"
          placeholder="conversationId"
          clearable
          @change="reloadThreads"
        />
        <div class="filter-row">
          <el-select
            v-model="threadFilters.isReleased"
            placeholder="released"
            clearable
            style="flex: 1"
            @change="reloadThreads"
          >
            <el-option label="已释放" :value="true" />
            <el-option label="未释放" :value="false" />
          </el-select>
          <el-button @click="loadThreads">刷新</el-button>
        </div>
      </div>
      <div class="pane-content" v-loading="threadsLoading">
        <div
          v-for="t in threads"
          :key="t.threadId"
          class="thread-item"
          :class="{ active: selectedThreadId === t.threadId }"
          @click="selectThread(t.threadId)"
        >
          <div class="thread-title">{{ t.threadName || t.threadId }}</div>
          <div class="thread-meta">
            <span>{{ t.checkpointCount }} 步</span>
            <span>{{ formatTime(t.lastSavedAt) }}</span>
          </div>
          <div class="thread-submeta">
            {{ t.userId || '-' }} · {{ t.conversationId || '-' }}
            <el-tag v-if="t.isReleased" size="small" type="info">released</el-tag>
          </div>
        </div>
        <div class="pager">
          <el-pagination
            small
            layout="prev, next"
            :total="threadsTotal"
            :page-size="threadPageSize"
            :current-page="threadPage"
            @current-change="onThreadPage"
          />
        </div>
      </div>
    </div>

    <div class="pane flow-pane" v-loading="stepsLoading">
      <div class="pane-header">
        <span v-if="selectedThread">执行流程 · {{ selectedThread.threadName }}</span>
        <span v-else>选择左侧线程查看执行流程</span>
        <span v-if="steps.length" class="step-count">共 {{ steps.length }} 步</span>
      </div>
      <div class="pane-content timeline-content">
        <el-empty v-if="!selectedThreadId && !stepsLoading" description="尚未选择线程" />
        <el-empty v-else-if="!steps.length && !stepsLoading" description="该线程暂无 Checkpoint" />
        <div v-else class="timeline-grid">
          <div
            v-for="(step, idx) in steps"
            :key="step.checkpointId"
            class="timeline-item"
            @click="openStepDetail(step)"
          >
            <div class="timeline-axis">
              <div class="axis-line" v-if="idx < steps.length - 1"></div>
              <div class="axis-dot" :class="getStepType(idx, step)"></div>
            </div>
            <div class="step-card">
              <div class="step-header">
                <span class="step-index">#{{ step.stepIndex }}</span>
                <span class="step-node">{{ step.nodeId || '(unnamed)' }}</span>
                <span class="step-arrow">→</span>
                <span class="step-next-node">{{ step.nextNodeId || 'END' }}</span>
                <el-button class="detail-btn" link type="primary" @click.stop="openStepDetail(step)">
                  查看明细
                </el-button>
              </div>
              <div class="step-meta">
                <span>{{ formatTime(step.savedAt) }}</span>
                <span v-if="step.deltaMs != null" class="delta">+{{ step.deltaMs }}ms</span>
                <span v-else class="delta muted">起点</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <el-dialog
      v-model="detailVisible"
      :title="detailTitle"
      width="900px"
      top="5vh"
      destroy-on-close
      class="checkpoint-detail-dialog"
    >
      <div v-loading="stepDetailLoading">
        <template v-if="stepDetail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="checkpointId" :span="2">
              {{ stepDetail.checkpointId }}
            </el-descriptions-item>
            <el-descriptions-item label="parent">
              {{ stepDetail.parentCheckpointId || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="savedAt">
              {{ formatTime(stepDetail.savedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="nodeId">{{ stepDetail.nodeId || '-' }}</el-descriptions-item>
            <el-descriptions-item label="nextNodeId">
              {{ stepDetail.nextNodeId || 'END' }}
            </el-descriptions-item>
            <el-descriptions-item label="contentType">
              {{ stepDetail.stateContentType || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="threadId">{{ stepDetail.threadId }}</el-descriptions-item>
          </el-descriptions>

          <el-alert
            v-if="stepDetail.decodeError"
            :title="stepDetail.decodeError"
            type="warning"
            show-icon
            :closable="false"
            style="margin-top: 16px"
          />

          <div class="section">
            <div class="section-title">
              <span>Messages（{{ stepDetail.messages?.length || 0 }}）</span>
            </div>
            <el-table
              :data="stepDetail.messages || []"
              border
              stripe
              size="small"
              empty-text="无消息"
              max-height="320"
            >
              <el-table-column prop="index" label="#" width="50" />
              <el-table-column prop="role" label="角色" width="110">
                <template #default="{ row }">
                  <el-tag :type="roleTag(row.role)" size="small">{{ row.role }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="内容" min-width="280">
                <template #default="{ row }">
                  <pre class="cell-pre">{{ row.content || '-' }}</pre>
                </template>
              </el-table-column>
              <el-table-column label="Tool Calls" min-width="180">
                <template #default="{ row }">
                  <pre class="cell-pre">{{ row.toolCallsJson || '-' }}</pre>
                </template>
              </el-table-column>
              <el-table-column label="Tool Responses" min-width="180">
                <template #default="{ row }">
                  <pre class="cell-pre">{{ row.toolResponsesJson || '-' }}</pre>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="section">
            <div class="section-title">
              <span>State 字段</span>
              <el-button
                size="small"
                :disabled="!stepDetail.decodedState"
                @click="copyJson(stepDetail.decodedState)"
              >
                复制解码 JSON
              </el-button>
            </div>
            <el-table
              :data="stepDetail.stateEntries || []"
              border
              stripe
              size="small"
              empty-text="无其它字段"
              max-height="280"
            >
              <el-table-column prop="key" label="Key" width="180" />
              <el-table-column prop="type" label="类型" width="90" />
              <el-table-column prop="summary" label="摘要 / 值" min-width="280">
                <template #default="{ row }">
                  <pre class="cell-pre">{{ row.summary }}</pre>
                </template>
              </el-table-column>
              <el-table-column label="详情" width="90">
                <template #default="{ row }">
                  <el-button
                    v-if="row.value != null && row.key !== 'messages'"
                    link
                    type="primary"
                    @click="showEntryValue(row)"
                  >
                    查看
                  </el-button>
                  <span v-else>-</span>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </div>
    </el-dialog>

    <el-dialog v-model="entryVisible" :title="entryTitle" width="640px" append-to-body>
      <pre class="json-block">{{ formatJson(entryValue) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as checkpointsApi from '@/api/checkpoints'
import type {
  AdminCheckpointThreadSummary,
  AdminCheckpointStepSummary,
  AdminCheckpointDetail,
  AdminCheckpointStateEntry,
  AdminCheckpointThreadQueryParams,
} from '@/api/checkpoints'

const threadsLoading = ref(false)
const threads = ref<AdminCheckpointThreadSummary[]>([])
const threadsTotal = ref(0)
const threadPage = ref(1)
const threadPageSize = ref(10)
const selectedThreadId = ref('')
const selectedThread = ref<AdminCheckpointThreadSummary | null>(null)
const threadFilters = reactive<AdminCheckpointThreadQueryParams>({
  threadName: '',
  userId: '',
  conversationId: '',
  isReleased: undefined,
})

const stepsLoading = ref(false)
const steps = ref<AdminCheckpointStepSummary[]>([])

const detailVisible = ref(false)
const stepDetailLoading = ref(false)
const stepDetail = ref<AdminCheckpointDetail | null>(null)
const activeStep = ref<AdminCheckpointStepSummary | null>(null)

const entryVisible = ref(false)
const entryTitle = ref('')
const entryValue = ref<unknown>(null)

const detailTitle = computed(() => {
  if (!activeStep.value) return '步骤明细'
  return `步骤明细 · #${activeStep.value.stepIndex} · ${activeStep.value.nodeId || ''}`
})

function cleanParams(params: AdminCheckpointThreadQueryParams): AdminCheckpointThreadQueryParams {
  const out: AdminCheckpointThreadQueryParams = { ...params }
  if (!out.threadName) delete out.threadName
  if (!out.userId) delete out.userId
  if (!out.conversationId) delete out.conversationId
  if (out.isReleased === undefined || out.isReleased === null) delete out.isReleased
  return out
}

async function loadThreads() {
  threadsLoading.value = true
  try {
    const data = await checkpointsApi.listCheckpointThreads({
      ...cleanParams(threadFilters),
      page: threadPage.value,
      size: threadPageSize.value,
    })
    threads.value = data.items
    threadsTotal.value = data.total
  } finally {
    threadsLoading.value = false
  }
}

function reloadThreads() {
  threadPage.value = 1
  void loadThreads()
}

function onThreadPage(p: number) {
  threadPage.value = p
  void loadThreads()
}

async function selectThread(id: string) {
  selectedThreadId.value = id
  selectedThread.value = threads.value.find((t) => t.threadId === id) || null
  stepsLoading.value = true
  steps.value = []
  try {
    const data = await checkpointsApi.getCheckpointThread(id)
    steps.value = data.steps
  } finally {
    stepsLoading.value = false
  }
}

async function openStepDetail(step: AdminCheckpointStepSummary) {
  activeStep.value = step
  detailVisible.value = true
  stepDetailLoading.value = true
  stepDetail.value = null
  try {
    stepDetail.value = await checkpointsApi.getCheckpoint(step.checkpointId)
  } finally {
    stepDetailLoading.value = false
  }
}

function showEntryValue(row: AdminCheckpointStateEntry) {
  entryTitle.value = `State · ${row.key}`
  entryValue.value = row.value
  entryVisible.value = true
}

function getStepType(idx: number, step: AdminCheckpointStepSummary) {
  if (idx === 0) return 'start'
  if (!step.nextNodeId || idx === steps.value.length - 1) return 'end'
  return 'mid'
}

function roleTag(role: string) {
  if (role === 'USER') return 'primary'
  if (role === 'ASSISTANT') return 'success'
  if (role === 'TOOL') return 'warning'
  if (role === 'SYSTEM') return 'info'
  return ''
}

function formatTime(t: string | null | undefined) {
  if (!t) return '-'
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatJson(obj: unknown) {
  if (obj == null) return ''
  try {
    return JSON.stringify(obj, null, 2)
  } catch {
    return String(obj)
  }
}

async function copyJson(obj: unknown) {
  try {
    await navigator.clipboard.writeText(JSON.stringify(obj, null, 2))
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

onMounted(() => {
  void loadThreads()
})
</script>

<style scoped>
.checkpoints-container {
  --timeline-start: #10b981;
  --timeline-mid: #3b82f6;
  --timeline-end: #6366f1;
  display: flex;
  height: calc(100vh - 120px);
  gap: 16px;
  background-color: #f3f4f6;
  padding: 16px;
}

.pane {
  background: white;
  display: flex;
  flex-direction: column;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.pane-header {
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
  font-weight: 600;
  color: #374151;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.pane-header.filters {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
  font-weight: 400;
}

.filter-row {
  display: flex;
  gap: 8px;
}

.pane-content {
  flex: 1;
  overflow-y: auto;
}

.left-pane {
  flex: 0 0 300px;
}

.flow-pane {
  flex: 1;
  min-width: 0;
}

.step-count {
  font-weight: 400;
  font-size: 13px;
  color: #6b7280;
}

.thread-item {
  padding: 12px 16px;
  border-bottom: 1px solid #f3f4f6;
  cursor: pointer;
  transition: background 0.2s;
}

.thread-item:hover {
  background: #f9fafb;
}

.thread-item.active {
  background: #eff6ff;
  border-left: 4px solid #3b82f6;
}

.thread-title {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 4px;
  word-break: break-all;
}

.thread-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}

.thread-submeta {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 4px;
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.timeline-content {
  padding: 20px 28px;
}

.timeline-grid {
  max-width: 960px;
}

.timeline-item {
  display: flex;
  gap: 16px;
  cursor: pointer;
}

.timeline-axis {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 24px;
}

.axis-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #d1d5db;
  z-index: 1;
  margin-top: 4px;
}

.axis-dot.start {
  background: var(--timeline-start);
}
.axis-dot.mid {
  background: var(--timeline-mid);
}
.axis-dot.end {
  background: var(--timeline-end);
}

.axis-line {
  width: 2px;
  flex: 1;
  background: #e5e7eb;
}

.step-card {
  flex: 1;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 14px;
  transition: all 0.2s;
}

.timeline-item:hover .step-card {
  border-color: #3b82f6;
  background: #f8fbff;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.detail-btn {
  margin-left: auto;
}

.step-index {
  font-size: 12px;
  color: #9ca3af;
  font-family: monospace;
}

.step-node {
  font-weight: 600;
  color: #111827;
}

.step-arrow {
  color: #d1d5db;
}

.step-next-node {
  color: #4b5563;
}

.step-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #6b7280;
}

.delta {
  font-weight: 500;
  color: #10b981;
}

.delta.muted {
  color: #9ca3af;
  font-weight: 400;
}

.section {
  margin-top: 18px;
}

.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
  color: #374151;
}

.cell-pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.45;
  max-height: 140px;
  overflow: auto;
}

.json-block {
  background: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 60vh;
}

.pager {
  padding: 8px;
  border-top: 1px solid #f3f4f6;
  display: flex;
  justify-content: center;
}
</style>
