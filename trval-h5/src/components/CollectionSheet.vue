<script setup>
/**
 * CollectionSheet.vue — 「收藏到合集」弹层（NoteDetailView 使用）
 * - 打开时加载我的收藏夹（GET /api/collection/mine）
 * - 点击已有合集 → POST /api/collection/{id}/notes {noteId}（去重）
 * - 底部「新建收藏夹」→ 表单创建后自动收藏该笔记
 */
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { showToast } from 'vant'
import { collectionApi } from '../api'

defineOptions({ name: 'CollectionSheet' })

const props = defineProps({
  show: { type: Boolean, default: false },
  noteId: { type: [Number, String], default: null },
})

const emit = defineEmits(['update:show', 'saved'])

const { t } = useI18n()

const collections = ref([])
const loading = ref(false)
const loadFailed = ref(false)
const showCreateForm = ref(false)
const creating = ref(false)
const savingId = ref(null)
const createForm = ref({ name: '', description: '', isPublic: false })

const close = () => { emit('update:show', false) }

const loadMine = async () => {
  loading.value = true
  loadFailed.value = false
  try {
    const res = await collectionApi.getMine()
    if (res.code === 0) collections.value = res.data || []
    else { collections.value = []; loadFailed.value = true }
  } catch (e) {
    collections.value = []
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.show, (val) => {
  showCreateForm.value = false
  createForm.value = { name: '', description: '', isPublic: false }
  if (val) loadMine()
})

const pickCollection = async (c) => {
  if (savingId.value) return
  if (!props.noteId) return
  savingId.value = c.id
  try {
    const res = await collectionApi.addNote(c.id, Number(props.noteId))
    if (res.code === 0) {
      if (res.data?.added) {
        showToast(t('collection.saved'))
        emit('saved')
        close()
      } else {
        showToast(t('collection.alreadySaved'))
      }
    } else {
      showToast(res.message || t('collection.saveFailed'))
    }
  } catch (e) {
    showToast(t('collection.saveFailed'))
  } finally {
    savingId.value = null
  }
}

const handleCreate = async () => {
  if (creating.value) return
  const name = createForm.value.name.trim()
  if (!name) { showToast(t('collection.nameRequired')); return }
  creating.value = true
  try {
    const res = await collectionApi.create({
      name,
      description: createForm.value.description.trim() || null,
      isPublic: createForm.value.isPublic,
    })
    if (res.code === 0) {
      const created = res.data
      // 有笔记目标：创建后直接收藏该笔记
      if (props.noteId && created?.id) {
        try {
          await collectionApi.addNote(created.id, Number(props.noteId))
        } catch (e) { /* 收藏失败不阻断创建成功提示 */ }
        showToast(t('collection.saved'))
      } else {
        showToast(t('collection.createSuccess'))
      }
      emit('saved')
      close()
    } else {
      showToast(res.message || t('collection.createFailed'))
    }
  } catch (e) {
    showToast(t('collection.createFailed'))
  } finally {
    creating.value = false
  }
}
</script>

<template>
  <van-popup :show="show" position="bottom" :style="{ height: '70%' }" round safe-area-inset-bottom @update:show="emit('update:show', $event)" @click-overlay="close">
    <div class="collection-sheet">
      <div class="sheet-header">
        <span class="sheet-title">{{ showCreateForm ? t('collection.createNew') : t('collection.saveToCollection') }}</span>
        <van-icon name="cross" size="18" color="#94a3b8" @click="close" />
      </div>

      <div class="sheet-body">
        <van-loading v-if="loading" size="24" color="#8B5CF6" class="sheet-loading" />
        <div v-else-if="loadFailed" class="sheet-fail">{{ t('collection.loadFailed') }}</div>
        <template v-else>
          <div v-if="collections.length === 0" class="sheet-empty">
            <van-icon name="label-o" size="36" color="#C4B5FD" />
            <p>{{ t('collection.empty') }}</p>
            <p class="sheet-empty-desc">{{ t('collection.emptyDesc') }}</p>
          </div>
          <div v-else class="collection-list">
            <div
              v-for="c in collections"
              :key="c.id"
              class="collection-item"
              @click="pickCollection(c)"
            >
              <div class="collection-icon"><van-icon :name="c.isPublic ? 'eye-o' : 'lock'" size="18" :color="c.isPublic ? '#34D399' : '#94a3b8'" /></div>
              <div class="collection-info">
                <div class="collection-name">{{ c.name }}</div>
                <div class="collection-meta">
                  <span>{{ t('collection.noteCount', { n: c.noteCount || 0 }) }}</span>
                  <span class="collection-visibility">{{ c.isPublic ? t('collection.publicLabel') : t('collection.privateLabel') }}</span>
                </div>
              </div>
              <van-loading v-if="savingId === c.id" size="16" color="#8B5CF6" />
              <van-icon v-else name="plus" size="16" color="#8B5CF6" />
            </div>
          </div>

          <!-- 新建收藏夹表单 -->
          <div v-if="showCreateForm" class="create-form">
            <van-field v-model="createForm.name" :label="t('collection.collectionName')" :placeholder="t('collection.namePlaceholder')" maxlength="100" />
            <van-field v-model="createForm.description" :label="t('collection.description')" :placeholder="t('collection.descPlaceholder')" maxlength="500" />
            <div class="public-row">
              <span>{{ t('collection.isPublic') }}</span>
              <van-switch v-model="createForm.isPublic" size="20" />
            </div>
            <p class="public-hint">{{ t('collection.publicHint') }}</p>
          </div>
        </template>
      </div>

      <div class="sheet-footer">
        <van-button
          v-if="!showCreateForm"
          block round plain class="create-btn"
          @click="showCreateForm = true"
        ><van-icon name="plus" size="16" /> {{ t('collection.createNew') }}</van-button>
        <van-button
          v-else
          block round class="create-btn create-btn--solid"
          :loading="creating"
          @click="handleCreate"
        >{{ creating ? t('collection.creating') : t('collection.create') }}</van-button>
      </div>
    </div>
  </van-popup>
</template>

<style scoped>
.collection-sheet {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #fff;
  border-radius: 20px 20px 0 0;
}
.sheet-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 20px 12px;
  border-bottom: 1px solid #f1f5f9;
  flex-shrink: 0;
}
.sheet-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.sheet-body { flex: 1; overflow-y: auto; padding: 12px 16px; -webkit-overflow-scrolling: touch; }
.sheet-loading { display: block; margin: 60px auto; }
.sheet-fail { text-align: center; color: #94a3b8; font-size: 13px; padding: 60px 0; }
.sheet-empty { display: flex; flex-direction: column; align-items: center; padding: 50px 20px; text-align: center; }
.sheet-empty p { font-size: 14px; color: #475569; margin: 10px 0 0; }
.sheet-empty-desc { font-size: 12px; color: #94a3b8; margin-top: 4px !important; }
.collection-list { display: flex; flex-direction: column; gap: 8px; }
.collection-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  background: #faf8ff;
  border-radius: 14px;
  cursor: pointer;
  transition: background 0.15s;
}
.collection-item:active { background: #f0edfa; }
.collection-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}
.collection-info { flex: 1; min-width: 0; }
.collection-name { font-size: 14px; font-weight: 600; color: #1e293b; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.collection-meta { display: flex; align-items: center; gap: 8px; font-size: 11px; color: #94a3b8; margin-top: 3px; }
.collection-visibility { padding: 1px 8px; border-radius: 8px; background: rgba(139, 92, 246, 0.07); color: #8B5CF6; }

.create-form { margin-top: 14px; background: #faf8ff; border-radius: 14px; padding: 4px 0; overflow: hidden; }
.public-row { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; font-size: 13px; color: #475569; }
.public-hint { font-size: 11px; color: #94a3b8; padding: 0 16px 12px; margin: 0; }

.sheet-footer { flex-shrink: 0; padding: 12px 16px; padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px)); border-top: 1px solid #f1f5f9; }
.create-btn { color: #8B5CF6 !important; border-color: #C4B5FD !important; display: flex; align-items: center; justify-content: center; gap: 6px; }
.create-btn--solid {
  background: linear-gradient(135deg, #8B5CF6, #6366F1) !important;
  border: none !important;
  color: #fff !important;
  font-weight: 600;
  box-shadow: 0 4px 14px rgba(139, 92, 246, 0.25);
}
</style>
