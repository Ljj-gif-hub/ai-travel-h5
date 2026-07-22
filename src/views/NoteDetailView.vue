<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { showToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi, commentApi, uploadApi, followApi } from '../api';

const router = useRouter();
const route = useRoute();

const goBack = () => {
  try { router.back() } catch (e) { router.push('/notes') }
};

const note = ref({});
const comments = ref([]);
const isLoading = ref(true);
const commentText = ref('');
const commentImage = ref('');   // 评论上传的图片URL
const commentVideo = ref('');   // 评论上传的视频URL
const isUploading = ref(false);
const isSending = ref(false);
const currentUserId = ref(null);
const fileInput = ref(null);

// 评论抽屉
const showDrawer = ref(false)
const textareaRef = ref(null)
const canSend = computed(() => (commentText.value.trim() || commentImage.value || commentVideo.value) && !isSending.value)

// 打开抽屉时自动聚焦 → 唤起键盘
const openDrawer = () => {
  showDrawer.value = true
  setTimeout(() => {
    textareaRef.value?.focus()
  }, 350) // 等待抽屉弹出动画完成
}

// 从token简单解析userId（后端JWT里存的）
const parseUserId = () => {
  try {
    const token = getToken();
    if (!token) return null;
    const payload = token.split('.')[1];
    const decoded = JSON.parse(atob(payload));
    return decoded.userId || null;
  } catch { return null }
};

// 提取HTML中的图片src
const extractImages = (html) => {
  if (!html) return []
  const regex = /<img[^>]*\bsrc="([^">]+)"[^>]*>/gi
  const result = []
  let match
  while ((match = regex.exec(html)) !== null) {
    if (match[1]) result.push(match[1])
  }
  return result
}

// 正文中提取的图片列表（用于顶部轮播）
const contentImages = computed(() => {
  return extractImages(note.value.content || '')
})

// 去除图片标签后的正文（图片已在顶部轮播展示，正文不重复显示）
const cleanedContent = computed(() => {
  const html = note.value.content || ''
  return html.replace(/<img[^>]*>/gi, '')
})

// 图片轮播当前索引
const swipeIndex = ref(0)
const onSwipeChange = (index) => {
  swipeIndex.value = index
}

// 关注状态
const isFollowing = ref(false)
const followLoading = ref(false)

// 喜欢（爱心）
const isHearted = ref(false)
const heartCount = ref(0)
const toggleHeart = () => {
  isHearted.value = !isHearted.value
  heartCount.value += isHearted.value ? 1 : -1
}

const handleFollow = async () => {
  if (!getToken()) {
    showToast('请先登录')
    return
  }
  followLoading.value = true
  try {
    if (isFollowing.value) {
      await followApi.unfollow(note.value.userId || note.value.authorId)
      isFollowing.value = false
      showToast('已取消关注')
    } else {
      await followApi.follow(note.value.userId || note.value.authorId)
      isFollowing.value = true
      showToast('关注成功')
    }
  } catch (e) {
    showToast('操作失败')
  } finally {
    followLoading.value = false
  }
}

const loadNote = async () => {
  const id = route.query.id;
  if (!id) {
    showToast('游记不存在');
    router.replace('/notes');
    return;
  }
  isLoading.value = true;
  try {
    const res = await noteApi.getNoteDetail(id);
    if (res.code === 0) {
      note.value = res.data;
    } else {
      showToast(res.message || '获取游记失败');
    }
  } catch (e) {
    showToast('加载失败');
  } finally {
    isLoading.value = false;
  }
};

const loadComments = async () => {
  const id = route.query.id;
  if (!id) return;
  try {
    const res = await commentApi.getComments(id);
    if (res.code === 0) {
      comments.value = res.data;
    }
  } catch (e) { /* ignore */ }
};

