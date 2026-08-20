<template>
  <el-card class="box-card" shadow="never" :body-style="{ padding: '0' }">
    <div class="chart-header">
      <h3 class="chart-title">服务订单评分统计</h3>
      <span class="chart-subtitle">基于用户真实反馈</span>
    </div>
    <div id="chartRate" class="chart-container"></div>
  </el-card>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import $axios from '../../../utils/axios'
import { getChartTheme, initECharts } from '../../../utils/echarts'

const tableData = ref([])
let chartInstance = null

// 高级配色方案（柔和质感）
const colorMap = {
  5: '#52c41a',   // 清新绿
  4: '#13c2c2',   // 薄荷青
  3: '#faad14',   // 琥珀黄
  2: '#ff7c43',   // 暖橙
  1: '#f56c6c'    // 淡红
}

const initChart = (chartData) => {
  const chartDom = document.getElementById('chartRate')
  if (!chartDom) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = initECharts(chartDom)
  const theme = getChartTheme()
  const rows = (Array.isArray(chartData) ? chartData : [])
    .map((item) => ({ rate: Number(item.rate), count: Math.max(0, Number(item.count) || 0) }))
    .filter((item) => item.rate >= 1 && item.rate <= 5 && item.count > 0)
  const total = rows.reduce((sum, item) => sum + item.count, 0)

  const options = {
    title: {
      show: false  // 由自定义头部取代
    },
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 单 ({d}%)',
      backgroundColor: theme.tooltip,
      borderColor: theme.border,
      textStyle: { color: theme.text, fontSize: 13 }
    },
    legend: {
      type: 'scroll',
      orient: 'vertical',
      right: 24,
      top: 24,
      bottom: 20,
      itemWidth: 20,
      itemHeight: 12,
      textStyle: { color: theme.muted, fontSize: 12, fontWeight: 400 },
      formatter: (name) => {
        const item = rows.find(d => `${d.rate} 分` === name)
        return item ? `${name} (${item.count}单)` : name
      }
    },
    series: [
      {
        name: '评分分布',
        type: 'pie',
        radius: ['42%', '68%'],
        center: ['40%', '52%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: theme.surface,
          borderWidth: 2
        },
        label: {
          show: true,
          position: 'outside',
          formatter: '{b}',
          fontSize: 12,
          color: theme.text,
          fontWeight: 500,
          lineHeight: 20,
          fontFamily: 'PingFang SC, Microsoft YaHei, sans-serif'
        },
        labelLine: {
          length: 12,
          length2: 8,
          smooth: true
        },
        emphasis: {
          scale: true,
          label: { show: true, fontWeight: 'bold' }
        },
        data: total > 0 ? rows.map(item => ({
          name: `${item.rate} 分`,
          value: item.count,
          itemStyle: { color: colorMap[item.rate] || '#909399' }
        })) : [{ name: '暂无评分', value: 1, itemStyle: { color: theme.grid }, label: { show: false }, tooltip: { show: false } }]
      }
    ],
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: 'center',
        style: {
          text: total > 0 ? `${total}\n总评价` : '暂无评分',
          fill: theme.brand,
          fontSize: total > 0 ? 22 : 13,
          fontWeight: 600,
          fontFamily: 'DIN Alternate, sans-serif',
          textAlign: 'center',
          lineHeight: 24
        },
        z: 100,
        invisible: false
      }
    ]
  }

  chartInstance.setOption(options)
}

const init = async () => {
  try {
    const path = '/count/countRate'
    const { data: res } = await $axios.get(path)
    if (res.code === 200) {
      tableData.value = res.result || []
      initChart(tableData.value)
    }
  } catch (error) {
    console.error('获取评分统计数据失败:', error)
    initChart([])
  }
}

const handleResize = () => {
  if (chartInstance) {
    chartInstance.resize()
  }
}

onMounted(() => {
  init()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  if (chartInstance) {
    chartInstance.dispose()
    chartInstance = null
  }
})
</script>

<style scoped>
.box-card {
  width: 100%;
  height: 100%;
  border-radius: 24px;
  background: var(--sp-surface);
  border: 1px solid var(--sp-border);
  transition: border-color var(--sp-duration-fast) ease, box-shadow var(--sp-duration-fast) ease;
  overflow: hidden;
}

.box-card :deep(.el-card__body) {
  padding: 0;
}

.chart-header {
  padding: 20px 24px 0 24px;
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  border-bottom: 1px solid var(--sp-border);
}

.chart-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--sp-text);
  margin: 0;
  letter-spacing: -0.3px;
}

.chart-subtitle {
  font-size: 13px;
  color: var(--sp-text-muted);
  font-weight: 400;
}

.chart-container {
  width: 100%;
  height: 460px;
  min-height: 400px;
}

/* 响应式适配 */
@media (max-width: 768px) {
  .chart-header {
    padding: 16px 20px 0 20px;
  }
  .chart-title {
    font-size: 16px;
  }
  .chart-subtitle {
    font-size: 12px;
  }
  .chart-container {
    height: 380px;
  }
}
</style>
