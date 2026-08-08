<template>
  <el-card v-loading="loading">
    <div class="toolbar">
      <el-button @click="router.push('/kb')">返回列表</el-button>
      <el-button @click="load">刷新</el-button>
      <span v-if="document" class="meta">
        {{ document.title }}（{{ document.doc_id }} / {{ document.collection }}）
      </span>
    </div>

    <h3>版本</h3>
    <el-table
      :data="versions"
      stripe
      highlight-current-row
      @current-change="onSelectVersion"
    >
      <el-table-column prop="version" label="版本" width="90" />
      <el-table-column prop="status" label="状态" width="120" />
      <el-table-column prop="source" label="来源" min-width="140" />
      <el-table-column label="ingest_options" min-width="220">
        <template #default="{ row }">
          <code class="opts">{{ formatOptions(row.ingest_options) }}</code>
        </template>
      </el-table-column>
      <el-table-column prop="published_at" label="发布时间" min-width="160" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            link
            type="primary"
            :disabled="row.status !== 'ACTIVE'"
            @click="onRebuildEmbedding(row)"
          >重建向量</el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status !== 'ACTIVE'"
            @click="onDeprecate(row)"
          >停用</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="chunk-head">
      <h3>Chunks{{ selectedVersion ? ` · ${selectedVersion.version}` : '' }}</h3>
      <div class="chunk-actions">
        <el-button :disabled="!selectedVersion" @click="loadEmbeddingInfo">Embedding 指纹</el-button>
        <el-button type="primary" :disabled="!selectedVersion" @click="openCreateChunk">新增 Chunk</el-button>
      </div>
    </div>

    <el-table :data="chunks" v-loading="chunkLoading" stripe>
      <el-table-column prop="chunkSeq" label="序号" width="80" sortable />
      <el-table-column prop="currentRevision" label="Rev" width="70" />
      <el-table-column prop="ingestedAt" label="生效时间" min-width="180" sortable />
      <el-table-column prop="chunkKey" label="chunkKey" min-width="160" />
      <el-table-column prop="section" label="章节" width="120" />
      <el-table-column prop="summary" label="摘要" min-width="180" show-overflow-tooltip />
      <el-table-column prop="contentHash" label="hash" min-width="120" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEditChunk(row)">编辑</el-button>
          <el-button link type="primary" @click="openRevisions(row)">历史</el-button>
          <el-button link type="danger" @click="onDeleteChunk(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="chunkVisible" :title="chunkEditing ? '编辑 Chunk' : '新增 Chunk'" width="640px">
      <el-form label-width="80px">
        <el-form-item label="章节"><el-input v-model="chunkForm.section" /></el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="chunkForm.content" type="textarea" :rows="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="chunkVisible = false">取消</el-button>
        <el-button type="primary" :loading="chunkSaving" @click="submitChunk">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="revisionVisible" title="Chunk Revisions" width="720px">
      <el-table :data="revisions" v-loading="revisionLoading" stripe>
        <el-table-column prop="revision" label="Rev" width="70" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="contentHash" label="hash" min-width="140" show-overflow-tooltip />
        <el-table-column prop="createTime" label="时间" min-width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="row.status === 'ACTIVE'"
              @click="onRollback(row)"
            >回滚</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="embeddingVisible" title="版本 Embedding" width="560px">
      <el-descriptions v-if="embeddingInfo" :column="1" border>
        <el-descriptions-item label="configId">
          {{ embeddingInfo.embeddingConfigId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="modelVersion">
          {{ embeddingInfo.embeddingModelVersion || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="已嵌入 ACTIVE chunk">
          {{ embeddingInfo.embeddedChunkCount }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="embeddingVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createChunk,
  deleteChunk,
  deprecateVersion,
  getDocument,
  getVersionEmbedding,
  listChunks,
  listRevisions,
  rebuildEmbedding,
  rollbackRevision,
  updateChunk,
  type KbChunkRevisionView,
  type KbChunkView,
  type KbVersionEmbeddingView,
} from '@/api/kb'

const route = useRoute()
const router = useRouter()
const docId = String(route.params.docId)

const loading = ref(false)
const chunkLoading = ref(false)
const chunkSaving = ref(false)
const document = ref<Record<string, unknown> | null>(null)
const versions = ref<Record<string, unknown>[]>([])
const selectedVersion = ref<Record<string, unknown> | null>(null)
const chunks = ref<KbChunkView[]>([])
const chunkVisible = ref(false)
const chunkEditing = ref(false)
const editingChunkId = ref('')

const revisionVisible = ref(false)
const revisionLoading = ref(false)
const revisions = ref<KbChunkRevisionView[]>([])
const revisionChunkId = ref('')

const embeddingVisible = ref(false)
const embeddingInfo = ref<KbVersionEmbeddingView | null>(null)

const chunkForm = reactive({
  content: '',
  section: '',
})

function formatOptions(raw: unknown) {
  if (raw == null) return '-'
  if (typeof raw === 'string') return raw
  try {
    return JSON.stringify(raw)
  } catch {
    return String(raw)
  }
}

async function load() {
  loading.value = true
  try {
    const data = await getDocument(docId)
    document.value = data.document
    versions.value = data.versions || []
    const active = versions.value.find((v) => v.status === 'ACTIVE') || versions.value[0]
    if (active) {
      selectedVersion.value = active
      await loadChunks()
    } else {
      selectedVersion.value = null
      chunks.value = []
    }
  } finally {
    loading.value = false
  }
}

function versionIdOf(row: Record<string, unknown> | null | undefined): string {
  if (!row || row.id == null) return ''
  return String(row.id)
}

async function loadChunks() {
  if (!selectedVersion.value) return
  const versionId = versionIdOf(selectedVersion.value)
  if (!versionId) return
  chunkLoading.value = true
  try {
    chunks.value = await listChunks(docId, versionId)
  } finally {
    chunkLoading.value = false
  }
}

async function onSelectVersion(row: Record<string, unknown> | null) {
  selectedVersion.value = row
  if (row) await loadChunks()
  else chunks.value = []
}

async function onDeprecate(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`确认停用版本 ${row.version}？`, '提示', { type: 'warning' })
  await deprecateVersion(docId, versionIdOf(row))
  ElMessage.success('已停用')
  await load()
}

async function onRebuildEmbedding(row: Record<string, unknown>) {
  await ElMessageBox.confirm(`确认按当前 Embedding 配置原地重建版本 ${row.version} 的向量？`, '提示', {
    type: 'warning',
  })
  const view = await rebuildEmbedding(docId, versionIdOf(row))
  ElMessage.success(`已重建（嵌入 ${view.embeddedChunkCount} 个 chunk）`)
}

async function loadEmbeddingInfo() {
  if (!selectedVersion.value) return
  embeddingInfo.value = await getVersionEmbedding(docId, versionIdOf(selectedVersion.value))
  embeddingVisible.value = true
}

function openCreateChunk() {
  chunkEditing.value = false
  editingChunkId.value = ''
  chunkForm.content = ''
  chunkForm.section = ''
  chunkVisible.value = true
}

function openEditChunk(row: KbChunkView) {
  chunkEditing.value = true
  editingChunkId.value = row.id
  chunkForm.content = row.content
  chunkForm.section = row.section || ''
  chunkVisible.value = true
}

async function openRevisions(row: KbChunkView) {
  revisionChunkId.value = row.id
  revisionVisible.value = true
  revisionLoading.value = true
  try {
    revisions.value = await listRevisions(row.id)
  } finally {
    revisionLoading.value = false
  }
}

async function onRollback(row: KbChunkRevisionView) {
  await ElMessageBox.confirm(`确认回滚到 revision ${row.revision}？`, '提示', { type: 'warning' })
  await rollbackRevision(revisionChunkId.value, row.revision)
  ElMessage.success('已回滚')
  revisions.value = await listRevisions(revisionChunkId.value)
  await loadChunks()
}

async function submitChunk() {
  if (!chunkForm.content.trim()) {
    ElMessage.warning('正文不能为空')
    return
  }
  if (!selectedVersion.value) return
  chunkSaving.value = true
  try {
    if (chunkEditing.value) {
      await updateChunk(editingChunkId.value, {
        content: chunkForm.content,
        section: chunkForm.section,
      })
      ElMessage.success('已更新（新 revision）')
    } else {
      await createChunk(docId, versionIdOf(selectedVersion.value), {
        content: chunkForm.content,
        section: chunkForm.section,
      })
      ElMessage.success('已新增')
    }
    chunkVisible.value = false
    await loadChunks()
  } finally {
    chunkSaving.value = false
  }
}

async function onDeleteChunk(row: KbChunkView) {
  await ElMessageBox.confirm(`确认停用 ${row.chunkKey}？revision 历史保留。`, '提示', {
    type: 'warning',
  })
  await deleteChunk(row.id)
  ElMessage.success('已停用')
  await loadChunks()
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.meta {
  margin-left: 8px;
  color: #6b7280;
}
.chunk-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24px;
}
.chunk-actions {
  display: flex;
  gap: 8px;
}
.opts {
  font-size: 12px;
  color: #4b5563;
  word-break: break-all;
}
</style>
