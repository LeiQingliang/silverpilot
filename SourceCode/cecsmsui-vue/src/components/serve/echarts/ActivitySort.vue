<template>
  <el-card class="box-card" shadow="never" :body-style="{ padding: '0' }">
    <header class="chart-header">
      <h3>热门活动排名</h3>
      <span>按报名人数排序</span>
    </header>
    <div id="chartSort" class="echarts-container"></div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import $axios from '../../../utils/axios'
import { getChartTheme, initECharts } from '../../../utils/echarts'

const tableData = ref([])
let chartInstance = null

const init = async () => {
  const path = '/count/countActivitySort'
  const { data: res } = await $axios.get(path)
  if (res.code === 200) {
    tableData.value = res.result
    drawChart()
  }
}

onMounted(() => {
  init()
  window.addEventListener('resize', handleResize)
})

const drawChart = () => {
  const chartContainer = document.getElementById('chartSort')
  if (!chartContainer) return
  chartInstance?.dispose()
  chartInstance = initECharts(chartContainer)
  const theme = getChartTheme()

  const options = {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    grid: {
      top: 28,
      bottom: 22,
      left: '8%',
      right: '5%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: tableData.value.map(item => item.activityName),
      axisLabel: {
        color: theme.muted,
        rotate: -30,
        interval: 0,
        fontSize: 11,
        margin: 12
      },
      axisLine: {
        lineStyle: { color: theme.border }
      }
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      axisLabel: {
        color: theme.muted,
        fontSize: 12
      },
      splitLine: {
        lineStyle: { type: 'dashed', color: theme.grid }
      }
    },
    series: [
      {
        name: '报名人数',
        type: 'bar',
        data: tableData.value.map(item => item.signNum),
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: theme.brand
        },
        label: {
          show: true,
          position: 'top',
          color: theme.brand,
          fontWeight: 'bold'
        }
      }
    ]
  }

  chartInstance.setOption(options)
}

const handleResize = () => chartInstance?.resize()

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
  border-radius: 16px;
  overflow: hidden;
  background: var(--sp-surface);
  transition: border-color var(--sp-duration-fast) ease, box-shadow var(--sp-duration-fast) ease;
}
.chart-header { min-height: 66px; display: flex; align-items: baseline; justify-content: space-between; gap: 10px; padding: 18px 22px 12px; border-bottom: 1px solid var(--sp-border); }
.chart-header h3 { color: var(--sp-text); font-size: 17px; font-weight: 740; }
.chart-header span { color: var(--sp-text-muted); font-size: 11px; }
.echarts-container {
  width: 100%;
  height: 420px;
  min-height: 360px;
}
@media (max-width: 640px) { .chart-header { align-items: flex-start; flex-direction: column; }.echarts-container { height: 380px; } }
</style>
