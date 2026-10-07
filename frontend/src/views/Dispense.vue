<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="合作门店配镜补助：门店上传处方、镜片参数、付款凭证、取镜签收 → 平台对照最近一次复诊处方、家庭资格与补助标准逐项核验 → 确认同一学生、同一副眼镜后方可发放；退镜/换片/换架按实际退款与新费用重算差额，不适反馈由门店上传调整结果并继续提醒复查与适应回访。" />
    <el-card>
      <el-tabs v-model="tab">
        <!-- ============ 核验单台账 ============ -->
        <el-tab-pane label="发放核验单" name="dispense">
          <el-radio-group v-model="dispenseStatus" style="margin-bottom:10px" @change="loadDispenses">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="PASSED">核验通过</el-radio-button>
            <el-radio-button value="SUSPENDED">核验挂起</el-radio-button>
          </el-radio-group>
          <el-table :data="dispenseRows" stripe size="small">
            <el-table-column prop="studentName" label="学生" width="80" />
            <el-table-column prop="schoolName" label="学校" min-width="150" />
            <el-table-column prop="storeName" label="合作门店" width="140" />
            <el-table-column label="实收/核定补助" width="130">
              <template #default="{ row }">{{ row.totalAmount }} / {{ row.standardAmount }} 元</template>
            </el-table-column>
            <el-table-column label="核验状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="{ PASSED: 'success', SUSPENDED: 'danger', PENDING: 'warning' }[row.status]">
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="补助状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.subsidyStatus" size="small"
                        :type="{ PAID: 'success', APPROVED: 'warning', RECOVERED: 'info' }[row.subsidyStatus] || 'info'">
                  {{ row.subsidyStatusLabel }}
                </el-tag>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="suspendReason" label="挂起原因" min-width="200">
              <template #default="{ row }"><span class="danger-text">{{ row.suspendReason || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="操作" width="330">
              <template #default="{ row }">
                <el-button size="small" @click="showDetail(row)">核验明细</el-button>
                <el-button v-if="row.status === 'PASSED' && row.subsidyStatus === 'APPROVED'"
                           size="small" type="success" @click="pay(row)">确认发放</el-button>
                <el-button v-if="row.status === 'SUSPENDED'" size="small" type="warning"
                           @click="resubmit(row)">更正重提</el-button>
                <el-button v-if="row.subsidyStatus === 'PAID'" size="small" type="danger" plain
                           @click="openAfterSales(row)">售后登记</el-button>
                <el-button v-if="row.status === 'PASSED'" size="small" type="primary" plain
                           @click="openFeedback(row)">不适反馈</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 门店上传材料 ============ -->
        <el-tab-pane :label="resubmitId ? '更正核验材料（重新提交）' : '门店上传核验材料'" name="upload">
          <el-form :model="up" label-width="120px" style="max-width:860px">
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="学生" required>
                  <el-select v-model="up.studentId" filterable placeholder="选择学生" style="width:100%"
                             :disabled="!!resubmitId" @change="loadStudentGlasses">
                    <el-option v-for="s in studentOptions" :key="s.id"
                               :label="`${s.name}（${s.schoolName} ${s.grade}${s.className}）`" :value="s.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="配镜记录" required>
                  <el-select v-model="up.glassesId" style="width:100%" :disabled="!!resubmitId"
                             placeholder="选择该学生的合作门店配镜记录（同一副眼镜）">
                    <el-option v-for="g in glassesOptions" :key="g.id"
                               :label="`#${g.id} ${g.opticianOrg} ${g.glassesDate} ${g.amount}元`" :value="g.id" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="合作门店"><el-input v-model="up.storeName" /></el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="付款凭证号" required>
                  <el-input v-model="up.paymentVoucherNo" placeholder="平台将查重，重复凭证自动挂起" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">门店上传处方（应与最近一次复诊处方一致）</el-divider>
            <el-row :gutter="10">
              <el-col :span="6"><el-form-item label="球镜左"><el-input v-model="up.rxSphereLeft" placeholder="-1.50" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="球镜右"><el-input v-model="up.rxSphereRight" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="柱镜左"><el-input v-model="up.rxCylinderLeft" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="柱镜右"><el-input v-model="up.rxCylinderRight" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="轴位左"><el-input v-model="up.rxAxisLeft" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="轴位右"><el-input v-model="up.rxAxisRight" /></el-form-item></el-col>
              <el-col :span="6"><el-form-item label="瞳距(mm)"><el-input v-model="up.pd" /></el-form-item></el-col>
            </el-row>

            <el-divider content-position="left">镜片参数与费用（补助标准：实收50%、封顶300元）</el-divider>
            <el-row :gutter="10">
              <el-col :span="12">
                <el-form-item label="镜片参数" required>
                  <el-input v-model="up.lensParams" placeholder="如 1.60防蓝光非球面" />
                </el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="镜片费(元)"><el-input-number v-model="up.lensAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="镜架费(元)"><el-input-number v-model="up.frameAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="4">
                <el-form-item label="实收合计(元)"><el-input-number v-model="up.totalAmount" :min="0" :precision="2" style="width:100%" /></el-form-item>
              </el-col>
            </el-row>

            <el-divider content-position="left">取镜签收</el-divider>
            <el-row :gutter="10">
              <el-col :span="6">
                <el-form-item label="已签收"><el-switch v-model="up.pickupSigned" /></el-form-item>
              </el-col>
              <el-col :span="9">
                <el-form-item label="签收日期"><el-date-picker v-model="up.pickupDate" value-format="YYYY-MM-DD" style="width:100%" /></el-form-item>
              </el-col>
              <el-col :span="9">
                <el-form-item label="签收人"><el-input v-model="up.pickupSigner" placeholder="家长/学生姓名" /></el-form-item>
              </el-col>
            </el-row>

            <el-form-item>
              <el-button type="primary" @click="submitUpload">
                {{ resubmitId ? '更正并重新核验' : '提交并自动核验' }}
              </el-button>
              <el-button v-if="resubmitId" @click="cancelResubmit">取消更正</el-button>
              <span class="muted" style="margin-left:10px">
                提交后平台立即逐项核验：同一学生/同一副眼镜/材料完整/处方一致/家庭资格/凭证唯一/补助标准
              </span>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <!-- ============ 售后台账 ============ -->
        <el-tab-pane label="售后与差额结算" name="afterSales">
          <el-radio-group v-model="asStatus" style="margin-bottom:10px" @change="loadAfterSales">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="PENDING_SETTLE">待结算</el-radio-button>
            <el-radio-button value="SETTLED">已结算</el-radio-button>
            <el-radio-button value="WAIT_ADJUST">待门店调整</el-radio-button>
            <el-radio-button value="ADJUSTED">已调整</el-radio-button>
          </el-radio-group>
          <el-table :data="asRows" stripe size="small">
            <el-table-column prop="studentName" label="学生" width="80" />
            <el-table-column prop="schoolName" label="学校" min-width="140" />
            <el-table-column prop="typeLabel" label="售后类型" width="90" />
            <el-table-column prop="reason" label="原因/反馈" min-width="170" />
            <el-table-column label="退款/新费用" width="110">
              <template #default="{ row }">
                <span v-if="row.type !== 'ADJUST'">{{ row.refundAmount ?? '—' }} / {{ row.newAmount ?? '—' }} 元</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="原补助→新应补" width="120">
              <template #default="{ row }">
                <span v-if="row.oldSubsidy != null">{{ row.oldSubsidy }} → {{ row.newSubsidy }} 元</span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="差额" width="100">
              <template #default="{ row }">
                <span v-if="row.difference != null" :class="row.difference < 0 ? 'danger-text' : 'ok-text'">
                  {{ row.difference < 0 ? '追回 ' + (-row.difference) : row.difference > 0 ? '补发 ' + row.difference : '无差额' }}
                </span>
                <span v-else class="muted">—</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <el-tag size="small"
                        :type="{ PENDING_SETTLE: 'danger', SETTLED: 'success', WAIT_ADJUST: 'warning', ADJUSTED: 'success' }[row.status]">
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="adjustResult" label="门店调整结果" min-width="160">
              <template #default="{ row }">{{ row.adjustResult || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button v-if="row.status === 'PENDING_SETTLE'" size="small" type="success"
                           @click="settle(row)">确认结算</el-button>
                <el-button v-if="row.status === 'WAIT_ADJUST'" size="small" type="primary"
                           @click="uploadAdjust(row)">上传调整结果</el-button>
                <el-button link size="small" @click="detail(row)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 核验明细 -->
    <el-dialog v-model="detailVisible" title="逐项核验明细" width="640px">
      <template v-if="current">
        <el-descriptions :column="3" border size="small" style="margin-bottom:10px">
          <el-descriptions-item label="学生">{{ current.studentName }}</el-descriptions-item>
          <el-descriptions-item label="门店">{{ current.storeName }}</el-descriptions-item>
          <el-descriptions-item label="凭证号">{{ current.paymentVoucherNo }}</el-descriptions-item>
          <el-descriptions-item label="处方">球 {{ current.rxSphereLeft }}/{{ current.rxSphereRight }} 柱 {{ current.rxCylinderLeft }}/{{ current.rxCylinderRight }} 轴 {{ current.rxAxisLeft }}/{{ current.rxAxisRight }} 瞳距 {{ current.pd }}</el-descriptions-item>
          <el-descriptions-item label="镜片参数">{{ current.lensParams }}</el-descriptions-item>
          <el-descriptions-item label="取镜签收">{{ current.pickupSigned ? `已签收（${current.pickupSigner || '—'}）` : '未签收' }}</el-descriptions-item>
        </el-descriptions>
        <div class="verify-detail">
          <div v-for="(line, i) in (current.verifyDetail || '').split('\n')" :key="i"
               :class="line.startsWith('×') ? 'danger-text' : 'ok-text'">{{ line }}</div>
        </div>
      </template>
    </el-dialog>

    <!-- 售后登记（退镜/换片/换架） -->
    <el-dialog v-model="asVisible" title="售后登记（与原补助关联，自动重算差额）" width="520px">
      <el-form label-width="120px">
        <el-form-item label="售后类型">
          <el-radio-group v-model="asf.type">
            <el-radio value="RETURN">退镜</el-radio>
            <el-radio value="LENS_CHANGE">更换镜片</el-radio>
            <el-radio value="FRAME_CHANGE">更换镜架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原因"><el-input v-model="asf.reason" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="实际退款(元)"><el-input-number v-model="asf.refundAmount" :min="0" :precision="2" /></el-form-item>
        <el-form-item v-if="asf.type !== 'RETURN'" label="新费用合计(元)">
          <el-input-number v-model="asf.newAmount" :min="0" :precision="2" />
          <div class="muted">换片/换架后按新费用重算应补（50%、封顶300元）</div>
        </el-form-item>
        <el-alert v-if="asf.type === 'RETURN'" type="warning" :closable="false"
                  title="退镜后应补为 0，差额全部从原补助中追回。" />
      </el-form>
      <template #footer>
        <el-button @click="asVisible = false">取消</el-button>
        <el-button type="danger" @click="submitAfterSales">登记并重算差额</el-button>
      </template>
    </el-dialog>

    <!-- 不适反馈 -->
    <el-dialog v-model="fbVisible" title="学生不适反馈（通知门店调整）" width="480px">
      <el-form label-width="90px">
        <el-form-item label="不适反馈">
          <el-checkbox-group v-model="fbTags">
            <el-checkbox value="头晕">头晕</el-checkbox>
            <el-checkbox value="看不清">看不清</el-checkbox>
            <el-checkbox value="镜架不合适">镜架不合适</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
        <el-form-item label="补充说明"><el-input v-model="fbText" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="fbVisible = false">取消</el-button>
        <el-button type="primary" @click="submitFeedback">登记并通知门店</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const tab = ref('dispense')
