<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" style="margin-bottom:10px"
      title="合作门店配镜补助须先在「发放核验」完成材料核验（处方/镜片参数/付款凭证/取镜签收）后再发放；退镜换片差额与不适调整也在「发放核验」处理。">
      <template #default>
        <el-button size="small" type="primary" plain @click="$router.push('/dispense')">前往发放核验</el-button>
      </template>
    </el-alert>
    <el-card>
      <el-tabs v-model="tab">
        <el-tab-pane label="补助申请与核准" name="apply">
          <el-radio-group v-model="statusFilter" style="margin-bottom:10px" @change="loadSubsidies">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="APPLIED">已申请待核</el-radio-button>
            <el-radio-button value="DISPUTE">争议复核</el-radio-button>
            <el-radio-button value="APPROVED">已核准</el-radio-button>
            <el-radio-button value="PAID">已发放</el-radio-button>
            <el-radio-button value="REJECTED">已驳回</el-radio-button>
          </el-radio-group>
          <el-table :data="subsidies" stripe size="small">
            <el-table-column prop="studentName" label="学生" width="90" />
            <el-table-column prop="schoolName" label="学校" min-width="170" />
            <el-table-column label="申请金额" width="100">
              <template #default="{ row }">{{ row.applyAmount }} 元</template>
            </el-table-column>
            <el-table-column label="核准金额" width="100">
              <template #default="{ row }">{{ row.approvedAmount ? row.approvedAmount + ' 元' : '—' }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="tagType(row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="opinion" label="意见/争议原因" min-width="180" />
            <el-table-column label="审批操作（卫健部门）" width="300">
              <template #default="{ row }">
                <template v-if="['APPLIED','DISPUTE'].includes(row.status)">
                  <el-button size="small" type="success" @click="approve(row,'APPROVED')">核准</el-button>
                  <el-button size="small" type="warning" @click="approve(row,'DISPUTE')">标记争议</el-button>
                  <el-button size="small" type="danger" plain @click="approve(row,'REJECTED')">驳回</el-button>
                </template>
                <el-button v-if="['APPROVED'].includes(row.status)" size="small" type="primary"
                           @click="approve(row,'PAID')">确认发放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="待申请补助学生（配镜登记）" name="glasses">
          <el-alert type="warning" :closable="false" style="margin-bottom:10px"
            title="家长自行外配眼镜需核验医学验光凭证，存在争议时由卫健部门复核，避免不合规补助。" />
          <el-table :data="candidates" stripe size="small">
            <el-table-column prop="name" label="姓名" width="90" />
            <el-table-column label="学校/班级" min-width="190">
              <template #default="{ row }">{{ row.schoolName }} {{ row.grade }}{{ row.className }}</template>
            </el-table-column>
            <el-table-column prop="stageLabel" label="状态" width="110" />
            <el-table-column label="异常标记" min-width="160">
              <template #default="{ row }">
                <el-tag v-for="(f,i) in row.flagLabels" :key="i" type="danger" size="small" style="margin:2px">{{ f }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <el-button size="small" @click="pick(row,'glasses')">配镜登记</el-button>
                <el-button size="small" type="warning" @click="pick(row,'subsidy')">补助申请</el-button>
                <el-button link size="small" @click="detail(row)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
    <action-dialogs ref="dialogs" :student-id="sid" @done="reload" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { api } from '../api'
import ActionDialogs from '../components/ActionDialogs.vue'

const router = useRouter()
const tab = ref('apply')
const statusFilter = ref('')
const subsidies = ref([])
const candidates = ref([])
const sid = ref(null)
const dialogs = ref()

const tagType = (s) => ({ PAID: 'success', APPROVED: '', DISPUTE: 'danger', REJECTED: 'info', APPLIED: 'warning' }[s])

const loadSubsidies = async () => {
  subsidies.value = await api.subsidies(statusFilter.value || 'ALL')
}
const loadCandidates = async () => {
  const all = await api.students({})
  candidates.value = all.filter(s => ['GLASSES_SUBSIDY','MANAGING','VISITED'].includes(s.stage))
}
const reload = async () => { await Promise.all([loadSubsidies(), loadCandidates()]) }

const approve = (row, action) => {
  const labels = { APPROVED: '核准补助', DISPUTE: '标记为争议复核', REJECTED: '驳回申请', PAID: '确认补助发放' }
  ElMessageBox.prompt(labels[action] + '（金额/意见）', labels[action], {
    confirmButtonText: '确认', cancelButtonText: '取消',
    inputValue: action === 'APPROVED' || action === 'PAID' ? String(row.applyAmount || 300) : '',
    inputPlaceholder: '可填写核准金额或审批意见'
  }).then(async ({ value }) => {
    const amount = (action === 'APPROVED' || action === 'PAID') && value && !isNaN(Number(value))
      ? Number(value) : null
    // 若填的是纯数字视为金额，否则视为意见
    const opinion = amount ? '核准' : value
    await api.subsidyApprove({ applicationId: row.id, action, approvedAmount: amount, opinion })
    ElMessage.success('已提交审批结果')
    loadSubsidies()
  }).catch(() => {})
}

const pick = (row, name) => { sid.value = row.id; dialogs.value.open(name) }
const detail = (row) => router.push(`/students/${row.id}`)

onMounted(reload)
</script>