const handleLike = async () => {
  if (!getToken()) {
    showToast('请先登录');
    return;
  }
  // 乐观更新
  const prevLiked = note.value.isLiked;
  note.value.isLiked = !note.value.isLiked;
  note.value.likes = (note.value.likes || 0) + (note.value.isLiked ? 1 : -1);

  try {
    const res = await noteApi.likeNote(note.value.id);
    if (res.code === 0) {
      // 用后端返回值覆盖，确保一致性
      note.value.likes = res.data.likes;
      note.value.isLiked = res.data.isLiked;
      showToast(note.value.isLiked ? '点赞成功' : '已取消点赞');
    } else {
      throw new Error(res.message);
    }
  } catch (e) {
    // 失败回滚
    note.value.isLiked = prevLiked;
    note.value.likes = (note.value.likes || 0) + (prevLiked ? 1 : -1);
    showToast('操作失败');
  }
};

// 上传图片或视频
const handleUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  isUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0) {
      if (res.data.type === 'image') {
        commentImage.value = res.data.url;
        commentVideo.value = '';
      } else {
        commentVideo.value = res.data.url;
        commentImage.value = '';
      }
      showToast(res.data.type === 'image' ? '图片已上传' : '视频已上传');
    } else {
      showToast(res.message || '上传失败');
    }
  } catch (e) {
    showToast('上传失败');
  } finally {
    isUploading.value = false;
    // 重置input以便可以重复选择同一个文件
    if (fileInput.value) fileInput.value.value = '';
  }
};

const clearMedia = () => {
  commentImage.value = '';
  commentVideo.value = '';
};

const handleSendComment = async () => {
  const text = commentText.value.trim();
  const img = commentImage.value;
  const vid = commentVideo.value;
  if (!text && !img && !vid) return;
  if (!getToken()) {
    showToast('请先登录');
    return;
  }
  isSending.value = true;
  try {
    const res = await commentApi.addComment(note.value.id, text || null, img || null, vid || null);
    if (res.code === 0) {
      commentText.value = '';
      commentImage.value = '';
      commentVideo.value = '';
      // 添加到列表末尾
      comments.value.push(res.data);
      note.value.comments = (note.value.comments || 0) + 1;
      showToast('评论成功');
    } else {
      showToast(res.message || '评论失败');
    }
  } catch (e) {
    showToast('评论失败');
  } finally {
    isSending.value = false;
  }
};

const handleDeleteComment = async (comment) => {
  try {
    const res = await commentApi.deleteComment(comment.id);
    if (res.code === 0) {
      comments.value = comments.value.filter(c => c.id !== comment.id);
      note.value.comments = Math.max(0, (note.value.comments || 1) - 1);
      showToast('已删除');
    } else {
      showToast(res.message || '删除失败');
    }
  } catch (e) {
    showToast('删除失败');
  }
};

const handleEdit = () => {
  router.push(`/write-note?id=${note.value.id}`);
};

onMounted(() => {
  currentUserId.value = parseUserId();
  loadNote();
  loadComments();
});
</script>

