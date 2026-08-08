<template>
  <div class="layout">
    <el-card class="servers" v-loading="serverLoading">
      <div class="servers-head">
        <span class="title">MCP Servers</span>
        <div>
          <el-button size="small" @click="loadAll">刷新</el-button>
          <el-button size="small" type="primary" @click="openCreate">新建</el-button>
        </div>
      </div>
      <el-menu :default-active="selectedCode" @select="onSelectServer">
        <el-menu-item v-for="s in servers" :key="s.serverCode" :index="s.serverCode">
          <div class="menu-item">
            <div>{{ s.displayName }}</div>
            <div class="muted">{{ s.serverCode }} · {{ s.status }}</div>
          </div>
        </el-menu-item>
      </el-menu>
      <div v-if="!servers.length" class="muted empty">暂无 Server，请新建或检查 DevSeed</div>
    </el-card>

    <el-card class="main">
      <div v-if="server" class="server-meta">
        <div>
          <div class="server-title">{{ server.displayName }}（{{ server.serverCode }}）</div>
          <div class="meta-line">
            <span>状态：{{ server.status }}</span>
            <span>URL：{{ server.baseUrl }}</span>
            <span>鉴权：{{ server.authType }} {{ server.authTokenMasked || '' }}</span>
          </div>
        </div>
        <div class="actions">
          <el-button @click="openEdit">编辑</el-button>
          <el-button v-if="server.status === 'ACTIVE'" @click="disableServer">禁用</el-button>
          <el-button v-else type="success" @click="enableServer">启用</el-button>
        </div>
      </div>

    <el-alert
      v-if="source === 'DB_ONLY'"
      type="warning"
      :closable="false"
      show-icon
      title="远端 /mcp/info 不可达，仅展示库内绑定。若 mcp-server 已开鉴权，请将本 Server 的 authType 设为 BEARER 并填写与 Server 相同的 API Key"
      class="mb"
    />
      <el-alert
        v-else-if="source"
        type="info"
        :closable="false"
        show-icon
        title="已合并远端目录与本地绑定；仅「已绑定且启用」注入 Agent；有 ACTIVE Server 时零绑定=该 Server 全量"
        class="mb"
      />

      <div class="toolbar">
        <el-button type="primary" :loading="saving" :disabled="!rows.length" @click="save">保存绑定</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="toolName" label="工具名" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="绑定" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.bound" @change="(v: boolean) => onBoundChange(row, v)" />
          </template>
        </el-table-column>
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" :disabled="!row.bound" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <el-dialog v-model="dialogVisible" :title="dialogMode === 'create' ? '新建 MCP Server' : '编辑 MCP Server'" width="520px">
    <el-form label-width="110px">
      <el-form-item label="serverCode" required>
        <el-input v-model="form.serverCode" :disabled="dialogMode === 'edit'" placeholder="如 wuji-mcp" />
      </el-form-item>
      <el-form-item label="展示名" required>
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item label="baseUrl" required>
        <el-input v-model="form.baseUrl" placeholder="http://127.0.0.1:8081" />
      </el-form-item>
      <el-form-item label="sseEndpoint">
        <el-input v-model="form.sseEndpoint" placeholder="/sse" />
      </el-form-item>
      <el-form-item label="authType">
        <el-select v-model="form.authType" style="width: 100%">
          <el-option label="NONE" value="NONE" />
          <el-option label="BEARER" value="BEARER" />
        </el-select>
      </el-form-item>
      <el-form-item label="authToken" :required="dialogMode === 'create' && form.authType === 'BEARER'">
        <el-input
          v-model="form.authToken"
          type="password"
          show-password
          :placeholder="dialogMode === 'edit' ? '留空则不修改' : ''"
        />
      </el-form-item>
      <el-form-item label="sortOrder">
        <el-input-number v-model="form.sortOrder" :min="0" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="dialogSaving" @click="submitDialog">保存</el-button>
    </template>
  </el-dialog>

  <el-drawer v-model="detailVisible" title="工具详情" size="420px">
    <div v-loading="detailLoading">
      <template v-if="detail">
        <p><strong>工具名：</strong>{{ detail.toolName }}</p>
        <p><strong>描述：</strong>{{ detail.description || '—' }}</p>
        <p><strong>绑定/启用：</strong>{{ detail.bound ? '已绑定' : '未绑定' }} / {{ detail.enabled ? '启用' : '禁用' }}</p>
        <p><strong>source：</strong>{{ detail.source }}</p>
        <p v-if="detail.serverVersion"><strong>serverVersion：</strong>{{ detail.serverVersion }}</p>
        <p v-if="detail.toolHash"><strong>toolHash：</strong>{{ detail.toolHash }}</p>
        <p><strong>inputSchema：</strong></p>
        <pre class="schema">{{ formatSchema(detail.inputSchema) }}</pre>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as mcpApi from '@/api/mcp'
import type { AdminMcpServerView, AdminMcpToolDetailView, AdminMcpToolView } from '@/api/mcp'

const serverLoading = ref(false)
const loading = ref(false)
const saving = ref(false)
const servers = ref<AdminMcpServerView[]>([])
const selectedCode = ref('')
const server = ref<AdminMcpServerView | null>(null)
const rows = ref<AdminMcpToolView[]>([])
const source = ref('')

