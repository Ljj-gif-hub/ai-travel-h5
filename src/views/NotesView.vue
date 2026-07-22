<script setup>
import { ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { showToast, showLoadingToast, closeToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi } from '../api';

const router = useRouter();

const goBack = () => {
  try { router.back() } catch (e) { router.push('/') }
};

const notes = ref([]);
const isLoading = ref(false);

const loadNotes = async () => {
  isLoading.value = true;
  try {
    const response = await noteApi.getMyNotes();
    if (response.code === 0) {
      notes.value = response.data;
    } else {
      notes.value = [];
    }
  } catch (error) {
    console.log('获取游记列表失败:', error);
    notes.value = [];
  } finally {
    isLoading.value = false;
  }
};

const handleDelete = async (id) => {
  try {
    const response = await noteApi.deleteNote(id);
    if (response.code === 0) {
      showToast('删除成功');
      loadNotes();
    } else {
      showToast(response.message || '删除失败');
    }
  } catch (error) {
    showToast('删除失败');
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
    loadNotes();
  }
});
</script>

<template>
  <div class="notes-page">
    <van-nav-bar
      title="我的游记"
      left-text="返回"
      left-arrow
      safe-area-inset-top
      @click-left="goBack"
    />

    <div class="content-area">
      <van-skeleton v-if="isLoading" title row="3" />

      <template v-else-if="notes.length === 0">
        <div class="empty-state">
          <van-icon name="edit" size="60" color="#ccc" />
          <div class="empty-text">暂无游记</div>
          <van-button type="primary" size="small" @click="handleWriteNote">去写一篇</van-button>
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
            :src="note.cover || '/api/proxy/image?url=https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?prompt=beautiful%20travel%20scenery&image_size=landscape_16_9'"
            fit="cover"
            class="note-cover"
          />
          <div class="note-content">
            <div class="note-title">{{ note.title }}</div>
            <div class="note-meta">
              <span class="meta-item">{{ note.views }}阅读</span>
              <span class="meta-item">{{ note.likes }}赞</span>
              <span class="meta-item">{{ note.comments }}评论</span>
              <span class="meta-item">{{ note.date }}</span>
            </div>
          </div>
          <div class="note-actions">
            <van-button size="small" type="default" @click.stop="router.push(`/write-note?id=${note.id}`)">编辑</van-button>
            <van-button size="small" type="danger" @click.stop="handleDelete(note.id)">删除</van-button>
          </div>
        </div>
      </div>
    </div>

    <div class="float-btn-wrap">
      <van-button type="primary" round icon="edit" @click="handleWriteNote">
        写游记
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.notes-page {
  width: 100%;
  min-height: 100vh;
  background: linear-gradient(180deg, #faf5ff 0%, #f0f4ff 40%, #f1f5f9 100%);
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
  background: #fff;
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
