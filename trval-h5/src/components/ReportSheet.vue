<script setup>
/**
 * ReportSheet.vue — 举报弹层（NoteDetailView / VideoDetailView 共用）
 * POST /api/report { targetType, targetId, reason }
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { reportApi } from '../api'

defineOptions({ name: 'ReportSheet' })

const props = defineProps({
  show: { type: Boolean, default: false },
  /** 举报目标类型：note / post / comment（与后端 VALID_TYPES 一致） */
  targetType: { type: String, default: 'note' },
  targetId: { type: [Number, String], default: null },
})

const emit = defineEmits(['update:show', 'success'])

const { t } = useI18n()

const reasons = [
  { key: 'spam', labelKey: 'report.reasonSpam' },
  { key: 'porn', labelKey: 'report.reasonPorn' },
  { key: 'abuse', labelKey: 'report.reasonAbuse' },
  { key: 'illegal', labelKey: 'report.reasonIllegal' },
  { key: 'other', labelKey: 'report.reasonOther' },
]

const selectedReason = ref('')
const description = ref('')
const submitting = ref(false)

const close = () => { emit('update:show', false) }

// 每次打开重置表单
watch(() => props.show, (val) => {
  if (val) { selectedReason.value = ''; description.value = ''; submitting.value = false }
})

const handleSubmit = async () => {
  if (submitting.value) return
  if (!selectedReason.value) { showToast(t('report.needReason')); return }
  if (!props.targetId) { showToast(t('report.failed')); return }
  submitting.value = true
  try {
    const reasonLabel = t(`report.reason${selectedReason.value.charAt(0).toUpperCase() + selectedReason.value.slice(1)}`)
    const desc = description.value.trim()
    const reason = desc ? `${reasonLabel}：${desc}` : reasonLabel
    const res = await reportApi.report({
      targetType: props.targetType,
      targetId: Number(props.targetId),
      reason: reason.slice(0, 200),
    })
    if (res.code === 0) {
      showToast(t('report.success'))
      emit('success')
      close()
    } else {
      showToast(res.message || t('report.failed'))
    }
  } catch (e) {
    showToast(t('report.failed'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <van-popup :show="show" position="bottom" round safe-area-inset-bottom @update:show="emit('update:show', $event)" @click-overlay="close">
    <div class="report-sheet">
      <div class="report-header">
        <span class="report-title">{{ t('report.title') }}</span>
        <van-icon name="cross" size="18" color="#94a3b8" @click="close" />
      </div>

      <div class="report-body">
        <div class="reason-title">{{ t('report.reasonTitle') }}</div>
        <div class="reason-list">
          <div
            v-for="r in reasons"
            :key="r.key"
            class="reason-item"
            :class="{ active: selectedReason === r.key }"
            @click="selectedReason = r.key"
          >
            <span class="reason-label">{{ t(r.labelKey) }}</span>
            <van-icon :name="selectedReason === r.key ? 'success' : 'circle'" :size="18" :color="selectedReason === r.key ? '#8B5CF6' : '#cbd5e1'" />
          </div>
        </div>

        <van-field
          v-model="description"
          type="textarea"
          rows="3"
          maxlength="200"
          :label="t('report.descLabel')"
          :placeholder="t('report.descPlaceholder')"
          class="report-desc"
        />
      </div>

      <div class="report-footer">
        <van-button block round class="report-submit" :loading="submitting" loading-text="" @click="handleSubmit">
          {{ submitting ? t('report.submitting') : t('report.submit') }}
        </van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.report-sheet {
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
}
.report-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 10px;
}
.report-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.report-body { padding: 0 20px 12px; }
.reason-title { font-size: 13px; color: #64748b; margin-bottom: 10px; }
.reason-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 12px;
}
.reason-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 14px;
  border-radius: 20px;
  background: #f8f7ff;
  border: 1px solid transparent;
  font-size: 13px;
  color: #475569;
  cursor: pointer;
  transition: all 0.15s;
}
.reason-item.active {
  background: rgba(139, 92, 246, 0.08);
  border-color: #C4B5FD;
  color: #7C3AED;
  font-weight: 500;
}
.reason-item:active { transform: scale(0.96); }
.report-desc { background: #f8fafc; border-radius: 12px; margin-bottom: 4px; }
.report-footer { padding: 12px 20px 8px; }
.report-submit {
  background: linear-gradient(135deg, #8B5CF6, #6366F1) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.25);
}
</style>