const dispenseStatus = ref('')
const asStatus = ref('')
const dispenseRows = ref([])
const asRows = ref([])
const studentOptions = ref([])
const glassesOptions = ref([])
const detailVisible = ref(false)
const current = ref(null)
const resubmitId = ref(null)

const blankUp = () => ({
  studentId: null, glassesId: null, storeName: '阳光眼镜合作门店',
  rxSphereLeft: '', rxSphereRight: '', rxCylinderLeft: '', rxCylinderRight: '',
  rxAxisLeft: '', rxAxisRight: '', pd: '',
  lensParams: '', lensAmount: null, frameAmount: null, totalAmount: null,
  paymentVoucherNo: '', pickupSigned: false, pickupDate: null, pickupSigner: ''
})
const up = ref(blankUp())

const asVisible = ref(false)
const asf = ref({ dispenseId: null, type: 'RETURN', reason: '', refundAmount: 0, newAmount: null })
const fbVisible = ref(false)
const fbTags = ref([])
const fbText = ref('')
const fbDispenseId = ref(null)

const loadDispenses = async () => { dispenseRows.value = await api.dispenses(dispenseStatus.value || 'ALL') }
const loadAfterSales = async () => { asRows.value = await api.afterSales(asStatus.value || 'ALL') }
const loadStudents = async () => { studentOptions.value = await api.students({}) }
const reload = () => Promise.all([loadDispenses(), loadAfterSales()])

