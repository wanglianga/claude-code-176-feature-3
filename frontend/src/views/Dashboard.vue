<template>
  <div class="page-container">
    <el-alert type="success" :closable="false" style="margin-bottom:14px"
      title="县域近视防控监管视图：筛查数据只是起点，复诊率、复查执行、学校干预与补助发放共同决定管理效果" />

    <div class="filters">
      <el-select v-if="!auth.isSchool" v-model="regionId" placeholder="全部片区" clearable
                 style="width:150px" @change="load">
        <el-option v-for="r in regions" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>
      <el-select v-if="!auth.isSchool" v-model="schoolId" placeholder="全部学校" clearable
                 style="width:210px" @change="load">
        <el-option v-for="s in schools" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
      <el-button @click="load" :icon="Refresh">刷新</el-button>
    </div>

    <el-row :gutter="12">
      <el-col :span="3" v-for="k in kpis" :key="k.label">
        <el-card class="kpi-card" shadow="hover">
          <div class="num" :style="{ color: k.color || '#1f6f8b' }">{{ k.value }}</div>
          <div class="label">{{ k.label }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top:14px">
      <el-col :span="14">
        <el-card>
          <template #header><span class="card-header-title">各年级近视发展趋势（异常率 / 真实复诊率 %）</span></template>
          <div ref="gradeChart" style="height:320px"></div>
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card>
          <template #header><span class="card-header-title">健康管理阶段漏斗（人）</span></template>
          <div ref="funnelChart" style="height:320px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="12" style="margin-top:14px">
      <el-col :span="12">
        <el-card>
          <template #header><span class="card-header-title">各片区异常率 / 真实复诊率（%）</span></template>
          <div ref="regionChart" style="height:300px"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header><span class="card-header-title">异常事项闭环情况（含门店核验挂起/售后差额）</span></template>
          <div ref="issueChart" style="height:300px"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-card style="margin-top:14px">
      <template #header><span class="card-header-title">学校落实对比（真实复诊率与学校干预执行数）</span></template>
      <el-table :data="schoolRows" stripe size="small">
        <el-table-column prop="schoolName" label="学校" min-width="180" />
        <el-table-column prop="regionName" label="片区" width="110" />
        <el-table-column prop="total" label="学生数" width="80" />
        <el-table-column prop="abnormal" label="异常人数" width="90" />
        <el-table-column label="异常率" width="90">
          <template #default="{ row }">{{ row.abnormalRate }}%</template>
        </el-table-column>
        <el-table-column prop="visited" label="已复诊" width="80" />
        <el-table-column label="真实复诊率" width="110">
          <template #default="{ row }">
            <el-tag :type="row.realVisitRate >= 80 ? 'success' : row.realVisitRate >= 50 ? 'warning' : 'danger'">
              {{ row.realVisitRate }}%
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="interventions" label="干预执行数" width="100" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import * as echarts from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { api } from '../api'
import { useAuthStore } from '../store/auth'

const auth = useAuthStore()
const regions = ref([])
const schools = ref([])
const regionId = ref()
const schoolId = ref()
const ov = ref({})
const gradeRows = ref([])
const schoolRows = ref([])
const funnelRows = ref([])

const gradeChart = ref()
const funnelChart = ref()
const regionChart = ref()
const issueChart = ref()
let charts = []

const kpis = computed(() => [
  { label: '在册学生', value: ov.value.totalStudents ?? '-' },
  { label: '筛查率', value: (ov.value.screenRate ?? 0) + '%' },
  { label: '异常人数', value: ov.value.abnormalCount ?? '-' },
  { label: '真实复诊率', value: (ov.value.realVisitRate ?? 0) + '%', color: rateColor(ov.value.realVisitRate) },
  { label: '异常闭环率', value: (ov.value.closedLoopRate ?? 0) + '%', color: rateColor(ov.value.closedLoopRate) },
  { label: '待处理异常事项', value: ov.value.openIssues ?? '-', color: '#e6a23c' },
  { label: '核验挂起', value: ov.value.dispenseSuspended ?? '-', color: ov.value.dispenseSuspended ? '#f56c6c' : '#67c23a' },
  { label: '售后待办', value: ov.value.afterSalesOpen ?? '-', color: ov.value.afterSalesOpen ? '#e6a23c' : '#67c23a' }
])

const rateColor = (v) => v >= 80 ? '#67c23a' : v >= 50 ? '#e6a23c' : '#f56c6c'

const load = async () => {
  const params = { schoolId: auth.isSchool ? auth.schoolId : schoolId.value, regionId: regionId.value }
  const [o, g, f, sr, ir, bySchool] = await Promise.all([
    api.overview(params), api.trendGrade(params), api.stageFunnel(params),
    api.byRegion(), api.issueDist(),
    api.bySchool(regionId.value)
  ])
  ov.value = o
  gradeRows.value = g
  funnelRows.value = f
  schoolRows.value = bySchool
  await nextTick()
  renderGrade(g)
  renderFunnel(f)
  renderRegion(sr)
  renderIssues(ir)
}

const renderGrade = (rows) => {
  const c = echarts.init(gradeChart.value)
  charts.push(c)
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['异常率', '真实复诊率'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(r => r.grade) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: '异常率', type: 'bar', data: rows.map(r => r.abnormalRate),
        itemStyle: { color: '#f56c6c' }, barMaxWidth: 34 },
      { name: '真实复诊率', type: 'line', smooth: true, data: rows.map(r => r.visitRate),
        itemStyle: { color: '#1f6f8b' }, lineStyle: { width: 3 } }
    ]
  })
}

