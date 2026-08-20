<template>
  <el-card class="report-card">
    <el-tabs type="border-card" class="demo-tabs">
      <el-tab-pane label="健康体检表">
        <el-card class="box-card" shadow="never">
          <el-table
            class="desktop-health-reports"
            :data="reportData"
            empty-text="暂无体检报告"
            style="width: 100%"
            height="310"
          >
            <el-table-column label="序号" prop="id" width="64" />
            <el-table-column label="体检日期" prop="time" width="160" />
            <el-table-column label="体检医生" prop="dId" width="120">
              <template #default="scope">
                {{ scope.row.doctor?.name || '待记录' }}
              </template>
            </el-table-column>
            <el-table-column label="姓名" prop="uId">
              <template #default="scope">
                {{ scope.row.user?.name || '未知居民' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" fixed="right" width="90">
              <template #default="scope">
                <el-button size="small" link type="primary" @click="toDetail(scope.row)">
                  查看
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="mobile-health-reports" aria-label="健康体检报告列表">
            <article v-for="report in reportData" :key="report.id">
              <small>REPORT {{ report.id }}</small>
              <h3>{{ report.user?.name || '居民健康报告' }}</h3>
              <dl>
                <div><dt>体检日期</dt><dd>{{ report.time || '待记录' }}</dd></div>
                <div><dt>体检医生</dt><dd>{{ report.doctor?.name || '待记录' }}</dd></div>
              </dl>
              <el-button type="primary" plain @click="toDetail(report)">查看报告详情</el-button>
            </article>
            <el-empty v-if="!reportData.length" description="请选择居民查看健康报告" :image-size="64" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { storeToRefs } from 'pinia'
import { useReportStore } from '../../../stores/report.js'

const reportStore = useReportStore()
const { reportData } = storeToRefs(reportStore)
const toDetail = (row) => reportStore.pre4Detail(row)
</script>

<style scoped>
.report-card { width: 100%; min-width: 0; }
.mobile-health-reports { display: none; }
@media (max-width: 760px) {
  .desktop-health-reports { display: none; }
  .mobile-health-reports { display: grid; gap: 10px; }
  .mobile-health-reports article { padding: 15px; color: var(--sp-text); background: var(--sp-surface-muted); border: 1px solid var(--sp-border); border-radius: var(--sp-radius-md); }
  .mobile-health-reports small { color: var(--sp-brand-strong); font: 750 9px/1 var(--sp-font-mono); letter-spacing: .08em; }
  .mobile-health-reports h3 { margin: 6px 0 12px; font-size: 17px; }
  .mobile-health-reports dl { display: grid; gap: 6px; margin: 0 0 12px; }
  .mobile-health-reports dl div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 8px; font-size: 12px; }
  .mobile-health-reports dt { color: var(--sp-text-muted); }
  .mobile-health-reports dd { margin: 0; color: var(--sp-text-secondary); }
  .mobile-health-reports .el-button { width: 100%; }
}
</style>
