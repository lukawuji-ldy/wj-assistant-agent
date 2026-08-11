<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="filterCollection"
        clearable
        placeholder="知识集合"
        style="width: 180px"
        @keyup.enter="reload"
      />
      <el-select v-model="filterStatus" clearable placeholder="当前版本状态" style="width: 160px" @change="reload">
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="DEPRECATED" value="DEPRECATED" />
      </el-select>
      <el-button type="primary" @click="openUpload">上传文档</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="doc_id" label="docId" min-width="160" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="collection" label="集合" width="120" />
      <el-table-column prop="current_version" label="当前版本" width="100" />
      <el-table-column prop="current_status" label="状态" width="110" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="goDetail(row)">详情</el-button>
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

    <el-dialog
      v-model="uploadVisible"
      title="上传知识库文档"
      width="960px"
      align-center
      :close-on-click-modal="false"
      @closed="resetUploadState"
    >
      <div class="upload-layout">
        <el-form label-width="120px" class="upload-form" @submit.prevent>
          <el-form-item label="文件" required>
            <!-- 不用 drag：隐藏 file input 可能盖住底栏，导致「确认入库」点了没请求 -->
            <el-upload
              :auto-upload="false"
              :limit="1"
              accept=".md,.txt,.markdown,.pdf"
              v-model:file-list="uploadFiles"
              :on-change="onFileChange"
              :on-remove="onFileRemove"
              :on-exceed="onFileExceed"
            >
              <el-button type="primary" plain native-type="button">选择文件</el-button>
              <template #tip>
                <div class="el-upload__tip">支持 .md / .txt / .pdf（单文件）</div>
              </template>
            </el-upload>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="uploadForm.title" placeholder="默认用文件名" />
          </el-form-item>
          <el-form-item label="知识集合">
            <el-select
              v-model="uploadForm.collection"
              filterable
              allow-create
              default-first-option
              placeholder="仅分组，不影响切分方式"
              style="width: 100%"
            >
              <el-option v-for="c in collections" :key="c" :label="c" :value="c" />
            </el-select>
            <div class="hint">知识集合只做分类容器，切分策略由下方「文档类型」决定</div>
          </el-form-item>
          <el-form-item label="可见角色">
            <el-select
              v-model="uploadForm.aclRoles"
              multiple
              filterable
              allow-create
              default-first-option
              placeholder="可选角色标签"
              style="width: 100%"
            >
              <el-option v-for="r in aclSuggestions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item>
          <el-form-item label="文档类型" required>
            <el-select
              v-model="uploadForm.contentType"
              placeholder="选择文档内容类型"
              style="width: 100%"
              @change="onContentTypeChange"
            >
              <el-option
                v-for="p in contentTypes"
                :key="p.id"
                :label="p.name"
                :value="p.id"
              >
                <div>{{ p.name }}</div>
                <div class="opt-desc">{{ p.description }}</div>
              </el-option>
            </el-select>
            <div v-if="activeTypeDesc" class="hint">{{ activeTypeDesc }}</div>
          </el-form-item>

          <el-collapse v-model="advancedOpen">
            <el-collapse-item title="高级设置（一般无需修改）" name="adv">
              <div class="hint mb8">改错可能导致 FAQ/条款被拆碎；优先用文档类型默认策略</div>
              <el-form-item label="按结构切分">
                <el-switch v-model="uploadForm.chapterSplitEnabled" @change="markAdvanced" />
              </el-form-item>
              <el-form-item label="章节正则">
                <el-input v-model="uploadForm.chapterPattern" type="textarea" :rows="2" @change="markAdvanced" />
              </el-form-item>
              <el-form-item label="标题写入">
                <el-radio-group v-model="uploadForm.sectionTitleMode" @change="markAdvanced">
                  <el-radio value="FULL_LINE">整行</el-radio>
                  <el-radio value="MATCH">仅匹配前缀</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="分块大小">
                <el-input-number v-model="uploadForm.chunkSize" :min="50" :step="50" @change="markAdvanced" />
              </el-form-item>
              <el-form-item label="重叠长度">
                <el-input-number v-model="uploadForm.overlap" :min="0" :step="10" @change="markAdvanced" />
                <span v-if="overlapWarn" class="warn">建议不超过分块大小的 50%</span>
              </el-form-item>
              <el-form-item label="最小片段">
                <el-input-number v-model="uploadForm.minChunkLengthToKeep" :min="1" :step="10" @change="markAdvanced" />
              </el-form-item>
              <el-form-item label="文本清洗">
                <div class="switch-col">
                  <el-checkbox v-model="uploadForm.stripPageNumbers" @change="markAdvanced">去除独立页码行</el-checkbox>
                  <el-checkbox v-model="uploadForm.mergeCjkHardWrap" @change="markAdvanced">合并中文硬换行</el-checkbox>
                  <el-checkbox v-model="uploadForm.collapseBlankLines" @change="markAdvanced">压缩连续空行</el-checkbox>
                  <el-checkbox v-model="uploadForm.trimOutsideChapters" @change="markAdvanced">裁切章节外杂讯</el-checkbox>
                </div>
              </el-form-item>
              <el-form-item label="分隔符">
                <el-input
                  v-model="uploadForm.separatorsText"
                  type="textarea"
                  :rows="2"
                  placeholder='JSON 数组，如 ["\\n\\n","\\n","。"]'
                  @change="markAdvanced"
                />
              </el-form-item>
            </el-collapse-item>
          </el-collapse>
        </el-form>

        <div class="preview-pane">
          <div class="preview-head">
            <span>切片预览</span>
            <el-button size="small" :loading="previewing" :disabled="saving" @click="runPreview">
              预览切片
            </el-button>
          </div>
          <el-alert
            v-for="(w, i) in preview?.warnings || []"
            :key="i"
            :title="w"
            type="warning"
            show-icon
            :closable="false"
            class="mb8"
          />
          <div v-if="preview" class="preview-meta">
            共 {{ preview.chunkCount }} 块
            <template v-if="preview.truncated">（仅展示前 {{ preview.chunks.length }}）</template>
            · 清洗后 {{ preview.cleanedLength }} 字
            <template v-if="preview.resolvedOptions?.contentType">
              · 类型 {{ String(preview.resolvedOptions.contentType) }}
            </template>
          </div>
          <el-scrollbar max-height="420px">
            <div v-for="c in preview?.chunks || []" :key="c.seq" class="chunk-card">
              <div class="chunk-title">
                #{{ c.seq }}
                <span v-if="c.section" class="sec">{{ c.section }}</span>
                <span class="len">{{ c.length }} 字</span>
              </div>
              <pre class="chunk-body">{{ c.content }}</pre>
            </div>
            <el-empty v-if="!preview" description="选择文件与文档类型后预览" :image-size="64" />
          </el-scrollbar>
        </div>
        <div class="dialog-actions">
          <el-button native-type="button" @click="uploadVisible = false">取消</el-button>
          <el-button native-type="button" :loading="previewing" :disabled="saving" @click.stop="runPreview">
            预览切片
          </el-button>
          <button
            type="button"
            class="ingest-btn"
            :disabled="saving"
            @click.stop.prevent="submitUpload"
          >
            {{ saving ? '入库中…' : '确认入库' }}
          </button>
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadFile } from 'element-plus'
import {
  ingestFile,
  listCollections,
  listDocuments,
  listSplitPresets,
  previewSplit,
  type ContentTypeDef,
  type SplitPreviewResult,
} from '@/api/kb'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const previewing = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterCollection = ref('')
const filterStatus = ref<string | undefined>()
const uploadVisible = ref(false)
const file = ref<File | null>(null)
const uploadFiles = ref<UploadFile[]>([])
const collections = ref<string[]>(['kb_default'])
const contentTypes = ref<ContentTypeDef[]>([])
const aclSuggestions = ref<string[]>(['admin', 'viewer'])
const advancedOpen = ref<string[]>([])
const preview = ref<SplitPreviewResult | null>(null)

