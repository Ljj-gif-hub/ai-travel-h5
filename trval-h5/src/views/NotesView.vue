<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { showToast, showLoadingToast, closeToast, showConfirmDialog } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi } from '../api';
import { useRequest } from '../composables/useRequest';

const router = useRouter();
const { t } = useI18n();

const getDefaultCover = () => {
  return '/images/landmarks/692e92669c0c.jpg'
}

const goBack = () => {
  try { router.back() } catch (e) { router.push('/') }
};

/*
 * 【useRequest 迁移示例】列表加载三态统一封装：
 * - loading/error/data 由 useRequest 管理，组件卸载自动 abort 在途请求
 * - loadNotes 即 run()：重复调用会取消旧请求（防竞态），AbortError 静默
 * - 其他视图可参照此写法逐步迁移（CommunityView 首屏请求已接 useRequest）
 */
const {
  data: notesData,
  loading: isLoading,
  error: loadError,
  run: loadNotes,
} = useRequest(
  async (opts) => {
    const response = await noteApi.getMyNotes({ signal: opts.signal });
    if (response.code === 0) {
      return Array.isArray(response.data) ? response.data : [];
    }
    return [];
  },
  { manual: true, initialData: [] }
);

const notes = computed(() => notesData.value ?? []);

const deleting = ref(false)  // BUGID FEAT-3 修复：删除防重锁，请求未完成前忽略重复点击

const handleDelete = async (id) => {
  // BUGID FEAT-3 修复：删除前弹确认框，用户取消则中止
  try {
    await showConfirmDialog({
      title: t('common.tip'),
      message: `${t('common.delete')} ${t('note.myNotes')}？`,
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
    });
  } catch (e) { return }
  if (deleting.value) return
  deleting.value = true
  try {
    const response = await noteApi.deleteNote(id);
    if (response.code === 0) {
      showToast(t('note.deleteSuccess'));
      loadNotes().catch(() => {});
    } else {
      showToast(response.message || t('note.deleteFailed'));
    }
  } catch (error) {
    showToast(t('note.deleteFailed'));
  } finally {
    deleting.value = false
  }
};

/*
 * 游记卡片点击 → 跳转到详情页
 */
const handleItemClick = (note) => {
  try {
    if (!note || !note.id) return
    router.push(`/note-detail?id=${note.id}`)
  } catch (e) { console.error('handleItemClick 失败:', e) }
};

const handleWriteNote = () => {
  router.push('/write-note');
};

onMounted(() => {
  if (getToken()) {
    loadNotes().catch(() => {});
  }
});
</script>

<template>
  <div class="notes-page">
    <van-nav-bar
      :title="t('note.myNotes')"
      :left-text="t('common.back')"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="content-area">
      <van-skeleton v-if="isLoading" title row="3" />

      <template v-else-if="notes.length === 0">
        <div class="empty-state">
          <van-icon name="edit" size="60" color="#ccc" />
          <div class="empty-text">{{ t('note.noNotes') }}</div>
          <van-button type="primary" size="small" @click="handleWriteNote">{{ t('note.goWriteOne') }}</van-button>
        </div>
      </template>

      <div v-else class="notes-list">
        <div
          v-for="note in notes"
          :key="note.id"
          class="note-card"
          @click="handleItemClick(note)"
        >
          <van-image
            width="100%"
            height="160px"
            :src="note.cover || getDefaultCover()"
            fit="cover"
            class="note-cover"
          />
          <div class="note-content">
            <div class="note-title">{{ note.title }}</div>
            <div class="note-meta">
              <span class="meta-item">{{ note.views }}{{ t('note.reads') }}</span>
              <span class="meta-item">{{ note.likes }}{{ t('note.likes') }}</span>
              <span class="meta-item">{{ note.comments }}{{ t('note.comments') }}</span>
              <span class="meta-item">{{ note.date }}</span>
            </div>
          </div>
          <div class="note-actions">
            <van-button size="small" type="default" @click.stop="router.push(`/write-note?id=${note.id}`)">{{ t('common.edit') }}</van-button>
            <van-button size="small" type="danger" @click.stop="handleDelete(note.id)">{{ t('common.delete') }}</van-button>
          </div>
        </div>
      </div>
    </div>

    <div class="float-btn-wrap">
      <van-button type="primary" round icon="edit" @click="handleWriteNote">
        {{ t('community.write') }}
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.notes-page {
  width: 100%;
  min-height: 100vh;
  background: transparent;
  padding-bottom: calc(var(--tabbar-height) + 100px + var(--safe-area-bottom));
}

.content-area {
  padding: 16px;
  box-sizing: border-box;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
}

.empty-text {
  margin-top: 16px;
  font-size: 15px;
  color: #9ca3af;
}

.notes-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.note-card {
  background: rgba(255,255,255,0.5);
  backdrop-filter: blur(14px) saturate(160%);
  -webkit-backdrop-filter: blur(14px) saturate(160%);
  border: 1px solid rgba(255,255,255,0.5);
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.04);
}

.note-cover {
  width: 100%;
}

.note-content {
  padding: 16px;
}

.note-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.note-meta {
  display: flex;
  gap: 12px;
}

.meta-item {
  font-size: 12px;
  color: #9ca3af;
}

.note-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 16px 16px;
}

.float-btn-wrap {
  position: fixed;
  bottom: calc(var(--tabbar-height) + 20px + var(--safe-area-bottom));
  right: 16px;
}
</style>