<template>
  <div class="note-detail-page">
    <!-- 自定义顶部栏：左箭头 + 头像昵称 + 关注按钮 -->
    <div class="custom-nav-bar" :style="{ paddingTop: 'env(safe-area-inset-top, 0px)' }">
      <div class="nav-left" @click="goBack">
        <van-icon name="arrow-left" size="22" color="#1f2937" />
      </div>
      <div class="nav-center">
        <van-image
          round
          width="30"
          height="30"
          :src="note.authorAvatar || 'https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png'"
          fit="cover"
          class="nav-avatar"
        />
        <span class="nav-username">{{ note.authorName || '匿名用户' }}</span>
      </div>
      <div class="nav-right">
        <button
          class="follow-btn"
          :class="{ followed: isFollowing }"
          :disabled="followLoading"
          @click.stop="handleFollow"
        >
          {{ isFollowing ? '已关注' : '+关注' }}
        </button>
      </div>
    </div>

    <van-skeleton v-if="isLoading" title avatar row="5" />

    <template v-else-if="note.id">
      <!-- 标题（图之上） -->
      <div class="detail-header">
        <h2 class="detail-title">{{ note.title }}</h2>
      </div>

      <!-- 图片轮播（标题之下、正文之上） -->
      <div v-if="contentImages.length" class="image-swipe-wrap">
        <van-swipe
          :autoplay="0"
          indicator-color="transparent"
          indicator-active-color="transparent"
          :circular="true"
          class="image-swipe"
          @change="onSwipeChange"
        >
          <van-swipe-item v-for="(img, idx) in contentImages" :key="idx">
            <img :src="img" class="swipe-image" />
          </van-swipe-item>
        </van-swipe>
        <!-- 自定义指示点（在图下方） -->
        <div class="swipe-dots-row">
          <span
            v-for="(_, idx) in contentImages"
            :key="idx"
            class="swipe-dot"
            :class="{ active: idx === swipeIndex }"
          ></span>
        </div>
        <!-- 位置信息（椭圆标签） -->
        <div class="location-row" v-if="note.address || note.location || note.city || note.authorCity">
          <span class="location-pill">
            <van-icon name="location-o" size="11" style="margin-right:4px" />
            {{ note.address || note.location || note.city || note.authorCity }}
          </span>
        </div>
      </div>

      <!-- 标签 -->
      <div class="detail-tags" v-if="note.tags && note.tags.length">
        <van-tag v-for="tag in note.tags" :key="tag" type="primary" size="medium" plain>
          {{ tag }}
        </van-tag>
      </div>

      <!-- 正文（图之下） -->
      <div class="detail-content" v-html="cleanedContent"></div>

      <!-- 日期 -->
      <div class="detail-date-row">
        <span class="detail-date">{{ note.date }}</span>
      </div>

      <!-- 评论区（在页面上，不在抽屉里） -->
      <div class="comments-section">
        <div class="comments-title">全部评论 ({{ comments.length }})</div>
        <div v-if="comments.length === 0" class="no-comments">暂无评论，来说两句吧</div>
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <van-image
            round
            width="28px"
            height="28px"
            src="https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png"
            fit="cover"
            class="comment-avatar"
          />
          <div class="comment-body">
            <div class="comment-top">
              <span class="comment-user">用户{{ c.userId }}</span>
              <span class="comment-date">{{ c.date }}</span>
            </div>
            <div class="comment-content" v-if="c.content">{{ c.content }}</div>
            <van-image v-if="c.image" :src="c.image" fit="cover" width="100%" class="comment-media-img" />
            <video v-if="c.video" :src="c.video" controls preload="metadata" class="comment-media-video"></video>
          </div>
          <van-icon
            v-if="currentUserId === c.userId"
            name="delete-o" size="16" color="#999" class="comment-delete"
            @click="handleDeleteComment(c)"
          />
        </div>
      </div>
    </template>

    <!-- 底部操作栏 -->
    <div class="bottom-bar">
      <!-- 写评论椭圆按钮 -->
      <button class="comment-oval-btn" @click="openDrawer">写评论</button>
      <!-- 点赞 & 评论 & 分享 & 喜欢 -->
      <div class="bottom-actions">
        <div class="action-item" :class="{ liked: note.isLiked }" @click="handleLike">
          <van-icon :name="note.isLiked ? 'good-job' : 'good-job-o'" size="24" />
          <span>{{ note.likes || 0 }}</span>
        </div>
        <div class="action-item">
          <van-icon name="chat-o" size="24" />
          <span>{{ note.comments || 0 }}</span>
        </div>
        <div class="action-item">
          <van-icon name="share-o" size="24" />
          <span>分享</span>
        </div>
        <div class="action-item" :class="{ hearted: isHearted }" @click="toggleHeart">
          <van-icon :name="isHearted ? 'like' : 'like-o'" size="24" />
          <span>{{ heartCount || '喜欢' }}</span>
        </div>
      </div>
    </div>

    <!-- 评论输入抽屉（底部弹出，仅用于打字） -->
    <van-popup
      v-model:show="showDrawer"
      position="bottom"
      :style="{ borderRadius: '20px 20px 0 0' }"
      closeable
      close-icon-position="top-right"
    >
      <div class="drawer-content">
        <div class="drawer-input-area">
          <!-- 已选媒体预览 -->
          <div class="media-preview-row" v-if="commentImage || commentVideo">
            <div class="media-preview-item" v-if="commentImage">
              <van-image :src="commentImage" fit="cover" width="50" height="50" radius="6" />
              <van-icon name="close" size="14" color="#fff" class="media-remove" @click="clearMedia" />
            </div>
            <div class="media-preview-item" v-if="commentVideo">
              <video :src="commentVideo" class="preview-video" />
              <van-icon name="close" size="14" color="#fff" class="media-remove" @click="clearMedia" />
            </div>
          </div>
          <!-- 输入框 + 图标 + 发送 -->
          <div class="drawer-input-row">
            <div class="drawer-textarea-wrap">
              <textarea
                ref="textareaRef"
                v-model="commentText"
                class="drawer-textarea"
                placeholder="写评论"
                rows="3"
              ></textarea>
              <div class="textarea-icons">
                <input
                  ref="fileInput"
                  type="file"
                  accept="image/*,video/*"
                  style="display:none"
                  @change="handleUpload"
                />
                <span class="textarea-icon" @click.stop="fileInput?.click()" :class="{ disabled: isUploading }">
                  <van-icon name="photograph" size="16" />
                </span>
                <span class="textarea-icon">
                  <van-icon name="at" size="16" />
                </span>
                <span class="textarea-icon">
                  <van-icon name="smile-o" size="16" />
                </span>
              </div>
            </div>
            <span class="drawer-send-btn" :class="{ active: canSend }" @click="canSend && handleSendComment()">发送</span>
          </div>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.note-detail-page {
  width: 100%;
  min-height: 100vh;
  background: #fff;
  padding-bottom: 60px; /* 底部操作栏高度 */
}

