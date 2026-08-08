<template>
  <el-card>
    <div class="toolbar">
      <el-select v-model="filterKind" clearable placeholder="modelKind" style="width: 140px" @change="reload">
        <el-option label="CHAT" value="CHAT" />
        <el-option label="EMBEDDING" value="EMBEDDING" />
      </el-select>
      <el-select v-model="filterStatus" clearable placeholder="status" style="width: 140px" @change="reload">
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="DISABLED" value="DISABLED" />
      </el-select>
      <el-button type="primary" @click="openCreate">新建配置</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="configId" label="configId" min-width="140" />
      <el-table-column prop="name" label="名称" min-width="120" />
      <el-table-column prop="modelKind" label="Kind" width="110" />
      <el-table-column prop="model" label="模型" min-width="140" />
      <el-table-column prop="apiKeyMasked" label="API Key" width="130" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="isSuperAdmin"
            link
            type="primary"
            @click="revealKey(row)"
          >看 Key</el-button>
          <el-button
            link
            type="danger"
            :disabled="row.status === 'DISABLED'"
            @click="onDisable(row)"
          >禁用</el-button>
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

    <el-dialog v-model="createVisible" title="新建 LLM 配置" width="560px">
      <el-form label-width="100px">
        <el-form-item label="configId"><el-input v-model="createForm.configId" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="Kind">
          <el-select v-model="createForm.modelKind" style="width: 100%">
            <el-option label="CHAT" value="CHAT" />
            <el-option label="EMBEDDING" value="EMBEDDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL"><el-input v-model="createForm.baseUrl" /></el-form-item>
        <el-form-item label="模型"><el-input v-model="createForm.model" /></el-form-item>
        <el-form-item label="API Key"><el-input v-model="createForm.apiKey" type="password" show-password /></el-form-item>
        <el-form-item label="温度"><el-input-number v-model="createForm.temperature" :min="0" :max="2" :step="0.1" /></el-form-item>
        <el-form-item label="maxTokens"><el-input-number v-model="createForm.maxTokens" :min="1" :step="256" /></el-form-item>
        <el-form-item label="extraJson"><el-input v-model="createForm.extraJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑 LLM 配置" width="560px">
      <el-form label-width="100px">
        <el-form-item label="configId"><el-input v-model="editForm.configId" disabled /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.name" /></el-form-item>
        <el-form-item label="Kind">
          <el-select v-model="editForm.modelKind" style="width: 100%">
            <el-option label="CHAT" value="CHAT" />
            <el-option label="EMBEDDING" value="EMBEDDING" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL"><el-input v-model="editForm.baseUrl" /></el-form-item>
        <el-form-item label="模型"><el-input v-model="editForm.model" /></el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="editForm.apiKey" type="password" show-password placeholder="留空则不修改" />
        </el-form-item>
        <el-form-item label="温度"><el-input-number v-model="editForm.temperature" :min="0" :max="2" :step="0.1" /></el-form-item>
        <el-form-item label="maxTokens"><el-input-number v-model="editForm.maxTokens" :min="1" :step="256" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item label="extraJson"><el-input v-model="editForm.extraJson" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import * as llmApi from '@/api/llmConfigs'
import type { AdminLlmConfigView } from '@/api/llmConfigs'

const auth = useAuthStore()
const isSuperAdmin = computed(() => auth.profile?.role === 'SUPER_ADMIN')

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminLlmConfigView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const filterKind = ref<string | undefined>()
const filterStatus = ref<string | undefined>()

const createVisible = ref(false)
const editVisible = ref(false)

const createForm = reactive({
  configId: '',
  name: '',
  modelKind: 'CHAT',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  model: '',
  temperature: 0.7 as number | undefined,
  maxTokens: 4096 as number | undefined,
  extraJson: '{}',
})

const editForm = reactive({
  configId: '',
  name: '',
  modelKind: 'CHAT',
  baseUrl: '',
  apiKey: '',
  model: '',
  temperature: undefined as number | undefined,
  maxTokens: undefined as number | undefined,
  status: 'ACTIVE',
  extraJson: '{}',
})

async function load() {
  loading.value = true
  try {
    const data = await llmApi.listLlmConfigs({
      page: page.value,
      size: size.value,
      modelKind: filterKind.value,
      status: filterStatus.value,
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

function openCreate() {
  createForm.configId = ''
  createForm.name = ''
  createForm.modelKind = 'CHAT'
  createForm.baseUrl = 'https://api.openai.com/v1'
  createForm.apiKey = ''
  createForm.model = ''
  createForm.temperature = 0.7
  createForm.maxTokens = 4096
  createForm.extraJson = '{}'
  createVisible.value = true
}

async function submitCreate() {
  saving.value = true
  try {
    await llmApi.createLlmConfig({
      configId: createForm.configId,
      name: createForm.name,
      modelKind: createForm.modelKind,
      baseUrl: createForm.baseUrl,
      apiKey: createForm.apiKey,
      model: createForm.model,
      temperature: createForm.temperature,
      maxTokens: createForm.maxTokens,
      extraJson: createForm.extraJson,
    })
    ElMessage.success('已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openEdit(row: AdminLlmConfigView) {
  editForm.configId = row.configId
  editForm.name = row.name
  editForm.modelKind = row.modelKind
  editForm.baseUrl = row.baseUrl
  editForm.apiKey = ''
  editForm.model = row.model
  editForm.temperature = row.temperature ?? undefined
  editForm.maxTokens = row.maxTokens ?? undefined
  editForm.status = row.status
  editForm.extraJson = row.extraJson || '{}'
  editVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await llmApi.updateLlmConfig(editForm.configId, {
      name: editForm.name,
      modelKind: editForm.modelKind,
      baseUrl: editForm.baseUrl,
      apiKey: editForm.apiKey || undefined,
      model: editForm.model,
      temperature: editForm.temperature,
      maxTokens: editForm.maxTokens,
      status: editForm.status,
      extraJson: editForm.extraJson,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function revealKey(row: AdminLlmConfigView) {
  const detail = await llmApi.getLlmConfig(row.configId, true)
  await ElMessageBox.alert(detail.apiKeyPreview || '(空)', `API Key · ${row.configId}`, {
    confirmButtonText: '关闭',
  })
}

async function onDisable(row: AdminLlmConfigView) {
  await ElMessageBox.confirm(`确认禁用配置「${row.configId}」？`, '提示', { type: 'warning' })
  await llmApi.deleteLlmConfig(row.configId)
  ElMessage.success('已禁用')
  await load()
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
</style>
