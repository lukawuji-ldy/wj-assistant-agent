<template>
  <el-card>
    <div class="toolbar">
      <el-select
        v-model="filters.userId"
        filterable
        remote
        clearable
        placeholder="选择用户"
        :remote-method="searchUsers"
        :loading="userLoading"
        style="width: 240px"
        @change="onFilter"
      >
        <el-option
          v-for="u in userOptions"
          :key="u.userId"
          :label="`${u.nickname} (${u.username})`"
          :value="u.userId"
        />
      </el-select>
      <el-input
        v-model="filters.memoryKey"
        clearable
        placeholder="memoryKey"
        style="width: 160px"
        @keyup.enter="onFilter"
      />
      <el-select v-model="filters.memoryType" clearable placeholder="类型" style="width: 130px" @change="onFilter">
        <el-option label="PROFILE" value="PROFILE" />
        <el-option label="PREFERENCE" value="PREFERENCE" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="onFilter">
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="INACTIVE" value="INACTIVE" />
        <el-option label="DELETED" value="DELETED" />
        <el-option label="EXPIRED" value="EXPIRED" />
      </el-select>
      <el-button type="primary" @click="onFilter">查询</el-button>
      <el-button type="primary" @click="openCreate">新建</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="userId" label="用户" min-width="110" />
      <el-table-column prop="memoryType" label="类型" width="110" />
      <el-table-column prop="memoryKey" label="Key" min-width="140" />
      <el-table-column prop="memoryValue" label="值" min-width="200" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="source" label="来源" width="110" />
      <el-table-column prop="version" label="版本" width="70" />
      <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" :disabled="row.status === 'DELETED'" @click="onDelete(row)">
            软删
          </el-button>
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

    <el-dialog v-model="createVisible" title="新建 Profile" width="480px">
      <el-form label-width="100px">
        <el-form-item label="用户">
          <el-select
            v-model="createForm.userId"
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="`${u.nickname} (${u.username})`"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="createForm.memoryType" style="width: 100%">
            <el-option label="PROFILE" value="PROFILE" />
            <el-option label="PREFERENCE" value="PREFERENCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Key"><el-input v-model="createForm.memoryKey" /></el-form-item>
        <el-form-item label="值"><el-input v-model="createForm.memoryValue" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="createForm.confidence" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="重要度">
          <el-input-number v-model="createForm.importance" :min="0" :max="1" :step="0.1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑 Profile" width="480px">
      <el-form label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="editForm.memoryType" style="width: 100%">
            <el-option label="PROFILE" value="PROFILE" />
            <el-option label="PREFERENCE" value="PREFERENCE" />
          </el-select>
        </el-form-item>
        <el-form-item label="Key"><el-input v-model="editForm.memoryKey" /></el-form-item>
        <el-form-item label="值"><el-input v-model="editForm.memoryValue" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="INACTIVE" value="INACTIVE" />
            <el-option label="DELETED" value="DELETED" />
            <el-option label="EXPIRED" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="editForm.confidence" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="重要度">
          <el-input-number v-model="editForm.importance" :min="0" :max="1" :step="0.1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as memoryApi from '@/api/memory'
import type { AdminProfileView } from '@/api/memory'
import * as chatUsersApi from '@/api/chatUsers'
import type { AdminChatUserView } from '@/api/chatUsers'

const loading = ref(false)
const saving = ref(false)
const userLoading = ref(false)
const rows = ref<AdminProfileView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const userOptions = ref<AdminChatUserView[]>([])

const filters = reactive({
  userId: '' as string,
  memoryKey: '',
  memoryType: '' as string,
  status: '' as string,
})

const createVisible = ref(false)
const editVisible = ref(false)

const createForm = reactive({
  userId: '',
  memoryType: 'PROFILE',
  memoryKey: '',
  memoryValue: '',
  confidence: 1,
  importance: 0.5,
})

const editForm = reactive({
  memoryId: '',
  memoryType: 'PROFILE',
  memoryKey: '',
  memoryValue: '',
  status: 'ACTIVE',
  confidence: 1,
  importance: 0.5,
})

async function searchUsers(q: string) {
  userLoading.value = true
  try {
    const data = await chatUsersApi.listChatUsers(q || '', 1, 50)
    userOptions.value = data.items
  } finally {
    userLoading.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const data = await memoryApi.listProfiles({
      userId: filters.userId || undefined,
      memoryKey: filters.memoryKey || undefined,
      memoryType: filters.memoryType || undefined,
      status: filters.status || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onFilter() {
  page.value = 1
  void load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

function openCreate() {
  createForm.userId = filters.userId || ''
  createForm.memoryType = 'PROFILE'
  createForm.memoryKey = ''
  createForm.memoryValue = ''
  createForm.confidence = 1
  createForm.importance = 0.5
  createVisible.value = true
  void searchUsers('')
}

async function submitCreate() {
  if (!createForm.userId || !createForm.memoryKey || !createForm.memoryValue) {
    ElMessage.warning('用户、Key、值不能为空')
    return
  }
  saving.value = true
  try {
    await memoryApi.createProfile({ ...createForm })
    ElMessage.success('已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openEdit(row: AdminProfileView) {
  editForm.memoryId = row.memoryId
  editForm.memoryType = row.memoryType
  editForm.memoryKey = row.memoryKey
  editForm.memoryValue = row.memoryValue
  editForm.status = row.status
  editForm.confidence = row.confidence
  editForm.importance = row.importance
  editVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await memoryApi.updateProfile(editForm.memoryId, {
      memoryType: editForm.memoryType,
      memoryKey: editForm.memoryKey,
      memoryValue: editForm.memoryValue,
      status: editForm.status,
      confidence: editForm.confidence,
      importance: editForm.importance,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: AdminProfileView) {
  await ElMessageBox.confirm(`软删 Profile「${row.memoryKey}」？`, '确认', { type: 'warning' })
  await memoryApi.deleteProfile(row.memoryId)
  ElMessage.success('已软删')
  await load()
}

onMounted(() => {
  void searchUsers('')
  void load()
})
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
