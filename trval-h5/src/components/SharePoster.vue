<script setup>
/**
 * SharePoster — 行程分享海报（Canvas 前端生成，后端零渲染依赖）
 * 渐变背景 + 目的地/天数 + 每日景点列表，导出 PNG 供保存/系统分享
 */
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = defineProps({
  plan: { type: Object, default: null },
})
const emit = defineEmits(['done'])

const canvasRef = ref(null)
const posterUrl = ref('')

const W = 750
const H = 1150
const FONT = '"PingFang SC","Microsoft YaHei",sans-serif'

const stripTags = (name) => (name || '').replace(/【[^】]*】/g, '').trim()

function drawPoster() {
  const plan = props.plan
  if (!plan) return
  const canvas = canvasRef.value
  if (!canvas) return
  canvas.width = W
  canvas.height = H
  const ctx = canvas.getContext('2d')

  // 背景渐变
  const grad = ctx.createLinearGradient(0, 0, 0, H)
  grad.addColorStop(0, '#8B5CF6')
  grad.addColorStop(1, '#6366F1')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, W, H)

  // 装饰光晕
  ctx.globalAlpha = 0.14
  ctx.fillStyle = '#fff'
  ctx.beginPath(); ctx.arc(630, 150, 150, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(120, 1000, 130, 0, Math.PI * 2); ctx.fill()
  ctx.beginPath(); ctx.arc(700, 900, 60, 0, Math.PI * 2); ctx.fill()
  ctx.globalAlpha = 1

  // 顶部标题
  ctx.fillStyle = 'rgba(255,255,255,0.85)'
  ctx.font = `bold 26px ${FONT}`
  ctx.fillText(t('components.posterBrand'), 60, 110)

  // 目的地大标题
  ctx.fillStyle = '#fff'
  ctx.font = `bold 66px ${FONT}`
  ctx.fillText(plan.destination || t('components.travel'), 60, 240)
  ctx.font = `26px ${FONT}`
  ctx.fillStyle = 'rgba(255,255,255,0.9)'
  ctx.fillText(t('components.posterDaysPeople', { days: plan.days || 3, people: plan.people || 2 }), 62, 292)

  // 每日行程
  const dayPlans = plan.dayPlans || []
  let y = 392
  const lineHeight = 36
  ctx.textBaseline = 'middle'
  for (let i = 0; i < dayPlans.length; i++) {
    if (y > H - 110) break
    const dp = dayPlans[i]
    ctx.fillStyle = '#fff'
    ctx.font = `bold 26px ${FONT}`
    ctx.fillText(`Day ${i + 1}`, 60, y)
    y += lineHeight + 4
    ctx.font = `22px ${FONT}`
    ctx.fillStyle = 'rgba(255,255,255,0.95)'
    const spots = (dp.timeSlots || []).map(s => stripTags(s.attraction)).filter(Boolean)
    spots.forEach((name) => {
      if (y > H - 110) return
      ctx.fillText(`· ${name}`, 92, y)
      y += lineHeight
    })
    y += 22
  }

  // 底部
  ctx.fillStyle = 'rgba(255,255,255,0.9)'
  ctx.font = `24px ${FONT}`
  ctx.fillText(t('components.posterFooter'), 60, H - 48)
  ctx.textBaseline = 'alphabetic'

  posterUrl.value = canvas.toDataURL('image/png')
  emit('done', posterUrl.value)
}

function download() {
  if (!posterUrl.value) return
  const a = document.createElement('a')
  a.href = posterUrl.value
  a.download = `${t('components.posterFileName')}-${props.plan?.destination || 'travel'}.png`
  a.click()
}

async function share() {
  if (!posterUrl.value) return
  try {
    const blob = await (await fetch(posterUrl.value)).blob()
    const file = new File([blob], 'poster.png', { type: 'image/png' })
    if (navigator.canShare && navigator.canShare({ files: [file] })) {
      await navigator.share({ files: [file], title: t('components.myTripPlan') })
    } else {
      download()
    }
  } catch (e) { download() }
}

onMounted(() => { drawPoster() })

defineExpose({ download, share, drawPoster })
</script>

<template>
  <div class="poster-wrap">
    <canvas ref="canvasRef" class="poster-canvas" />
    <div class="poster-actions">
      <van-button size="small" round plain type="primary" @click="download">{{ t('components.savePoster') }}</van-button>
      <van-button size="small" round type="primary" @click="share">{{ t('components.sharePoster') }}</van-button>
    </div>
  </div>
</template>

<style scoped>
.poster-wrap { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 12px 0; }
.poster-canvas { width: 300px; height: auto; border-radius: 16px; box-shadow: 0 8px 30px rgba(139,92,246,0.25); background: #fff; }
.poster-actions { display: flex; gap: 12px; }
</style>