const loadStudentGlasses = async (sid) => {
  up.value.glassesId = null
  glassesOptions.value = []
  if (!sid) return
  const d = await api.studentDetail(sid)
  glassesOptions.value = (d.glasses || []).filter(g => g.partnerOptician)
  if (glassesOptions.value.length === 1) up.value.glassesId = glassesOptions.value[0].id
  if (!glassesOptions.value.length) ElMessage.warning('该学生暂无合作门店配镜记录，请先在档案中登记配镜')
}

const submitUpload = async () => {
  if (!up.value.studentId || !up.value.glassesId) {
    ElMessage.warning('请选择学生与配镜记录')
    return
  }
  try {
    if (resubmitId.value) {
      await api.dispenseResubmit(resubmitId.value, up.value)
      ElMessage.success('已重新提交核验')
    } else {
      await api.dispenseSubmit(up.value)
      ElMessage.success('已提交并完成自动核验')
    }
    cancelResubmit()
    up.value = blankUp()
    glassesOptions.value = []
    tab.value = 'dispense'
    await reload()
  } catch (e) { /* 拦截器已提示 */ }
}

const showDetail = (row) => { current.value = row; detailVisible.value = true }

const pay = (row) => {
  ElMessageBox.confirm(`确认向 ${row.studentName} 发放配镜补助 ${row.standardAmount} 元？（同一副眼镜仅发放一次）`,
    '确认发放', { confirmButtonText: '确认发放', cancelButtonText: '取消', type: 'warning' })
    .then(async () => {
      await api.dispensePay(row.id)
      ElMessage.success('补助已发放')
      reload()
    }).catch(() => {})
}

