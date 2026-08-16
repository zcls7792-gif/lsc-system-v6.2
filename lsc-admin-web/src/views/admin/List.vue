<template>
  <div class="page-container">
    <el-form :inline="true" :model="query" class="search-form">
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="用户名/姓名" clearable @keyup.enter="handleSearch" />
      </el-form-item>
      <el-form-item label="角色">
        <el-select v-model="query.role" placeholder="全部" clearable style="width: 150px">
          <el-option label="超级管理员" value="super_admin" />
          <el-option label="运营管理员" value="ops_admin" />
          <el-option label="技术管理员" value="tech_admin" />
          <el-option label="财务管理员" value="finance_admin" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="handleSearch">查询</el-button>
        <el-button :icon="Refresh" @click="handleReset">重置</el-button>
        <el-button type="success" :icon="Plus" @click="handleAdd">新增管理员</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" border stripe>
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="realName" label="真实姓名" width="130" />
      <el-table-column prop="role" label="角色" width="140" align="center">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)">{{ roleText(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">{{ row.status === 1 ? '正常' : '禁用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录时间" width="180" />
      <el-table-column prop="lastLoginIp" label="最后登录IP" width="140" />
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
          <el-button link type="warning" @click="toggleStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button link type="danger" :disabled="row.role === 'super_admin'" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="fetchData"
        @current-change="fetchData"
      />
    </div>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑管理员' : '新增管理员'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item v-if="!isEdit" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" placeholder="请选择" style="width: 100%">
            <el-option label="超级管理员" value="super_admin" />
            <el-option label="运营管理员" value="ops_admin" />
            <el-option label="技术管理员" value="tech_admin" />
            <el-option label="财务管理员" value="finance_admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Search, Refresh, Plus } from '@element-plus/icons-vue'
import { getAdminList, addAdmin, updateAdmin, deleteAdmin } from '@/api/admin'

const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const formRef = ref<FormInstance>()

const query = reactive({ page: 1, size: 10, keyword: '', role: '' })
const form = reactive({ id: 0, username: '', realName: '', password: '', role: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const roleText = (r: string) => ({ super_admin: '超级管理员', ops_admin: '运营管理员', tech_admin: '技术管理员', finance_admin: '财务管理员' } as any)[r]
const roleTagType = (r: string) => ({ super_admin: 'danger', ops_admin: 'primary', tech_admin: 'warning', finance_admin: 'success' } as any)[r]

async function fetchData() {
  loading.value = true
  try {
    const res: any = await getAdminList(query)
    tableData.value = res.data?.records || res.data || []
    total.value = res.data?.total || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() { query.page = 1; fetchData() }
function handleReset() { query.keyword = ''; query.role = ''; handleSearch() }

function handleAdd() {
  isEdit.value = false
  Object.assign(form, { id: 0, username: '', realName: '', password: '', role: '' })
  dialogVisible.value = true
}
function handleEdit(row: any) {
  isEdit.value = true
  Object.assign(form, { id: row.id, username: row.username, realName: row.realName, password: '', role: row.role })
  dialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) await updateAdmin(form.id, form)
      else await addAdmin(form)
    } catch (e) {
      submitting.value = false
      ElMessage.error('操作失败')
      return
    }
    submitting.value = false
    ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
    dialogVisible.value = false
    fetchData()
  })
}

async function toggleStatus(row: any) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}该管理员吗？`, '提示', { type: 'warning' })
  try {
    await updateAdmin(row.id, { status: row.status === 1 ? 0 : 1 })
  } catch (e) {
    ElMessage.error('操作失败')
    return
  }
  row.status = row.status === 1 ? 0 : 1
  ElMessage.success(`${action}成功`)
}

async function handleDelete(row: any) {
  await ElMessageBox.confirm(`确定删除管理员 ${row.username} 吗？`, '提示', { type: 'warning' })
  try {
    await deleteAdmin(row.id)
  } catch (e) {
    ElMessage.error('操作失败')
    return
  }
  ElMessage.success('删除成功')
  fetchData()
}

onMounted(fetchData)
</script>
