<template>
  <div class="prompts">
    <el-card class="list-card">
      <div class="toolbar">
        <el-button type="primary" @click="openCreateCode">新建 code / 版本</el-button>
        <el-button @click="loadSummaries">刷新</el-button>
      </div>
      <el-table
        :data="summaries"
        v-loading="loadingList"
        stripe
        highlight-current-row
        @current-change="onSelect"
      >
        <el-table-column prop="code" label="code" min-width="180" />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column prop="role" label="role" width="90" />
        <el-table-column prop="publishedVersion" label="已发布" width="80" />
        <el-table-column label="草稿" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.hasDraft" type="warning" size="small">v{{ row.draftVersion }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">
              {{ row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="detail-card">
      <template #header>
        <div class="detail-header">
          <span>{{ selectedCode || '选择左侧 code 查看版本' }}</span>
          <el-button v-if="selectedCode" type="primary" @click="openNewVersion">新建版本</el-button>
        </div>
      </template>
      <el-table :data="versions" v-loading="loadingVersions" stripe empty-text="暂无版本">
        <el-table-column prop="version" label="版本" width="70" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="changeNote" label="改动说明" min-width="150" show-overflow-tooltip />
        <el-table-column prop="createdBy" label="创建者" width="100" />
        <el-table-column label="创建/发布时间" width="160">
          <template #default="{ row }">
            <div class="time-info">
              <span>C: {{ formatTime(row.createTime) }}</span>
              <span v-if="row.publishTime">P: {{ formatTime(row.publishTime) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容摘要" min-width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showContent(row)">查看</el-button>
            <el-button
              v-if="row.status === 'DRAFT'"
              link
              type="success"
              @click="onPublish(row)"
            >发布</el-button>
            <el-button
              v-if="row.status === 'SUPERSEDED'"
              link
              type="warning"
              @click="onRollback(row)"
            >回滚</el-button>
            <el-button link type="info" @click="openDiff(row)">对比</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="createVisible" :title="createTitle" width="640px">
      <el-form label-width="90px">
        <el-form-item v-if="creatingNewCode" label="code">
          <el-input v-model="versionForm.code" placeholder="如 agent.default.system" />
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="versionForm.name" /></el-form-item>
        <el-form-item label="role">
          <el-select v-model="versionForm.role" style="width: 100%">
            <el-option label="SYSTEM" value="SYSTEM" />
            <el-option label="USER" value="USER" />
          </el-select>
        </el-form-item>
        <el-form-item label="改动说明">
          <el-input v-model="versionForm.changeNote" placeholder="本次版本的改动内容..." />
        </el-form-item>
        <el-form-item label="content">
          <el-input v-model="versionForm.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="submitVersion(false)">保存草稿</el-button>
        <el-button type="primary" :loading="saving" @click="submitVersion(true)">保存并发布</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="diffVisible" title="版本对比" width="800px" top="5vh">
      <div class="diff-header">
        <div class="diff-selector">
          <span>对比基准 (From): v{{ diffFrom?.version }}</span>
          <span class="mx-4">对比目标 (To):</span>
          <el-select v-model="diffToVersion" size="small" @change="loadDiff">
            <el-option
              v-for="v in versions.filter(v => v.version !== diffFrom?.version)"
              :key="v.version"
              :label="`v${v.version} (${v.status})`"
              :value="v.version"
            />
          </el-select>
        </div>
      </div>
      <div v-loading="loadingDiff" class="diff-container">
        <div v-if="diffData" class="diff-content">
          <div class="diff-side">
            <div class="diff-side-title">v{{ diffData.from.version }}</div>
            <div class="diff-pre-wrapper">
              <div v-for="(line, idx) in diffData.from.content.split('\n')" :key="idx" class="diff-line">
                <span class="line-num">{{ idx + 1 }}</span>
                <span class="line-content">{{ line || ' ' }}</span>
              </div>
            </div>
          </div>
          <div class="diff-side">
            <div class="diff-side-title">v{{ diffData.to.version }}</div>
            <div class="diff-pre-wrapper">
              <div v-for="(line, idx) in diffData.to.content.split('\n')" :key="idx" class="diff-line">
                <span class="line-num">{{ idx + 1 }}</span>
                <span class="line-content">{{ line || ' ' }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="!loadingDiff" class="diff-empty">请选择对比版本</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as promptsApi from '@/api/prompts'
import type { AdminPromptSummary, AdminPromptVersionView, AdminPromptDiffView } from '@/api/prompts'

const loadingList = ref(false)
const loadingVersions = ref(false)
const saving = ref(false)
const summaries = ref<AdminPromptSummary[]>([])
const versions = ref<AdminPromptVersionView[]>([])
const selectedCode = ref('')
const createVisible = ref(false)
const creatingNewCode = ref(false)

const diffVisible = ref(false)
const loadingDiff = ref(false)
const diffFrom = ref<AdminPromptVersionView | null>(null)
const diffToVersion = ref<number | null>(null)
const diffData = ref<AdminPromptDiffView | null>(null)

const versionForm = reactive({
  code: '',
  name: '',
  role: 'SYSTEM',
  content: '',
  changeNote: '',
})

const createTitle = computed(() =>
  creatingNewCode.value ? '新建提示词 code' : `新建版本 · ${selectedCode.value}`,
)

function formatTime(time: string | null) {
  if (!time) return '-'
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).replace(/\//g, '-')
}

function getStatusTagType(status: string) {
  switch (status) {
    case 'PUBLISHED': return 'success'
    case 'DRAFT': return 'warning'
    case 'SUPERSEDED': return 'info'
    default: return ''
  }
}

async function loadSummaries() {
  loadingList.value = true
  try {
    summaries.value = await promptsApi.listPromptSummaries()
  } finally {
    loadingList.value = false
  }
}

async function loadVersions(code: string) {
  if (!code) {
    versions.value = []
    return
  }
  loadingVersions.value = true
  try {
    versions.value = await promptsApi.listPromptVersions(code)
  } finally {
    loadingVersions.value = false
  }
}

function onSelect(row: AdminPromptSummary | undefined) {
  selectedCode.value = row?.code || ''
  void loadVersions(selectedCode.value)
}

function openCreateCode() {
  creatingNewCode.value = true
  versionForm.code = ''
  versionForm.name = ''
  versionForm.role = 'SYSTEM'
  versionForm.content = ''
  versionForm.changeNote = ''
  createVisible.value = true
}

function openNewVersion() {
  creatingNewCode.value = false
  versionForm.code = selectedCode.value
  const latest = versions.value[0]
  versionForm.name = latest?.name || selectedCode.value
  versionForm.role = latest?.role || 'SYSTEM'
  versionForm.content = latest?.content || ''
  versionForm.changeNote = ''
  createVisible.value = true
}

async function submitVersion(publish: boolean) {
  const code = creatingNewCode.value ? versionForm.code.trim() : selectedCode.value
  if (!code) {
    ElMessage.warning('请填写 code')
    return
  }
  saving.value = true
  try {
    await promptsApi.createPromptVersion(code, {
      name: versionForm.name,
      role: versionForm.role,
      content: versionForm.content,
      changeNote: versionForm.changeNote,
      publish,
    })
    ElMessage.success(publish ? '已发布' : '草稿已保存')
    createVisible.value = false
    await loadSummaries()
    selectedCode.value = code
    await loadVersions(code)
  } finally {
    saving.value = false
  }
}

async function onPublish(row: AdminPromptVersionView) {
  await ElMessageBox.confirm(`发布 ${row.code} v${row.version}？`, '提示', { type: 'warning' })
  await promptsApi.publishPromptVersion(row.code, row.version)
  ElMessage.success('已发布')
  await loadSummaries()
  await loadVersions(row.code)
}

async function onRollback(row: AdminPromptVersionView) {
  await ElMessageBox.confirm(
    `回滚到 ${row.code} v${row.version}？将沿用该版本号设为当前发布版。`,
    '提示',
    { type: 'warning' },
  )
  await promptsApi.rollbackPromptVersion(row.code, row.version)
  ElMessage.success('已回滚')
  await loadSummaries()
  await loadVersions(row.code)
}

async function showContent(row: AdminPromptVersionView) {
  await ElMessageBox.alert(row.content, `${row.code} @ v${row.version}`, {
    confirmButtonText: '关闭',
    customClass: 'prompt-content-box',
  })
}

function openDiff(row: AdminPromptVersionView) {
  diffFrom.value = row
  diffToVersion.value = null
  diffData.value = null
  diffVisible.value = true
  
  // Auto-select published version as target if it exists and is different
  const summary = summaries.value.find(s => s.code === selectedCode.value)
  if (summary?.publishedVersion && summary.publishedVersion !== row.version) {
    diffToVersion.value = summary.publishedVersion
    void loadDiff()
  }
}

async function loadDiff() {
  if (!diffFrom.value || !diffToVersion.value) return
  loadingDiff.value = true
  try {
    diffData.value = await promptsApi.diffPromptVersions(
      selectedCode.value,
      diffFrom.value.version,
      diffToVersion.value,
    )
  } finally {
    loadingDiff.value = false
  }
}

onMounted(() => {
  void loadSummaries()
})
</script>

<style scoped>
.prompts {
  display: grid;
  grid-template-columns: minmax(360px, 1fr) minmax(420px, 1.4fr);
  gap: 16px;
  align-items: start;
}
.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.time-info {
  display: flex;
  flex-direction: column;
  font-size: 12px;
  color: #999;
}
.diff-header {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}
.diff-container {
  min-height: 300px;
}
.diff-content {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.diff-side-title {
  font-weight: bold;
  margin-bottom: 8px;
  text-align: center;
  background: #f5f7fa;
  padding: 4px;
}
.diff-pre-wrapper {
  margin: 0;
  padding: 8px 0;
  background: #f8f9fa;
  border: 1px solid #e4e7ed;
  font-family: monospace;
  max-height: 60vh;
  overflow-y: auto;
}
.diff-line {
  display: flex;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.diff-line:hover {
  background-color: #f0f2f5;
}
.line-num {
  width: 40px;
  text-align: right;
  padding-right: 8px;
  color: #999;
  user-select: none;
  border-right: 1px solid #eee;
  margin-right: 8px;
  flex-shrink: 0;
}
.line-content {
  flex: 1;
  padding-right: 8px;
}
.diff-empty {
  text-align: center;
  padding: 40px;
  color: #999;
}
.mx-4 {
  margin-left: 16px;
  margin-right: 16px;
}
@media (max-width: 1200px) {
  .prompts {
    grid-template-columns: 1fr;
  }
}
</style>