const renderFunnel = (rows) => {
  const data = rows.filter(r => r.count > 0).map(r => ({ name: r.label, value: r.count }))
  const c = echarts.init(funnelChart.value)
  charts.push(c)
  c.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 人' },
    series: [{
      type: 'funnel', left: '5%', width: '90%',
      label: { formatter: '{b} {c}' },
      data,
      color: ['#1f6f8b', '#2c8da8', '#4fb0c6', '#8ccbdb', '#e6a23c',
        '#67c23a', '#909399', '#f56c6c', '#b37feb', '#5b8ff9']
    }]
  })
}

const renderRegion = (rows) => {
  const c = echarts.init(regionChart.value)
  charts.push(c)
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['异常率', '真实复诊率'] },
    grid: { left: 40, right: 20, top: 40, bottom: 30 },
    xAxis: { type: 'category', data: rows.map(r => r.region) },
    yAxis: { type: 'value', max: 100, axisLabel: { formatter: '{value}%' } },
    series: [
      { name: '异常率', type: 'bar', data: rows.map(r => r.abnormalRate), itemStyle: { color: '#f56c6c' } },
      { name: '真实复诊率', type: 'bar', data: rows.map(r => r.realVisitRate), itemStyle: { color: '#1f6f8b' } }
    ]
  })
}

const renderIssues = (rows) => {
  const c = echarts.init(issueChart.value)
  charts.push(c)
  c.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['待处理', '已闭环'] },
    grid: { left: 10, right: 20, top: 40, bottom: 80 },
    xAxis: {
      type: 'category', data: rows.map(r => r.label),
      axisLabel: { interval: 0, rotate: 28, fontSize: 11 }
    },
    yAxis: { type: 'value' },
    series: [
      { name: '待处理', type: 'bar', stack: 'x', data: rows.map(r => r.open), itemStyle: { color: '#e6a23c' } },
      { name: '已闭环', type: 'bar', stack: 'x', data: rows.map(r => r.resolved), itemStyle: { color: '#67c23a' } }
    ]
  })
}

onMounted(async () => {
  regions.value = await api.regions()
  schools.value = await api.schools()
  await load()
  window.addEventListener('resize', () => charts.forEach(c => c.resize()))
})
</script>

<style scoped>
.filters { display: flex; gap: 10px; margin-bottom: 14px; }
</style>
