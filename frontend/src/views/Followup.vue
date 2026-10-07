<template>
  <div class="page-container">
    <el-card>
      <el-radio-group v-model="status" style="margin-bottom:10px" @change="load">
        <el-radio-button value="PLANNED">待复查</el-radio-button>
        <el-radio-button value="NO_SHOW">未到场待催办</el-radio-button>
        <el-radio-button value="DONE">已完成</el-radio-button>
        <el-radio-button value="ALL">全部</el-radio-button>
      </el-radio-group>
      <el-table :data="rows" stripe size="small">
        <el-table-column prop="studentName" label="学生" width="90" />
        <el-table-column prop="schoolName" label="学校" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.kind === 'ADAPT' ? 'warning' : 'info'">{{ row.kindLabel || '周期复查' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="plannedDate" label="计划复查" width="110" />
        <el-table-column prop="actualDate" label="实际复查" width="120" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status==='DONE'?'success':row.status==='NO_SHOW'?'danger':'warning'">
              {{ {DONE:'已完成',NO_SHOW:'未到场',PLANNED:'待复查'}[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="orgName" label="复查机构" width="160" />
        <el-table-column prop="visionResult" label="结果" min-width="140" />
        <el-table-column prop="remindCount" label="催办次数" width="90" />
        <el-table-column label="操作" width="210">
          <template #default="{ row }">
            <el-button v-if="row.status!=='DONE'" size="small" type="success"
                       @click="act(row,'DONE')">完成复查</el-button>
            <el-button v-if="row.status!=='DONE'" size="small" type="danger" plain
                       @click="act(row,'NO_SHOW')">未到场催办</el-button>
            <el-button link size="small" @click="detail(row)">档案</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const rows = ref([])
const status = ref('PLANNED')

const load = async () => { rows.value = await api.followups(status.value) }

const act = (row, action) => {
  const isDone = action === 'DONE'
  ElMessageBox.prompt(isDone ? '请填写本次视力结果' : '催办备注（半年复查未到场将登记异常事项）',
    isDone ? '完成复查登记' : '未到场催办', {
      confirmButtonText: '确认', cancelButtonText: '取消',
      inputPlaceholder: isDone ? '如：双眼矫正视力5.0，度数稳定' : '已电话/短信通知家长'
    }).then(async ({ value }) => {
      await api.followup({
        studentId: row.studentId, action,
        actualDate: isDone ? new Date().toISOString().slice(0, 10) : null,
        plannedDate: isDone ? null : row.plannedDate,
        orgName: row.orgName, visionResult: isDone ? value : null,
        remark: isDone ? '复查正常' : value
      })
      ElMessage.success('已登记')
      load()
    }).catch(() => {})
}
const detail = (row) => router.push(`/students/${row.studentId}`)
onMounted(load)
</script>
