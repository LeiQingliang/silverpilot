import { use, init } from 'echarts/core'
import { BarChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  GraphicComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components'
import { LabelLayout } from 'echarts/features'
import { CanvasRenderer } from 'echarts/renderers'

use([
  BarChart,
  PieChart,
  GridComponent,
  GraphicComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  LabelLayout,
  CanvasRenderer
])

export const initECharts = init

export const getChartTheme = (documentTarget = globalThis.document) => {
  const fallback = {
    text: '#142033', muted: '#718096', surface: '#ffffff', border: '#dce4ee',
    grid: '#e7edf3', brand: '#087f78', tooltip: '#101927'
  }
  if (!documentTarget?.documentElement || !globalThis.getComputedStyle) return fallback
  const styles = globalThis.getComputedStyle(documentTarget.documentElement)
  const value = (name, defaultValue) => styles.getPropertyValue(name).trim() || defaultValue
  return {
    text: value('--sp-text', fallback.text),
    muted: value('--sp-text-muted', fallback.muted),
    surface: value('--sp-surface', fallback.surface),
    border: value('--sp-border', fallback.border),
    grid: value('--sp-bg-subtle', fallback.grid),
    brand: value('--sp-brand', fallback.brand),
    tooltip: value('--sp-surface-raised', fallback.tooltip)
  }
}