const uploadForm = reactive({
  title: '',
  collection: 'kb_default',
  aclRoles: [] as string[],
  contentType: 'narrative',
  chunkSize: 500,
  overlap: 80,
  minChunkLengthToKeep: 50,
  chapterSplitEnabled: true,
  chapterPattern: '',
  sectionTitleMode: 'FULL_LINE',
  separatorsText: '["\\n\\n","\\n","。","！","？","；","，"]',
  stripPageNumbers: true,
  mergeCjkHardWrap: true,
  collapseBlankLines: true,
  trimOutsideChapters: true,
  normalizeNewlines: true,
  /** 高级区是否手动改过参数；未改则入库只传 contentType */
  advancedTouched: false,
})

const activeTypeDesc = computed(
  () => contentTypes.value.find((p) => p.id === uploadForm.contentType)?.description || '',
)
const overlapWarn = computed(
  () => uploadForm.chunkSize > 0 && uploadForm.overlap * 2 > uploadForm.chunkSize,
)

async function load() {
  loading.value = true
  try {
    const data = await listDocuments({
      collection: filterCollection.value || undefined,
      status: filterStatus.value,
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
  load()
}

function onPage(p: number) {
  page.value = p
  load()
}

function goDetail(row: Record<string, unknown>) {
  router.push(`/kb/${row.doc_id}`)
}

function applyContentType(id: string) {
  const p = contentTypes.value.find((x) => x.id === id)
  if (!p) return
  const s = p.split || {}
  const pre = p.preprocess || {}
  uploadForm.contentType = id
  uploadForm.chunkSize = Number(s.chunkSize ?? 500)
  uploadForm.overlap = Number(s.overlap ?? 80)
  uploadForm.minChunkLengthToKeep = Number(s.minChunkLengthToKeep ?? 50)
  uploadForm.chapterSplitEnabled = Boolean(s.chapterSplitEnabled ?? true)
  uploadForm.chapterPattern = String(s.chapterPattern ?? '')
  uploadForm.sectionTitleMode = String(s.sectionTitleMode ?? 'FULL_LINE')
  const seps = s.separators as string[] | undefined
  uploadForm.separatorsText = JSON.stringify(seps ?? ['\n\n', '\n', '。', '！', '？', '；', '，'])
  uploadForm.normalizeNewlines = Boolean(pre.normalizeNewlines ?? true)
  uploadForm.stripPageNumbers = Boolean(pre.stripPageNumbers ?? true)
  uploadForm.mergeCjkHardWrap = Boolean(pre.mergeCjkHardWrap ?? true)
  uploadForm.collapseBlankLines = Boolean(pre.collapseBlankLines ?? true)
  uploadForm.trimOutsideChapters = Boolean(pre.trimOutsideChapters ?? false)
  uploadForm.advancedTouched = false
  if (id === 'custom') {
    advancedOpen.value = ['adv']
  }
}

function onContentTypeChange() {
  applyContentType(uploadForm.contentType)
  preview.value = null
}

function suggestTypeByName(name: string): string {
  const lower = name.toLowerCase()
  if (lower.endsWith('.md') || lower.endsWith('.markdown')) return 'tech_markdown'
  if (/\.(java|kt|py|ts|js|go)$/.test(lower)) return 'code_structure'
  return 'narrative'
}

function resetUploadState() {
  file.value = null
  uploadFiles.value = []
  preview.value = null
  uploadForm.title = ''
  uploadForm.collection = 'kb_default'
  uploadForm.aclRoles = []
  uploadForm.advancedTouched = false
  advancedOpen.value = []
  saving.value = false
  previewing.value = false
}

async function openUpload() {
  resetUploadState()
  try {
    const [cols, meta] = await Promise.all([listCollections(), listSplitPresets()])
    collections.value = cols?.length ? cols : ['kb_default']
    if (!collections.value.includes('kb_default')) {
      collections.value = ['kb_default', ...collections.value]
    }
    contentTypes.value = meta.contentTypes?.length ? meta.contentTypes : meta.presets || []
    aclSuggestions.value = meta.aclRoleSuggestions?.length ? meta.aclRoleSuggestions : ['admin', 'viewer']
  } catch {
    contentTypes.value = []
  }
  applyContentType('narrative')
  uploadVisible.value = true
}

function rawFromUpload(uploadFile?: UploadFile): File | null {
  const raw = uploadFile?.raw
  return raw instanceof File ? raw : null
}

/** 以 el-upload 的 file-list 为准，避免 UI 已显示文件但 ref 为空导致按钮被 disabled、点击完全无请求 */
function resolveSelectedFile(): File | null {
  if (file.value instanceof File) {
    return file.value
  }
  for (let i = uploadFiles.value.length - 1; i >= 0; i--) {
    const raw = rawFromUpload(uploadFiles.value[i])
    if (raw) {
      return raw
    }
  }
  return null
}

function rememberFile(raw: File | null, name?: string) {
  file.value = raw
  preview.value = null
  const displayName = name || raw?.name || ''
  if (!uploadForm.title && displayName) {
    uploadForm.title = displayName
  }
  if (displayName && !uploadForm.advancedTouched) {
    applyContentType(suggestTypeByName(displayName))
  }
}

function onFileChange(uploadFile: UploadFile, files: UploadFile[]) {
  rememberFile(rawFromUpload(uploadFile) || rawFromUpload(files[files.length - 1]), uploadFile.name)
}

function onFileRemove() {
  file.value = null
  uploadFiles.value = []
  preview.value = null
}

function onFileExceed(files: File[]) {
  const next = files[0]
  if (!next) {
    return
  }
  uploadFiles.value = [
    {
      name: next.name,
      size: next.size,
      status: 'ready',
      uid: Date.now(),
      raw: next,
    } as UploadFile,
  ]
  rememberFile(next, next.name)
}

function markAdvanced() {
  uploadForm.advancedTouched = true
}

function appendSplitFields(form: FormData) {
  const strategy = uploadForm.contentType
  form.append('contentType', strategy)
  // 兼容：部分网关/旧绑定只认 preset；与 contentType 同值，服务端优先 contentType
  form.append('preset', strategy)
  // 自定义或高级改过时才传技术参数覆盖
  if (uploadForm.contentType === 'custom' || uploadForm.advancedTouched) {
    form.append('chunkSize', String(uploadForm.chunkSize))
    form.append('overlap', String(uploadForm.overlap))
    form.append('minChunkLengthToKeep', String(uploadForm.minChunkLengthToKeep))
    form.append('chapterSplitEnabled', String(uploadForm.chapterSplitEnabled))
    if (uploadForm.chapterPattern) form.append('chapterPattern', uploadForm.chapterPattern)
    form.append('sectionTitleMode', uploadForm.sectionTitleMode)
    if (uploadForm.separatorsText) form.append('separators', uploadForm.separatorsText)
    form.append('keepSeparator', 'APPEND')
    form.append('normalizeNewlines', String(uploadForm.normalizeNewlines))
    form.append('stripPageNumbers', String(uploadForm.stripPageNumbers))
    form.append('mergeCjkHardWrap', String(uploadForm.mergeCjkHardWrap))
    form.append('collapseBlankLines', String(uploadForm.collapseBlankLines))
    form.append('trimOutsideChapters', String(uploadForm.trimOutsideChapters))
  }
}

async function runPreview() {
  const raw = resolveSelectedFile()
  if (!raw) {
    await ElMessageBox.alert('请选择文件', '无法预览', { type: 'warning', confirmButtonText: '知道了' }).catch(
      () => undefined,
    )
    return
  }
  if (uploadForm.advancedTouched && uploadForm.overlap >= uploadForm.chunkSize) {
    ElMessage.error({ message: '重叠长度必须小于分块大小', zIndex: 4000 })
    return
  }
  previewing.value = true
  try {
    const form = new FormData()
    form.append('file', raw)
    appendSplitFields(form)
    preview.value = await previewSplit(form)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '预览失败'
    ElMessage.error({ message: msg, zIndex: 4000 })
  } finally {
    previewing.value = false
  }
}

async function submitUpload() {
  ElMessage.info({ message: '开始入库…', zIndex: 5000, duration: 1500 })
  const raw = resolveSelectedFile()
  if (!raw) {
    await ElMessageBox.alert('请选择文件后再入库', '无法入库', {
      type: 'warning',
      confirmButtonText: '知道了',
    }).catch(() => undefined)
    return
  }
  if (!uploadForm.contentType) {
    await ElMessageBox.alert('请选择文档类型', '无法入库', { type: 'warning', confirmButtonText: '知道了' }).catch(
      () => undefined,
    )
    return
  }
  if (uploadForm.advancedTouched && uploadForm.overlap >= uploadForm.chunkSize) {
    ElMessage.error({ message: '重叠长度必须小于分块大小', zIndex: 4000 })
    return
  }
  saving.value = true
  try {
    const form = new FormData()
    form.append('file', raw)
    if (uploadForm.title) form.append('title', uploadForm.title)
    if (uploadForm.collection) form.append('collection', uploadForm.collection)
    if (uploadForm.aclRoles.length) form.append('aclRoles', uploadForm.aclRoles.join(','))
    appendSplitFields(form)
    const result = await ingestFile(form)
    if (!result?.docId) {
      throw new Error('入库响应无效，请查看网络面板或后端日志')
    }
    ElMessage.success({
      message: `入库成功：${result.docId} ${result.version}（${result.chunkCount} chunks）`,
      zIndex: 4000,
    })
    uploadVisible.value = false
    await load()
    await router.push(`/kb/${result.docId}`)
  } catch (e: unknown) {
    const msg = e instanceof Error ? e.message : '入库失败'
    // Message 可能被 dialog 挡住；用 MessageBox 保证可见
    await ElMessageBox.alert(msg || '入库失败', '入库失败', {
      type: 'error',
      confirmButtonText: '知道了',
    }).catch(() => undefined)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.pager {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.upload-layout {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.upload-form {
  min-width: 0;
  overflow: hidden;
}
.upload-form :deep(.el-upload__input) {
  /* 防止隐藏 file input 铺满弹窗抢走「确认入库」点击 */
  display: none !important;
}
.dialog-actions {
  grid-column: 1 / -1;
  position: relative;
  z-index: 30;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color);
  background: #fff;
}
.ingest-btn {
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: 4px;
  background: #409eff;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
.ingest-btn:disabled {
  opacity: 0.65;
  cursor: not-allowed;
}
.preview-pane {
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  padding: 12px;
  min-height: 360px;
}
.preview-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-weight: 600;
}
.preview-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.chunk-card {
  border-bottom: 1px solid var(--el-border-color-lighter);
  padding: 8px 0;
}
.chunk-title {
  font-size: 12px;
  margin-bottom: 4px;
}
.chunk-title .sec {
  margin-left: 8px;
  color: var(--el-color-primary);
}
.chunk-title .len {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
}
.chunk-body {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  margin: 0;
  max-height: 120px;
  overflow: auto;
}
.hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  line-height: 1.4;
}
.opt-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  max-width: 420px;
  white-space: normal;
}
.warn {
  margin-left: 8px;
  color: var(--el-color-warning);
  font-size: 12px;
}
.switch-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.mb8 {
  margin-bottom: 8px;
}
@media (max-width: 900px) {
  .upload-layout {
    grid-template-columns: 1fr;
  }
}
</style>