/* ==================== 自定义顶部栏 ==================== */
.custom-nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 8px 4px;
  background: #fff;
  border-bottom: 1px solid #f3f4f6;
  position: sticky;
  top: 0;
  z-index: 1000;
}

.nav-left {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  cursor: pointer;
  flex-shrink: 0;
}

.nav-center {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.nav-avatar {
  flex-shrink: 0;
  border: 1.5px solid rgba(139, 92, 246, 0.2);
}

.nav-username {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-right {
  flex-shrink: 0;
  margin-left: 10px;
}

/* 关注按钮 - 镂空椭圆 */
.follow-btn {
  padding: 5px 14px;
  border: 1px solid #8B5CF6;
  border-radius: 20px;
  background: transparent;
  color: #8B5CF6;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.2s;
}
.follow-btn:active {
  transform: scale(0.94);
  background: rgba(139, 92, 246, 0.06);
}
.follow-btn.followed {
  background: linear-gradient(135deg, #8B5CF6, #6366F1);
  color: #fff;
  border-color: transparent;
}
.follow-btn:disabled {
  opacity: 0.6;
}

/* ==================== 图片轮播 ==================== */
.image-swipe-wrap {
  position: relative;
  background: #f5f5f5;
  padding-bottom: 10px;
}

.image-swipe {
  width: 100%;
}

.swipe-image {
  width: 100%;
  height: auto;
  object-fit: contain;
  display: block;
  background: #f5f5f5;
}

/* 自定义指示点（图下方） */
.swipe-dots-row {
  display: flex;
  justify-content: center;
  gap: 8px;
  padding: 10px 0 6px;
}

.swipe-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #d1d5db;
  transition: all 0.25s;
}
.swipe-dot.active {
  width: 18px;
  border-radius: 3px;
  background: #8B5CF6;
}

/* 位置信息（椭圆标签） */
.location-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 16px 8px;
}

.location-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 20px;
  font-size: 12px;
  color: #6b7280;
  background: #fafafa;
}

/* ==================== 标题 & 日期 ==================== */
.detail-header {
  padding: 16px 16px 0;
}

.detail-title {
  font-size: 20px;
  font-weight: 700;
  color: #1f2937;
  line-height: 1.4;
  margin: 0 0 8px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
}

/* ==================== 正文 ==================== */
.detail-content {
  padding: 0 16px 16px;
  font-size: 15px;
  line-height: 1.8;
  color: #374151;
  word-break: break-word;
  white-space: pre-wrap;
  max-width: 23em; /* 一行最多23字，超出自动换行 */
}

