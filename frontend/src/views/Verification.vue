<template>
  <div class="page-container">
    <el-alert type="info" :closable="false" style="margin-bottom:12px"
      title="合作门店配镜补助发放核验：门店上传处方、镜片参数、付款凭证、取镜签收，平台对照最近一次复诊处方、家庭资格与补助标准逐项核验；处方不一致、参数缺失、付款凭证重复或门店重复提交一律先挂起，不直接发两次补助。" />

    <el-card>
      <el-tabs v-model="tab">
        <!-- ============ 1. 发放核验台账 ============ -->
        <el-tab-pane name="verify">
          <template #label>发放核验台账
            <el-badge v-if="suspendedCount" :value="suspendedCount" type="danger" style="margin-left:6px" />
          </template>
          <div class="toolbar">
            <el-radio-group v-model="vStatus" @change="loadVerifications">
              <el-radio-button value="ALL">全部</el-radio-button>
              <el-radio-button value="SUSPENDED">挂起待复核</el-radio-button>
              <el-radio-button value="PASSED">通过待发放</el-radio-button>
              <el-radio-button value="PAID">已发放</el-radio-button>
              <el-radio-button value="AFTER_SALES">售后处理中</el-radio-button>
              <el-radio-button value="CLOSED">售后结清</el-radio-button>
              <el-radio-button value="REJECTED">已驳回</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="openStoreDialog()">门店上传材料/提交核验</el-button>
          </div>

          <el-table :data="verifications" stripe size="small" :row-class-name="rowClass">
            <el-table-column prop="studentName" label="学生" width="80" />
            <el-table-column prop="schoolName" label="学校" min-width="150" show-overflow-tooltip />
            <el-table-column prop="opticianOrg" label="门店" min-width="130" show-overflow-tooltip />
            <el-table-column label="费用/凭证" width="150">
              <template #default="{ row }">
                <div>{{ row.glassesAmount }} 元</div>
                <div class="muted">{{ row.paymentVoucherNo }}</div>
              </template>
            </el-table-column>
            <el-table-column label="七项逐项核验" width="225">
              <template #default="{ row }">
                <el-tooltip v-for="c in checkCells(row)" :key="c.k" :content="c.label" placement="top">
                  <span class="dot" :class="dotClass(c.result)">{{ c.short }}</span>
                </el-tooltip>
                <el-button link type="primary" size="small" @click="showDetail(row)">明细</el-button>
              </template>
            </el-table-column>
            <el-table-column label="应发/实发" width="110">
              <template #default="{ row }">
                <div>{{ row.payableAmount == null ? '—' : row.payableAmount + ' 元' }}</div>
                <div class="muted">{{ row.paidAmount == null ? '' : '已发 ' + row.paidAmount }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="vTag(row.status)">{{ row.statusLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="285" fixed="right">
              <template #default="{ row }">
                <el-button v-if="row.status==='SUSPENDED'" size="small" type="warning"
                           @click="openStoreDialog(row)">门店补材料</el-button>
                <el-button v-if="row.status==='SUSPENDED'" size="small" type="success"
                           @click="review(row,'RELEASE')">复核放行</el-button>
                <el-button v-if="row.status==='SUSPENDED'" size="small" type="danger" plain
                           @click="review(row,'REJECT')">驳回</el-button>
                <el-button v-if="row.status==='PASSED'" size="small" type="primary"
                           @click="pay(row)">确认发放</el-button>
                <el-button v-if="row.status==='PAID' || row.status==='AFTER_SALES'" size="small"
                           type="warning" @click="openAfterSales(row)">登记退镜/换片/换架</el-button>
                <el-button link size="small" @click="goDetail(row)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 2. 售后差额 ============ -->
        <el-tab-pane name="after">
          <template #label>售后差额结算
            <el-badge v-if="afterOpenCount" :value="afterOpenCount" type="danger" style="margin-left:6px" />
          </template>
          <el-alert type="warning" :closable="false" style="margin-bottom:10px"
            title="补助发放后家长退镜、换片或只更换镜架：平台按门店实际退款与新费用重算应补助，自动给出应追回/应补发差额，并与原补助、原核验单连在一起，差额结清后才关闭。" />
          <el-radio-group v-model="aStatus" style="margin-bottom:10px" @change="loadAfter">
            <el-radio-button value="ALL">全部</el-radio-button>
            <el-radio-button value="REQUESTED">差额待结清</el-radio-button>
            <el-radio-button value="SETTLED">已结清</el-radio-button>
          </el-radio-group>
          <el-table :data="afterRows" stripe size="small">
            <el-table-column prop="studentName" label="学生" width="80" />
            <el-table-column prop="schoolName" label="学校" min-width="150" show-overflow-tooltip />
            <el-table-column label="售后类型" width="110">
              <template #default="{ row }"><el-tag size="small" type="warning">{{ row.typeLabel }}</el-tag></template>
            </el-table-column>
            <el-table-column label="原费用→退款/新费用" width="190">
              <template #default="{ row }">
                退款 <b>{{ row.refundAmount }}</b> ｜ 新费用 <b>{{ row.newCostAmount }}</b>
              </template>
            </el-table-column>
            <el-table-column label="原补助→重算" width="140">
              <template #default="{ row }">
                {{ row.originalSubsidy }} → <b>{{ row.recalculatedSubsidy }}</b> 元
              </template>
            </el-table-column>
            <el-table-column label="差额方向/金额" width="130">
              <template #default="{ row }">
                <el-tag size="small" :type="row.direction==='RECOVER'?'danger':row.direction==='SUPPLEMENT'?'success':'info'">
                  {{ row.directionLabel }} {{ row.settleAmount }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status==='SETTLED'?'success':'danger'">
                  {{ row.status==='SETTLED' ? '已结清' : '待结清' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150">
              <template #default="{ row }">
                <el-button v-if="row.status!=='SETTLED'" size="small" type="primary"
                           @click="settle(row)">登记结清</el-button>
                <el-button link size="small" @click="goDetail(row)">档案</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 3. 不适调整与适应回访 ============ -->
        <el-tab-pane name="comfort">
          <template #label>配镜不适与适应回访
            <el-badge v-if="comfortOpenCount" :value="comfortOpenCount" type="warning" style="margin-left:6px" />
          </template>
          <el-alert type="warning" :closable="false" style="margin-bottom:10px"
            title="学生反馈头晕、看不清或镜架不合适时，门店须上传调整结果；平台保留售后记录，并继续提醒复查与 7 天戴镜适应回访，回访确认适应后才闭环。" />
          <div class="toolbar">
            <el-radio-group v-model="cStatus" @change="loadComfort">
              <el-radio-button value="ALL">全部</el-radio-button>
              <el-radio-button value="PLANNED">待适应回访</el-radio-button>
              <el-radio-button value="DONE">已回访</el-radio-button>
            </el-radio-group>
            <el-button type="primary" @click="openFeedback">学生反馈不适</el-button>
          </div>
          <el-table :data="comfortRows" stripe size="small">
            <el-table-column prop="studentName" label="学生" width="80" />
            <el-table-column prop="schoolName" label="学校" min-width="140" show-overflow-tooltip />
            <el-table-column label="不适类型" width="100">
              <template #default="{ row }"><el-tag size="small" type="danger">{{ row.discomfortTypeLabel }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="feedback" label="学生反馈" min-width="150" show-overflow-tooltip />
            <el-table-column prop="adjustResult" label="门店调整结果" min-width="170" show-overflow-tooltip />
            <el-table-column label="复查提醒" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.reviewAdvised" size="small" type="warning">{{ row.reviewPlannedDate }} 前</el-tag>
                <span v-else class="muted">无</span>
              </template>
            </el-table-column>
            <el-table-column label="适应回访" width="150">
              <template #default="{ row }">
                <div :class="overdue(row.callbackDueDate) && row.callbackStatus==='PLANNED' ? 'danger-text' : ''">
                  应在 {{ row.callbackDueDate }} 前
                </div>
                <div class="muted">{{ row.callbackDate ? row.callbackDate + ' ' + (row.callbackResult||'') : '待回访' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态/操作" width="210">
              <template #default="{ row }">
                <el-button size="small" @click="openAdjust(row)">上传调整</el-button>
                <el-button size="small" type="success" :disabled="!row.adjustResult"
                           @click="openCallback(row)">适应回访</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- ===== 门店上传四类材料 ===== -->
    <el-dialog v-model="storeDlg" :title="storeForm.glassesId ? '门店补材料重新提交核验' : '合作门店上传配镜材料并提交发放核验'" width="860px" top="4vh">
      <el-form :model="storeForm" label-width="120px">
        <el-row :gutter="10">
          <el-col v-if="!storeForm.glassesId" :span="12">
            <el-form-item label="学生" required>
              <el-select v-model="storeForm.studentId" filterable placeholder="选择已复诊待配镜学生" style="width:100%">
                <el-option v-for="s in candidateStudents" :key="s.id"
                           :label="s.name + '（' + s.schoolName + ' ' + s.grade + s.className + '）'" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12"><el-form-item label="配镜门店"><el-input v-model="storeForm.opticianOrg"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="配镜日期"><el-date-picker v-model="storeForm.glassesDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="配镜金额(元)"><el-input-number v-model="storeForm.amount" :min="0" :precision="2" style="width:100%"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="镜片品类"><el-input v-model="storeForm.lensBrand" placeholder="如 1.56防蓝光树脂片"/></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">材料一：验光处方（须与最近一次复诊处方一致，处方号）</el-divider>
        <el-row :gutter="10">
          <el-col :span="6"><el-form-item label="处方号" label-width="90px"><el-input v-model="storeForm.rxNo"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="球镜左" label-width="80px"><el-input v-model="storeForm.rxSphereLeft" placeholder="-1.50"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="球镜右" label-width="80px"><el-input v-model="storeForm.rxSphereRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="瞳距" label-width="80px"><el-input v-model="storeForm.rxPd" placeholder="60"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜左" label-width="90px"><el-input v-model="storeForm.rxCylinderLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜右" label-width="80px"><el-input v-model="storeForm.rxCylinderRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位左" label-width="80px"><el-input v-model="storeForm.rxAxisLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位右" label-width="80px"><el-input v-model="storeForm.rxAxisRight"/></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">材料二：镜片实际加工参数（可一键同处方）</el-divider>
        <el-button size="small" style="margin-left:120px;margin-bottom:8px" @click="sameAsRx">加工参数同处方</el-button>
        <el-row :gutter="10">
          <el-col :span="6"><el-form-item label="球镜左" label-width="90px"><el-input v-model="storeForm.lensSphereLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="球镜右" label-width="80px"><el-input v-model="storeForm.lensSphereRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="瞳距" label-width="80px"><el-input v-model="storeForm.lensPd"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜左" label-width="80px"><el-input v-model="storeForm.lensCylinderLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="柱镜右" label-width="90px"><el-input v-model="storeForm.lensCylinderRight"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位左" label-width="80px"><el-input v-model="storeForm.lensAxisLeft"/></el-form-item></el-col>
          <el-col :span="6"><el-form-item label="轴位右" label-width="80px"><el-input v-model="storeForm.lensAxisRight"/></el-form-item></el-col>
        </el-row>
        <el-divider content-position="left">材料三：付款凭证（发票/收据号全局查重） ｜ 材料四：取镜签收</el-divider>
        <el-row :gutter="10">
          <el-col :span="12"><el-form-item label="付款凭证号"><el-input v-model="storeForm.paymentVoucherNo" placeholder="如 V-2026-088"/></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="凭证影像"><el-input v-model="storeForm.paymentVoucherFile" placeholder="影像文件名/说明（演示）"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="已取镜签收"><el-switch v-model="storeForm.pickedUp"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="签收日期"><el-date-picker v-model="storeForm.pickupDate" value-format="YYYY-MM-DD" style="width:100%"/></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="签收人"><el-input v-model="storeForm.receiverName" placeholder="须为登记家长本人"/></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="storeDlg=false">取消</el-button>
        <el-button type="primary" @click="submitStore">提交核验（不符自动挂起）</el-button>
      </template>
    </el-dialog>

    <!-- ===== 核验明细 ===== -->
    <el-dialog v-model="detailDlg" title="发放核验逐项明细" width="720px">
      <el-descriptions v-if="detailRow" :column="3" border size="small" style="margin-bottom:10px">
        <el-descriptions-item label="学生">{{ detailRow.studentName }} {{ detailRow.studentNo }}</el-descriptions-item>
        <el-descriptions-item label="门店">{{ detailRow.opticianOrg }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag size="small" :type="vTag(detailRow.status)">{{ detailRow.statusLabel }}</el-tag>
        </el-descriptions-item>
      </el-descriptions>
      <el-alert v-if="detailRow?.holdReasons" type="error" :closable="false" style="margin-bottom:10px"
                :title="'挂起原因：' + detailRow.holdReasons" />
      <pre class="detail-pre">{{ detailRow?.checkDetail }}</pre>
    </el-dialog>

    <!-- ===== 登记售后 ===== -->
    <el-dialog v-model="afterDlg" title="退镜/换片/换架售后差额登记" width="600px">
      <el-form :model="afterForm" label-width="130px">
        <el-alert type="info" :closable="false" style="margin-bottom:10px"
                  :title="'关联核验单#' + afterForm.verificationId + '，原补助 ' + (afterForm.originalSubsidy) + ' 元（同一副眼镜）'" />
        <el-form-item label="售后类型">
          <el-radio-group v-model="afterForm.type">
            <el-radio value="REFUND">退镜退款</el-radio>
            <el-radio value="LENS_REPLACE">换片</el-radio>
            <el-radio value="FRAME_REPLACE">仅换镜架</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="门店实际退款(元)"><el-input-number v-model="afterForm.refundAmount" :min="0" :precision="2"/></el-form-item>
        <el-form-item label="新发生费用(元)"><el-input-number v-model="afterForm.newCostAmount" :min="0" :precision="2"/></el-form-item>
        <el-form-item label="备注"><el-input v-model="afterForm.remark" type="textarea" :rows="2"/></el-form-item>
        <el-alert type="success" :closable="false"
                  title="提交后平台按（原费用-退款+新费用）重算应补助，自动给出应追回/应补发差额并与原补助连档。" />
      </el-form>
      <template #footer>
        <el-button @click="afterDlg=false">取消</el-button>
        <el-button type="warning" @click="submitAfter">登记并重算差额</el-button>
      </template>
    </el-dialog>

    <!-- ===== 学生反馈不适 ===== -->
    <el-dialog v-model="feedbackDlg" title="学生反馈配镜不适" width="520px">
      <el-form :model="feedbackForm" label-width="110px">
        <el-form-item label="学生" required>
          <el-select v-model="feedbackForm.studentId" filterable placeholder="选择学生" style="width:100%" @change="onFeedbackStudent">
            <el-option v-for="s in candidateStudents" :key="s.id"
                       :label="s.name + '（' + s.schoolName + '）'" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="对应眼镜">
          <el-select v-model="feedbackForm.glassesId" placeholder="选择配镜记录" style="width:100%">
            <el-option v-for="g in feedbackGlasses" :key="g.id"
                       :label="g.glassesDate + ' ' + g.opticianOrg + ' ' + g.amount + '元'" :value="g.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="不适类型">
          <el-radio-group v-model="feedbackForm.discomfortType">
            <el-radio value="DIZZY">头晕</el-radio>
            <el-radio value="BLURRY">看不清</el-radio>
            <el-radio value="FRAME_FIT">镜架不合适</el-radio>
            <el-radio value="OTHER">其他</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="具体反馈"><el-input v-model="feedbackForm.feedback" type="textarea" :rows="3"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="feedbackDlg=false">取消</el-button>
        <el-button type="danger" @click="submitFeedback">登记并通知门店</el-button>
      </template>
    </el-dialog>

    <!-- ===== 门店上传调整结果 ===== -->
    <el-dialog v-model="adjustDlg" title="门店上传配镜不适调整结果" width="520px">
      <el-form :model="adjustForm" label-width="120px">
        <el-form-item label="调整处理结果"><el-input v-model="adjustForm.adjustResult" type="textarea" :rows="3"
          placeholder="如：重新对光/调整鼻托镜腿/返修更换镜片"/></el-form-item>
        <el-form-item label="建议返院复查"><el-switch v-model="adjustForm.reviewAdvised"/></el-form-item>
        <el-form-item v-if="adjustForm.reviewAdvised" label="计划复查日期">
          <el-date-picker v-model="adjustForm.reviewPlannedDate" value-format="YYYY-MM-DD" style="width:100%"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="adjustDlg=false">取消</el-button>
        <el-button type="primary" @click="submitAdjust">上传调整结果</el-button>
      </template>
    </el-dialog>

    <!-- ===== 适应回访 ===== -->
    <el-dialog v-model="callbackDlg" title="戴镜适应回访" width="520px">
      <el-form :model="callbackForm" label-width="120px">
        <el-form-item label="回访结果"><el-input v-model="callbackForm.callbackResult" type="textarea" :rows="3"/></el-form-item>
        <el-form-item label="是否已适应"><el-switch v-model="callbackForm.resolved" active-text="已适应，无不适"/></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="callbackDlg=false">取消</el-button>
        <el-button type="success" @click="submitCallback">提交回访</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api'

const router = useRouter()
const tab = ref('verify')
const verifications = ref([])
const afterRows = ref([])
const comfortRows = ref([])
const vStatus = ref('ALL')
const aStatus = ref('ALL')
const cStatus = ref('ALL')
const candidateStudents = ref([])

const suspendedCount = computed(() => verifications.value.filter(v => v.status === 'SUSPENDED').length)
const afterOpenCount = computed(() => afterRows.value.filter(a => a.status !== 'SETTLED').length)
const comfortOpenCount = computed(() => comfortRows.value.filter(c => c.callbackStatus === 'PLANNED').length)

const CHECKS = [
  ['studentCheck', '同一学生', '生'], ['prescriptionCheck', '处方一致', '方'],
  ['lensParamCheck', '镜片参数', '片'], ['eligibilityCheck', '家庭资格', '格'],
  ['voucherCheck', '付款凭证', '证'], ['pickupCheck', '取镜签收', '签'], ['standardCheck', '补助标准/防重', '重']
]
const checkCells = (row) => CHECKS.map(([k, label, short]) => ({ k, label, short, result: row[k]?.result }))
const dotClass = (r) => r === 'PASS' ? 'pass' : r === 'MISSING' ? 'miss' : r === 'FAIL' ? 'fail' : 'pend'
const vTag = (s) => ({ PAID: 'success', PASSED: '', SUSPENDED: 'danger', REJECTED: 'info', AFTER_SALES: 'warning', CLOSED: 'success' }[s])
const rowClass = ({ row }) => row.status === 'SUSPENDED' ? 'row-suspend' : ''
const overdue = (d) => d && d < new Date().toISOString().slice(0, 10)

const loadVerifications = async () => { verifications.value = await api.verificationList(vStatus.value) }
const loadAfter = async () => { afterRows.value = await api.afterSalesList(aStatus.value) }
const loadComfort = async () => { comfortRows.value = await api.comfortList(cStatus.value) }
const loadCandidates = async () => {
  if (!candidateStudents.value.length) {
    const all = await api.students({})
    candidateStudents.value = all
  }
}
const reloadAll = async () => { await Promise.all([loadVerifications(), loadAfter(), loadComfort()]) }

const goDetail = (row) => router.push(`/students/${row.studentId}`)

/* ---------- 核验明细 ---------- */
const detailDlg = ref(false)
const detailRow = ref(null)
const showDetail = (row) => { detailRow.value = row; detailDlg.value = true }

/* ---------- 门店上传材料 ---------- */
const storeDlg = ref(false)
const today = new Date().toISOString().slice(0, 10)
const blankStore = () => ({
  glassesId: null, studentId: null, opticianOrg: '爱眼门诊配镜部', glassesDate: today, amount: 600,
  lensBrand: '1.56树脂镜片', rxNo: '', rxSphereLeft: '', rxSphereRight: '', rxCylinderLeft: '',
  rxCylinderRight: '', rxAxisLeft: '', rxAxisRight: '', rxPd: '',
  lensSphereLeft: '', lensSphereRight: '', lensCylinderLeft: '', lensCylinderRight: '',
  lensAxisLeft: '', lensAxisRight: '', lensPd: '',
  paymentVoucherNo: '', paymentVoucherFile: '', pickedUp: true, pickupDate: today, receiverName: ''
})
const storeForm = ref(blankStore())

const openStoreDialog = async (row) => {
  await loadCandidates()
  storeForm.value = blankStore()
  if (row) {
    // 挂起单补材料：拉取学生详情回填四类材料
    const d = await api.studentDetail(row.studentId)
    const g = d.glasses.find(x => x.id === row.glassesId) || d.glasses.at(-1)
    storeForm.value = {
      ...blankStore(),
      glassesId: g.id, studentId: row.studentId,
      opticianOrg: g.opticianOrg, glassesDate: g.glassesDate, amount: g.amount, lensBrand: g.lensBrand,
      rxNo: g.rxNo, rxSphereLeft: g.rxSphereLeft, rxSphereRight: g.rxSphereRight,
      rxCylinderLeft: g.rxCylinderLeft, rxCylinderRight: g.rxCylinderRight,
      rxAxisLeft: g.rxAxisLeft, rxAxisRight: g.rxAxisRight, rxPd: g.rxPd,
      lensSphereLeft: g.lensSphereLeft, lensSphereRight: g.lensSphereRight,
      lensCylinderLeft: g.lensCylinderLeft, lensCylinderRight: g.lensCylinderRight,
      lensAxisLeft: g.lensAxisLeft, lensAxisRight: g.lensAxisRight, lensPd: g.lensPd,
      paymentVoucherNo: g.paymentVoucherNo, paymentVoucherFile: g.paymentVoucherFile,
      pickedUp: g.pickedUp, pickupDate: g.pickupDate, receiverName: g.receiverName
    }
  }
  storeDlg.value = true
}
const sameAsRx = () => {
  const f = storeForm.value
  f.lensSphereLeft = f.rxSphereLeft; f.lensSphereRight = f.rxSphereRight
  f.lensCylinderLeft = f.rxCylinderLeft; f.lensCylinderRight = f.rxCylinderRight
  f.lensAxisLeft = f.rxAxisLeft; f.lensAxisRight = f.rxAxisRight; f.lensPd = f.rxPd
}
const submitStore = async () => {
  if (!storeForm.value.glassesId && !storeForm.value.studentId) return ElMessage.warning('请选择学生')
  const created = await api.storeGlasses(storeForm.value)
  storeDlg.value = false
  const v = await api.verificationDetail(created)
  await loadVerifications()
  if (v.status === 'SUSPENDED') {
    ElMessageBox.alert(v.holdReasons, '已挂起待复核（未发放补助）', { type: 'warning' })
  } else {
    ElMessage.success('七项核验通过，已进入待发放，由卫健部门确认发放')
  }
}

/* ---------- 复核/发放 ---------- */
const review = (row, action) => {
  ElMessageBox.prompt(action === 'RELEASE' ? '复核放行意见（可调整核定金额，不超过补助标准上限）' : '驳回原因',
    action === 'RELEASE' ? '挂起单复核放行' : '核验驳回', {
      inputValue: action === 'RELEASE' ? String(row.payableAmount || 300) : '',
      inputPlaceholder: action === 'RELEASE' ? '纯数字=核定金额，否则为意见' : '请填写驳回原因'
    }).then(async ({ value }) => {
      const payload = { verificationId: row.id, action, opinion: value }
      if (action === 'RELEASE' && value && !isNaN(Number(value))) payload.payableAmount = Number(value)
      await api.verificationReview(payload)
      ElMessage.success(action === 'RELEASE' ? '已放行，可发放补助' : '已驳回')
      loadVerifications()
    }).catch(() => {})
}
const pay = async (row) => {
  await ElMessageBox.confirm(`确认按核定金额 ${row.payableAmount} 元发放补助？系统将再次校验防重。`, '确认发放', { type: 'warning' })
  await api.verificationPay(row.id)
  ElMessage.success('补助已发放，与该副眼镜及申请绑定')
  loadVerifications()
}

/* ---------- 售后 ---------- */
const afterDlg = ref(false)
const afterForm = ref({})
const openAfterSales = (row) => {
  afterForm.value = {
    verificationId: row.id, glassesId: row.glassesId, studentId: row.studentId,
    originalSubsidy: row.paidAmount, type: 'REFUND', refundAmount: 0, newCostAmount: 0, remark: ''
  }
  afterDlg.value = true
}
const submitAfter = async () => {
  await api.afterSalesCreate({
    glassesId: afterForm.value.glassesId, type: afterForm.value.type,
    refundAmount: afterForm.value.refundAmount, newCostAmount: afterForm.value.newCostAmount,
    remark: afterForm.value.remark
  })
  afterDlg.value = false
  ElMessage.success('已按实际退款与新费用重算差额')
  tab.value = 'after'
  await Promise.all([loadAfter(), loadVerifications()])
}
const settle = (row) => {
  ElMessageBox.prompt(`请登记${row.directionLabel} ${row.settleAmount} 元的凭证号`, '差额追回/补发结清', {
    inputPlaceholder: '凭证号（可留空）'
  }).then(async ({ value }) => {
    await api.afterSalesSettle({ afterSalesId: row.id, settleVoucherNo: value })
    ElMessage.success('差额已结清，原补助与售后连档关闭')
    loadAfter()
  }).catch(() => {})
}

/* ---------- 不适反馈/调整/回访 ---------- */
const feedbackDlg = ref(false)
const feedbackForm = ref({})
const feedbackGlasses = ref([])
const openFeedback = async () => {
  await loadCandidates()
  feedbackForm.value = { studentId: null, glassesId: null, discomfortType: 'DIZZY', feedback: '' }
  feedbackGlasses.value = []
  feedbackDlg.value = true
}
const onFeedbackStudent = async (sid) => {
  const d = await api.studentDetail(sid)
  feedbackGlasses.value = d.glasses || []
  feedbackForm.value.glassesId = feedbackGlasses.value.at(-1)?.id || null
}
const submitFeedback = async () => {
  if (!feedbackForm.value.glassesId) return ElMessage.warning('请选择对应眼镜')
  await api.comfortFeedback({ ...feedbackForm.value, feedbackDate: today })
  feedbackDlg.value = false
  ElMessage.success('已登记，等待门店上传调整结果，并安排 7 天适应回访')
  tab.value = 'comfort'
  loadComfort()
}

const adjustDlg = ref(false)
const adjustForm = ref({})
const openAdjust = (row) => {
  adjustForm.value = {
    followupId: row.id, adjustResult: row.adjustResult || '', reviewAdvised: !!row.reviewAdvised,
    reviewPlannedDate: row.reviewPlannedDate
  }
  adjustDlg.value = true
}
const submitAdjust = async () => {
  await api.comfortAdjust({ ...adjustForm.value, adjustDate: today })
  adjustDlg.value = false
  ElMessage.success('调整结果已留存，复查与适应回访继续跟进')
  loadComfort()
}

const callbackDlg = ref(false)
const callbackForm = ref({})
const openCallback = (row) => {
  callbackForm.value = { followupId: row.id, callbackResult: row.callbackResult || '', resolved: true }
  callbackDlg.value = true
}
const submitCallback = async () => {
  await api.comfortCallback({ ...callbackForm.value, callbackDate: today })
  callbackDlg.value = false
  ElMessage.success(callbackForm.value.resolved ? '回访完成，学生已适应，事项闭环' : '回访完成，继续跟进')
  loadComfort()
}

onMounted(reloadAll)
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; flex-wrap: wrap; gap: 8px; }
.dot {
  display: inline-flex; align-items: center; justify-content: center;
  width: 21px; height: 21px; border-radius: 50%; margin-right: 3px;
  font-size: 11px; color: #fff;
}
.dot.pass { background: #67c23a; }
.dot.fail { background: #f56c6c; }
.dot.miss { background: #e6a23c; }
.dot.pend { background: #909399; }
.muted { color: #909399; font-size: 12px; }
.danger-text { color: #f56c6c; font-weight: 600; }
.detail-pre {
  background: #f6f8fa; padding: 10px; border-radius: 6px; font-size: 12px;
  white-space: pre-wrap; line-height: 1.7; margin: 0;
}
:deep(.row-suspend) { background-color: #fef0f0 !important; }
</style>
