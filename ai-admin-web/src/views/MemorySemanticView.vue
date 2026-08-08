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
        v-model="filters.keyword"
        clearable
        placeholder="关键词 ILIKE"
        style="width: 180px"
        @keyup.enter="onFilter"
      />
      <el-input
        v-model="filters.similarQuery"
        clearable
        placeholder="相似检索（须选用户）"
        style="width: 200px"
        @keyup.enter="onFilter"
      />
      <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="onFilter">
        <el-option label="ACTIVE" value="ACTIVE" />
        <el-option label="DELETED" value="DELETED" />
        <el-option label="EXPIRED" value="EXPIRED" />
      </el-select>
      <el-button type="primary" @click="onFilter">查询</el-button>
      <el-button @click="load">刷新</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="userId" label="用户" min-width="110" />
      <el-table-column prop="content" label="正文" min-width="260" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column prop="importance" label="重要度" width="90" />
      <el-table-column prop="confidence" label="置信度" width="90" />
      <el-table-column label="标签" min-width="120">
        <template #default="{ row }">
          <el-tag v-for="t in row.tags || []" :key="t" size="small" class="tag">{{ t }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="score" label="相似分" width="90" />
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

    <el-dialog v-model="editVisible" title="编辑 Semantic" width="560px">
      <el-form label-width="100px">
        <el-form-item label="正文">
          <el-input v-model="editForm.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="ACTIVE" value="ACTIVE" />
            <el-option label="INACTIVE" value="INACTIVE" />
            <el-option label="DELETED" value="DELETED" />
            <el-option label="EXPIRED" value="EXPIRED" />
          </el-select>
        </el-form-item>
        <el-form-item label="重要度">
          <el-input-number v-model="editForm.importance" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="置信度">
          <el-input-number v-model="editForm.confidence" :min="0" :max="1" :step="0.1" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select
            v-model="editForm.tags"
            multiple
            filterable
            allow-create
            default-first-option
            style="width: 100%"
            placeholder="回车添加标签"
          />
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          title="修改正文将按当前 Embedding 配置重新向量化"
          show-icon
        />
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
import type { AdminSemanticView } from '@/api/memory'
import * as chatUsersApi from '@/api/chatUsers'
import type { AdminChatUserView } from '@/api/chatUsers'

const loading = ref(false)
const saving = ref(false)
const userLoading = ref(false)
const rows = ref<AdminSemanticView[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const userOptions = ref<AdminChatUserView[]>([])

const filters = reactive({
  userId: '' as string,
  keyword: '',
  similarQuery: '',
  status: '' as string,
})

const editVisible = ref(false)
const editForm = reactive({
  id: '',
  content: '',
  status: 'ACTIVE',
  importance: 0.5,
  confidence: 0.8,
  tags: [] as string[],
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
  if (filters.similarQuery && !filters.userId) {
    ElMessage.warning('相似检索须先选择用户')
    return
  }
  loading.value = true
  try {
    const data = await memoryApi.listSemantics({
      userId: filters.userId || undefined,
      status: filters.status || undefined,
      keyword: filters.similarQuery ? undefined : filters.keyword || undefined,
      similarQuery: filters.similarQuery || undefined,
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

function openEdit(row: AdminSemanticView) {
  editForm.id = row.id
  editForm.content = row.content
  editForm.status = row.status
  editForm.importance = row.importance
  editForm.confidence = row.confidence
  editForm.tags = [...(row.tags || [])]
  editVisible.value = true
}

async function submitEdit() {
  saving.value = true
  try {
    await memoryApi.updateSemantic(editForm.id, {
      content: editForm.content,
      status: editForm.status,
      importance: editForm.importance,
      confidence: editForm.confidence,
      tags: editForm.tags,
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(row: AdminSemanticView) {
  await ElMessageBox.confirm('软删该语义记忆？', '确认', { type: 'warning' })
  await memoryApi.deleteSemantic(row.id)
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
.tag {
  margin-right: 4px;
}
</style>
