<template>
  <el-card class="box-card" shadow="never" :body-style="{ padding: '0' }">
    <header class="chart-header">
      <h3>活动报名分布</h3>
      <span>按活动类型汇总</span>
    </header>
    <div id="chartSignedUp" class="chart-container"></div>
  </el-card>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import $axios from '../../../utils/axios'
import { getChartTheme, initECharts } from '../../../utils/echarts'

const tableData = ref([])
let chartInstance = null

const initChart = (chartData) => {
  const chartDom = document.getElementById('chartSignedUp')
  if (!chartDom) return
  chartInstance?.dispose()
  chartInstance = initECharts(chartDom)
  const theme = getChartTheme()
  const rows = (Array.isArray(chartData) ? chartData : [])
    .map((item) => ({ name: String(item.ActivityType || '未分类'), value: Math.max(0, Number(item.NumberOfSignUps) || 0) }))
    .sort((left, right) => left.value - right.value)
  const hasData = rows.some((item) => item.value > 0)

  const options = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: ([item]) => `${item?.name || '未分类'}：${item?.value || 0} 人次`,
      backgroundColor: theme.tooltip,
      borderColor: theme.border,
      textStyle: { color: theme.text }
    },
    grid: { top: 22, right: 42, bottom: 22, left: 18, containLabel: true },
    xAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: { color: theme.muted, fontSize: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { color: theme.grid, type: 'dashed' } }
    },
    yAxis: {
      type: 'category',
      data: rows.map((item) => item.name),
      axisLabel: {
        color: theme.muted,
        fontSize: 10,
        width: 78,
        overflow: 'truncate'
      },
      axisLine: { show: false },
      axisTick: { show: false }
    },
    series: [
      {
        name: '报名人次',
        type: 'bar',
        data: rows.map((item) => item.value),
        barMaxWidth: 18,
        showBackground: true,
        backgroundStyle: { color: theme.grid, borderRadius: 7 },
        itemStyle: {
          color: theme.brand,
          borderRadius: [0, 7, 7, 0]
        },
        label: {
          show: hasData,
          position: 'right',
          color: theme.text,
          fontSize: 10,
          fontWeight: 700
        }
      }
    ],
    graphic: hasData ? [] : [{
      type: 'text', left: 'center', top: 'middle',
      style: { text: '暂无报名数据', fill: theme.muted, fontSize: 13 }
    }]
  }

  chartInstance.setOption(options)
}

const init = async () => {
  try {
    const path = '/count/countSignedUpNum'
    const { data: res } = await $axios.get(path)
    if (res.code === 200) {
      tableData.value = res.result || []
      initChart(tableData.value)
    }
  } catch (error) {
    console.error('获取活动报名统计失败:', error)
    initChart([])
  }
}
const handleResize = () => chartInstance?.resize()

onMounted(() => {
  init()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
  chartInstance = null
})
</script>

<style scoped>
.box-card {
  width: 100%;
  height: 100%;
  border-radius: var(--sp-radius-lg);
  overflow: hidden;
  background: var(--sp-surface);
}
.chart-header { min-height: 66px; display: flex; align-items: baseline; justify-content: space-between; gap: 10px; padding: 18px 22px 12px; border-bottom: 1px solid var(--sp-border); }
.chart-header h3 { color: var(--sp-text); font-size: 17px; font-weight: 740; }
.chart-header span { color: var(--sp-text-muted); font-size: 11px; }
.chart-container {
  width: 100%;
  height: 420px;
  min-height: 360px;
}
@media (max-width: 640px) { .chart-header { align-items: flex-start; flex-direction: column; }.chart-container { height: 380px; } }
</style>
