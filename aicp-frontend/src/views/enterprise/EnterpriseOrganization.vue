<template>
  <div class="enterprise-org">
    <h2>组织与成员</h2>

    <el-row :gutter="24">
      <!-- Department tree -->
      <el-col :span="6">
        <el-card>
          <template #header>
            <span>部门</span>
            <el-button size="small" type="primary" link @click="showCreateDept = true">+ 新建</el-button>
          </template>
          <el-tree
            :data="departmentTree"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            highlight-current
            @node-click="onDeptSelect"
          />
        </el-card>
      </el-col>

      <!-- Member list -->
      <el-col :span="18">
        <el-card>
          <template #header>
            <span>成员</span>
            <el-button size="small" type="primary" link @click="showInvite = true">+ 邀请</el-button>
          </template>
          <el-table :data="members" stripe v-loading="loading">
            <el-table-column prop="user_id" label="ID" width="80" />
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.status === 'active' ? 'success' : 'info'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="department_id" label="部门" width="120" />
            <el-table-column prop="role_id" label="角色" width="120" />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button size="small" @click="editMember(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            v-model:current-page="page"
            :page-size="size"
            :total="total"
            layout="prev,pager,next"
            @current-change="fetchMembers"
          />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { enterpriseApi } from '@/api/enterprise'

const departmentTree = ref([])
const members = ref([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)

const showCreateDept = ref(false)
const showInvite = ref(false)

function onDeptSelect() {}

function editMember() {}

async function fetchDepartments() {
  try {
    const res = await enterpriseApi.listDepartments()
    if (res?.data?.items) departmentTree.value = res.data.items
  } catch {}
}

async function fetchMembers() {
  loading.value = true
  try {
    const res = await enterpriseApi.listMembers({ page: page.value, size: size.value })
    if (res?.data) {
      members.value = res.data.items || []
      total.value = res.data.total || 0
    }
  } catch {} finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchDepartments()
  fetchMembers()
})
</script>
