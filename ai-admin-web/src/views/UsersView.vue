<template>
  <el-card>
    <div class="toolbar">
      <el-button type="primary" @click="openCreate">新建管理员</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="展示名" min-width="120" />
      <el-table-column prop="role" label="角色" width="130" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="内置" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.builtin" type="warning" size="small">是</el-tag>
          <span v-else>否</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="openPassword(row)">改密</el-button>
          <el-button link type="danger" :disabled="row.builtin" @click="onDelete(row)">禁用</el-button>
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

    <el-dialog v-model="createVisible" title="新建管理员" width="420px">
      <el-form label-width="80px">
        <el-form-item label="用户名"><el-input v-model="createForm.username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="createForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="展示名"><el-input v-model="createForm.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
            <el-option label="OPERATOR" value="OPERATOR" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑管理员" width="420px">
      <el-form label-width="80px">
        <el-form-item label="展示名"><el-input v-model="editForm.displayName" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.role" style="width: 100%" :disabled="editForm.builtin">
            <el-option label="SUPER_ADMIN" value="SUPER_ADMIN" />
            <el-option label="OPERATOR" value="OPERATOR" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%" :disabled="editForm.builtin">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form label-width="80px">
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPassword">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as usersApi from '@/api/users'
import type { AdminUserView } from '@/api/users'

const loading = ref(false)
const saving = ref(false)
const rows = ref<AdminUserView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)

const createVisible = ref(false)
const editVisible = ref(false)
const pwdVisible = ref(false)

const createForm = reactive({
  username: '',
  password: '',
  displayName: '',
  role: 'OPERATOR',
})

const editForm = reactive({
  adminId: '',
  displayName: '',
  role: 'OPERATOR',
  status: 'ACTIVE',
  builtin: false,
})

const pwdForm = reactive({
  adminId: '',
  newPassword: '',
})

async function load() {
  loading.value = true
  try {
    const data = await usersApi.listUsers(page.value, size.value)
    rows.value = data.items
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function onPage(p: number) {
  page.value = p
  void load()
}

function openCreate() {
  createForm.username = ''
  createForm.password = ''
  createForm.displayName = ''
  createForm.role = 'OPERATOR'
  createVisible.value = true
}

async function submitCreate() {
  saving.value = true
  try {
    await usersApi.createUser({ ...createForm })
    ElMessage.success('已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openEdit(row: AdminUserView) {
  editForm.adminId = row.adminId
  editForm.displayName = row.displayName
  editForm.role = row.role
  editForm.status = row.status
  editForm.builtin = row.builtin
  editVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await usersApi.updateUser(editForm.adminId, {
      displayName: editForm.displayName,
      role: editForm.builtin ? undefined : editForm.role,
      status: editForm.builtin ? undefined : editForm.status,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

function openPassword(row: AdminUserView) {
  pwdForm.adminId = row.adminId
  pwdForm.newPassword = ''
  pwdVisible.value = true
}

async function submitPassword() {
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  saving.value = true
  try {
    await usersApi.changePassword(pwdForm.adminId, pwdForm.newPassword)
    ElMessage.success('密码已更新')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function onDelete(row: AdminUserView) {
  await ElMessageBox.confirm(`确认禁用管理员「${row.username}」？`, '提示', { type: 'warning' })
  await usersApi.deleteUser(row.adminId)
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
}
.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