const dialogVisible = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const dialogSaving = ref(false)
const form = reactive({
  serverCode: '',
  displayName: '',
  baseUrl: '',
  sseEndpoint: '/sse',
  authType: 'NONE',
  authToken: '',
  sortOrder: 0,
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<AdminMcpToolDetailView | null>(null)

function onBoundChange(row: AdminMcpToolView, bound: boolean) {
  row.bound = bound
  if (!bound) row.enabled = false
}

function formatSchema(schema: unknown) {
  if (schema == null) return '—'
  try {
    return JSON.stringify(schema, null, 2)
  } catch {
    return String(schema)
  }
}

async function loadServers(preferCode?: string) {
  serverLoading.value = true
  try {
    const list = await mcpApi.listMcpServers()
    servers.value = list
    const code = preferCode && list.some((s) => s.serverCode === preferCode)
      ? preferCode
      : selectedCode.value && list.some((s) => s.serverCode === selectedCode.value)
        ? selectedCode.value
        : list[0]?.serverCode ?? ''
    selectedCode.value = code
    server.value = list.find((s) => s.serverCode === code) ?? null
  } finally {
    serverLoading.value = false
  }
}

async function loadTools() {
  if (!server.value) {
    rows.value = []
    source.value = ''
    return
  }
  loading.value = true
  try {
    const data = await mcpApi.listMcpTools(server.value.serverCode)
    rows.value = data.tools.map((t) => ({
      ...t,
      enabled: t.bound ? t.enabled : false,
    }))
    source.value = data.source
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  await loadServers()
  await loadTools()
}

async function onSelectServer(code: string) {
  selectedCode.value = code
  server.value = servers.value.find((s) => s.serverCode === code) ?? null
  await loadTools()
}

function openCreate() {
  dialogMode.value = 'create'
  form.serverCode = ''
  form.displayName = ''
  form.baseUrl = 'http://127.0.0.1:8081'
  form.sseEndpoint = '/sse'
  form.authType = 'NONE'
  form.authToken = ''
  form.sortOrder = 0
  dialogVisible.value = true
}

function openEdit() {
  if (!server.value) return
  dialogMode.value = 'edit'
  form.serverCode = server.value.serverCode
  form.displayName = server.value.displayName
  form.baseUrl = server.value.baseUrl
  form.sseEndpoint = server.value.sseEndpoint || '/sse'
  form.authType = server.value.authType || 'NONE'
  form.authToken = ''
  form.sortOrder = server.value.sortOrder ?? 0
  dialogVisible.value = true
}

async function submitDialog() {
  dialogSaving.value = true
  try {
    if (dialogMode.value === 'create') {
      const created = await mcpApi.createMcpServer({
        serverCode: form.serverCode.trim(),
        displayName: form.displayName.trim(),
        baseUrl: form.baseUrl.trim(),
        sseEndpoint: form.sseEndpoint.trim() || undefined,
        authType: form.authType,
        authToken: form.authToken || undefined,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('已创建')
      dialogVisible.value = false
      await loadServers(created.serverCode)
      await loadTools()
    } else {
      await mcpApi.updateMcpServer(form.serverCode, {
        displayName: form.displayName.trim(),
        baseUrl: form.baseUrl.trim(),
        sseEndpoint: form.sseEndpoint.trim() || undefined,
        authType: form.authType,
        authToken: form.authToken || undefined,
        sortOrder: form.sortOrder,
      })
      ElMessage.success('已更新')
      dialogVisible.value = false
      await loadServers(form.serverCode)
      await loadTools()
    }
  } finally {
    dialogSaving.value = false
  }
}

async function disableServer() {
  if (!server.value) return
  await ElMessageBox.confirm(`禁用 ${server.value.serverCode}？将不再建 Transport。`, '确认')
  await mcpApi.updateMcpServer(server.value.serverCode, { status: 'DISABLED' })
  ElMessage.success('已禁用')
  await loadAll()
}

async function enableServer() {
  if (!server.value) return
  await mcpApi.updateMcpServer(server.value.serverCode, { status: 'ACTIVE' })
  ElMessage.success('已启用')
  await loadAll()
}

async function openDetail(row: AdminMcpToolView) {
  if (!server.value) return
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await mcpApi.getMcpToolDetail(server.value.serverCode, row.toolName)
  } finally {
    detailLoading.value = false
  }
}

async function save() {
  if (!server.value) return
  saving.value = true
  try {
    const data = await mcpApi.updateMcpTools(
      server.value.serverCode,
      rows.value.map((r) => ({
        toolName: r.toolName,
        bound: r.bound,
        enabled: r.bound ? r.enabled : false,
      })),
    )
    rows.value = data.tools.map((t) => ({
      ...t,
      enabled: t.bound ? t.enabled : false,
    }))
    source.value = data.source
    ElMessage.success('已保存并刷新 Registry')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<style scoped>
.layout {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 12px;
  align-items: start;
}
.servers-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.title {
  font-weight: 600;
}
.menu-item {
  line-height: 1.3;
  white-space: normal;
}
.muted {
  color: #6b7280;
  font-size: 12px;
}
.empty {
  padding: 12px 0;
}
.server-meta {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.server-title {
  font-weight: 600;
  font-size: 15px;
}
.meta-line {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  margin-top: 6px;
  color: #6b7280;
  font-size: 13px;
}
.actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.toolbar {
  margin-bottom: 12px;
}
.mb {
  margin-bottom: 12px;
}
.schema {
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  font-size: 12px;
  overflow: auto;
  max-height: 50vh;
}
@media (max-width: 900px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