const resubmit = (row) => {
  resubmitId.value = row.id
  up.value = {
    studentId: row.studentId, glassesId: row.glassesId, storeName: row.storeName,
    rxSphereLeft: row.rxSphereLeft || '', rxSphereRight: row.rxSphereRight || '',
    rxCylinderLeft: row.rxCylinderLeft || '', rxCylinderRight: row.rxCylinderRight || '',
    rxAxisLeft: row.rxAxisLeft || '', rxAxisRight: row.rxAxisRight || '', pd: row.pd || '',
    lensParams: row.lensParams || '', lensAmount: row.lensAmount, frameAmount: row.frameAmount,
    totalAmount: row.totalAmount, paymentVoucherNo: row.paymentVoucherNo || '',
    pickupSigned: !!row.pickupSigned, pickupDate: row.pickupDate, pickupSigner: row.pickupSigner || ''
  }
  tab.value = 'upload'
  ElMessage.warning('请更正被挂起的材料后重新提交核验：' + (row.suspendReason || ''))
}
const cancelResubmit = () => { resubmitId.value = null }

const openAfterSales = (row) => {
  asf.value = { dispenseId: row.id, type: 'RETURN', reason: '', refundAmount: row.totalAmount, newAmount: null }
  asVisible.value = true
}
const submitAfterSales = async () => {
  try {
    await api.afterSalesRegister(asf.value)
    asVisible.value = false
    ElMessage.success('售后已登记，差额待卫健结算')
    tab.value = 'afterSales'
    reload()
  } catch (e) { /* 拦截器已提示 */ }
}

const settle = (row) => {
  const text = row.difference < 0 ? `追回 ${-row.difference} 元` : row.difference > 0 ? `补发 ${row.difference} 元` : '无差额'
  ElMessageBox.confirm(`确认按售后重算结果结算（${text}），并更新原补助？`, '确认结算',
    { confirmButtonText: '确认结算', cancelButtonText: '取消', type: 'warning' })
    .then(async () => {
      await api.afterSalesSettle(row.id)
      ElMessage.success('结算完成，原补助已更新')
      reload()
    }).catch(() => {})
}

const openFeedback = (row) => {
  fbDispenseId.value = row.id
  fbTags.value = []
  fbText.value = ''
  fbVisible.value = true
}
const submitFeedback = async () => {
  const feedback = [...fbTags.value, fbText.value].filter(Boolean).join('；')
  if (!feedback) { ElMessage.warning('请填写不适反馈'); return }
  try {
    await api.afterSalesFeedback({ dispenseId: fbDispenseId.value, feedback })
    fbVisible.value = false
    ElMessage.success('已登记并通知门店调整')
    tab.value = 'afterSales'
    reload()
  } catch (e) { /* 拦截器已提示 */ }
}

const uploadAdjust = (row) => {
  ElMessageBox.prompt('请填写门店调整结果（平台将保留售后记录，并继续提醒复查与适应回访）',
    '上传调整结果', { confirmButtonText: '提交', cancelButtonText: '取消', inputType: 'textarea' })
    .then(async ({ value }) => {
      await api.afterSalesAdjust(row.id, value || '已调整')
      ElMessage.success('调整结果已上传，已建立适应回访提醒')
      reload()
    }).catch(() => {})
}

const detail = (row) => router.push(`/students/${row.studentId}`)

onMounted(() => { reload(); loadStudents() })
</script>

<style scoped>
.muted { color: #909399; font-size: 12px; }
.danger-text { color: #f56c6c; }
.ok-text { color: #67c23a; }
.verify-detail { background: #f7f9fb; border-radius: 6px; padding: 10px 14px; font-size: 13px; line-height: 1.9; }
</style>
