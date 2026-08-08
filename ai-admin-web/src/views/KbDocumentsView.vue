<template>
  <el-card>
    <div class="toolbar">
      <el-input
        v-model="filterCollection"
        clearable
        placeholder="collection"
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

    <el-dialog v-model="uploadVisible" title="上传知识库文档" width="560px">
      <el-form label-width="130px">
        <el-form-item label="文件" required>
          <el-upload
            :auto-upload="false"
            :limit="1"
            accept=".md,.txt,.markdown,.pdf"
            :on-change="onFileChange"
            :on-remove="() => (file = null)"
          >
            <el-button>选择 .md / .txt / .pdf</el-button>
          </el-upload>
        </el-form-item>
        <el-form-item label="标题"><el-input v-model="uploadForm.title" placeholder="默认用文件名" /></el-form-item>
        <el-form-item label="collection"><el-input v-model="uploadForm.collection" placeholder="kb_default" /></el-form-item>
        <el-form-item label="docId"><el-input v-model="uploadForm.docId" placeholder="空则自动生成" /></el-form-item>
        <el-form-item label="aclRoles"><el-input v-model="uploadForm.aclRoles" placeholder="逗号分隔，可空" /></el-form-item>
        <el-form-item label="chunkSize"><el-input-number v-model="uploadForm.chunkSize" :min="50" :step="50" /></el-form-item>
        <el-form-item label="overlap"><el-input-number v-model="uploadForm.overlap" :min="0" :step="10" /></el-form-item>
        <el-form-item label="minChunkKeep"><el-input-number v-model="uploadForm.minChunkLengthToKeep" :min="1" :step="10" /></el-form-item>
        <el-form-item label="章节硬切">
          <el-switch v-model="uploadForm.chapterSplitEnabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitUpload">入库</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { ingestFile, listDocuments } from '@/api/kb'

const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const rows = ref<Record<string, unknown>[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterCollection = ref('')
const filterStatus = ref<string | undefined>()
const uploadVisible = ref(false)
const file = ref<File | null>(null)

const uploadForm = reactive({
  title: '',
  collection: 'kb_default',
  docId: '',
  aclRoles: '',
  chunkSize: 500,
  overlap: 80,
  minChunkLengthToKeep: 50,
  chapterSplitEnabled: true,
})

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

function openUpload() {
  file.value = null
  uploadForm.title = ''
  uploadForm.collection = 'kb_default'
  uploadForm.docId = ''
  uploadForm.aclRoles = ''
  uploadForm.chunkSize = 500
  uploadForm.overlap = 80
  uploadForm.minChunkLengthToKeep = 50
  uploadForm.chapterSplitEnabled = true
  uploadVisible.value = true
}

function onFileChange(uploadFile: UploadFile) {
  file.value = (uploadFile.raw as File) || null
  if (!uploadForm.title && uploadFile.name) {
    uploadForm.title = uploadFile.name
  }
}

async function submitUpload() {
  if (!file.value) {
    ElMessage.warning('请选择文件')
    return
  }
  saving.value = true
  try {
    const form = new FormData()
    form.append('file', file.value)
    if (uploadForm.title) form.append('title', uploadForm.title)
    if (uploadForm.collection) form.append('collection', uploadForm.collection)
    if (uploadForm.docId) form.append('docId', uploadForm.docId)
    if (uploadForm.aclRoles) form.append('aclRoles', uploadForm.aclRoles)
    form.append('chunkSize', String(uploadForm.chunkSize))
    form.append('overlap', String(uploadForm.overlap))
    form.append('minChunkLengthToKeep', String(uploadForm.minChunkLengthToKeep))
    form.append('chapterSplitEnabled', String(uploadForm.chapterSplitEnabled))
    const result = await ingestFile(form)
    ElMessage.success(`入库成功：${result.docId} ${result.version}（${result.chunkCount} chunks）`)
    uploadVisible.value = false
    await load()
    router.push(`/kb/${result.docId}`)
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
</style>