/* 正文中的图片隐藏（已在顶部轮播展示） */
.detail-content :deep(img) {
  display: none;
}

/* ==================== 日期行 ==================== */
.detail-date-row {
  padding: 8px 16px 16px;
}

.detail-date {
  font-size: 12px;
  color: #9ca3af;
}

/* ==================== 底部操作栏 ==================== */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px));
  background: #fff;
  border-top: 1px solid #f3f4f6;
  z-index: 500;
}

.comment-oval-btn {
  padding: 6px 42px;
  border: 1px solid #e5e7eb;
  border-radius: 24px;
  background: #fafafa;
  color: #6b7280;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
  margin-right: 16px;
}
.comment-oval-btn:active {
  background: #f3f4f6;
  transform: scale(0.97);
}

.bottom-actions {
  display: flex;
  align-items: center;
  gap: 16px;
}

.bottom-actions .action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: #6b7280;
  cursor: pointer;
  min-width: 36px;
}
.bottom-actions .action-item.liked {
  color: #e74c3c;
}
.bottom-actions .action-item.hearted {
  color: #ff2d55;
}

/* ==================== 评论区（在页面上） ==================== */
.comments-section {
  padding: 16px;
}

.comments-title {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  margin-bottom: 12px;
}

.no-comments {
  text-align: center;
  font-size: 14px;
  color: #9ca3af;
  padding: 24px 0;
}

.comment-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 0;
  border-bottom: 1px solid #f3f4f6;
}

.comment-avatar {
  flex-shrink: 0;
  margin-top: 2px;
}

.comment-body {
  flex: 1;
  min-width: 0;
}

.comment-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.comment-user {
  font-size: 13px;
  color: #6b7280;
}

.comment-date {
  font-size: 11px;
  color: #9ca3af;
}

.comment-content {
  font-size: 14px;
  color: #374151;
  line-height: 1.6;
  word-break: break-word;
}

.comment-delete {
  flex-shrink: 0;
  margin-top: 4px;
  cursor: pointer;
}

.comment-media-img {
  margin-top: 6px;
  border-radius: 8px;
  max-height: 200px;
  cursor: pointer;
}

.comment-media-video {
  width: 100%;
  max-height: 200px;
  margin-top: 6px;
  border-radius: 8px;
  background: #000;
}

/* 抽屉内容（仅输入区） */
.drawer-content {
  padding: 16px 12px;
  padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px));
}

.drawer-input-area {
  /* 无额外样式 */
}

.media-preview-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.media-preview-item {
  position: relative;
  border-radius: 6px;
  overflow: hidden;
}

.preview-video {
  width: 50px;
  height: 50px;
  object-fit: cover;
  border-radius: 6px;
}

.media-remove {
  position: absolute;
  top: 2px;
  right: 2px;
  background: rgba(0,0,0,0.5);
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
}

.drawer-input-row {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.drawer-textarea-wrap {
  flex: 1;
  position: relative;
  background: #f5f5f5;
  border-radius: 16px;
  padding: 10px 12px;
  min-height: 60px;
}

.drawer-textarea {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: #374151;
  line-height: 1.5;
  resize: none;
  font-family: inherit;
}
.drawer-textarea::placeholder {
  color: #9ca3af;
}

.textarea-icons {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-end;
  margin-top: 4px;
}

.textarea-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: #9ca3af;
  cursor: pointer;
  transition: all 0.15s;
}
.textarea-icon:active {
  background: rgba(139,92,246,0.08);
  color: #8B5CF6;
}
.textarea-icon.disabled {
  opacity: 0.4;
  pointer-events: none;
}

.drawer-send-btn {
  font-size: 14px;
  font-weight: 500;
  color: #d1d5db;
  cursor: default;
  flex-shrink: 0;
  padding: 6px 4px;
  user-select: none;
  transition: color 0.2s;
}
.drawer-send-btn.active {
  color: #8B5CF6;
  cursor: pointer;
}
.drawer-send-btn.active:active {
  transform: scale(0.92);
}
</style>
