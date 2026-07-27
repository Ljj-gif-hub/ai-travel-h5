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
const commentImage = ref('');
const commentVideo = ref('');
const isUploading = ref(false);
const isSending = ref(false);
const currentUserId = ref(null);
const fileInput = ref(null);

/* ==================== 评论 & 回复 & 分享 状态 ==================== */
const replyTo = ref(null)
const showCommentsDrawer = ref(false)
const showShareSheet = ref(false)
const expandedGroups = ref(new Set())

const shareOptions = [
  { name: '微信好友', icon: 'wechat', color: '#07C160' },
  { name: '朋友圈', icon: 'cluster-o', color: '#07C160' },
  { name: '复制链接', icon: 'link-o', color: '#8B5CF6' },
  { name: '保存图片', icon: 'photo-o', color: '#3B82F6' },
  { name: 'QQ好友', icon: 'chat-o', color: '#12B7F5' },
  { name: '微博', icon: 'share-o', color: '#E6162D' },
]

const handleShare = (option) => {
  showShareSheet.value = false
  if (option.name === '复制链接') {
    navigator.clipboard?.writeText(window.location.href)
      .then(() => showToast('链接已复制'))
      .catch(() => showToast('复制失败，请手动复制'))
  } else if (option.name === '保存图片') {
    showToast('长按图片即可保存')
  } else {
    showToast(`分享到${option.name}功能开发中`)
  }
}

/* ==================== 评论输入抽屉 ==================== */
const showDrawer = ref(false)
const textareaRef = ref(null)
const canSend = computed(() => (commentText.value.trim() || commentImage.value || commentVideo.value) && !isSending.value)

const openDrawer = (comment) => {
  showCommentsDrawer.value = false
  replyTo.value = comment || null
  commentText.value = ''
  commentImage.value = ''
  commentVideo.value = ''
  setTimeout(() => { showDrawer.value = true }, 200)
  setTimeout(() => { textareaRef.value?.focus() }, 550)
}
const onDrawerShowChange = (val) => { if (!val) replyTo.value = null }
const cancelReply = () => {
  replyTo.value = null
  commentText.value = ''
  commentImage.value = ''
  commentVideo.value = ''
}
const inputPlaceholder = computed(() => replyTo.value ? `回复 用户${replyTo.value.userId}：` : '写评论')

/* ==================== 评论分组（抖音风格） ==================== */
const findUserById = (id) => {
  const x = comments.value.find(c => c.id === id)
  return x ? `用户${x.userId}` : '用户'
}
const findRootId = (c) => {
  if (!c.parentId) return c.id
  const p = comments.value.find(x => x.id === c.parentId)
  return p ? findRootId(p) : c.parentId
}
const groupedComments = computed(() => {
  const roots = []
  const childMap = {}
  for (const c of comments.value) {
    const rootId = findRootId(c)
    if (rootId === c.id) {
      roots.push({ parent: c, replies: [] })
    } else {
      if (!childMap[rootId]) childMap[rootId] = []
      childMap[rootId].push(c)
    }
  }
  for (const r of roots) {
    r.replies = (childMap[r.parent.id] || []).sort((a, b) => (b.likes || 0) - (a.likes || 0))
  }
  return roots
})
const toggleGroup = (id) => {
  const s = new Set(expandedGroups.value)
  s.has(id) ? s.delete(id) : s.add(id)
  expandedGroups.value = s
}
const withMention = (c) => {
  const mention = c.parentId ? `@${findUserById(c.parentId)} ` : ''
  return { ...c, mention, content: c.content || '' }
}

/* ==================== 数据加载 ==================== */
const parseUserId = () => {
  try {
    const token = getToken();
    if (!token) return null;
    const payload = token.split('.')[1];
    const decoded = JSON.parse(atob(payload));
    return decoded.userId || null;
  } catch { return null }
};

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

const contentImages = computed(() => extractImages(note.value.content || ''))
const cleanedContent = computed(() => {
  const html = note.value.content || ''
  return html.replace(/<img[^>]*>/gi, '')
})

const swipeIndex = ref(0)
const onSwipeChange = (index) => { swipeIndex.value = index }

const isFollowing = ref(false)
const followLoading = ref(false)
const isHearted = ref(false)
const heartCount = ref(0)
const toggleHeart = () => {
  isHearted.value = !isHearted.value
  heartCount.value += isHearted.value ? 1 : -1
}

const handleFollow = async () => {
  if (!getToken()) { showToast('请先登录'); return }
  followLoading.value = true
  try {
    if (isFollowing.value) {
      await followApi.unfollow(note.value.userId || note.value.authorId)
      isFollowing.value = false; showToast('已取消关注')
    } else {
      await followApi.follow(note.value.userId || note.value.authorId)
      isFollowing.value = true; showToast('关注成功')
    }
  } catch (e) { showToast('操作失败') }
  finally { followLoading.value = false }
}

const loadNote = async () => {
  const id = route.query.id;
  if (!id) { showToast('游记不存在'); router.replace('/notes'); return }
  isLoading.value = true;
  try {
    const res = await noteApi.getNoteDetail(id);
    if (res.code === 0) {
      note.value = res.data;
      isFollowing.value = !!res.data.isFollowing;
    } else { showToast(res.message || '获取游记失败') }
  } catch (e) { showToast('加载失败') }
  finally { isLoading.value = false }
};

const loadComments = async () => {
  const id = route.query.id;
  if (!id) return;
  try {
    const res = await commentApi.getComments(id);
    if (res.code === 0) {
      const list = Array.isArray(res.data) ? res.data : [];
      comments.value = list;
      for (const c of list) {
        if (!c.parentId) {
          try {
            const r = await commentApi.getReplies(c.id);
            if (r.code === 0 && r.data?.length) comments.value.push(...r.data);
          } catch {}
        }
      }
    }
  } catch (e) { /* ignore */ }
};

const handleLike = async () => {
  if (!getToken()) { showToast('请先登录'); return }
  const prevLiked = note.value.isLiked;
  note.value.isLiked = !note.value.isLiked;
  note.value.likes = (note.value.likes || 0) + (note.value.isLiked ? 1 : -1);
  try {
    const res = await noteApi.likeNote(note.value.id);
    if (res.code === 0) {
      note.value.likes = res.data.likes;
      note.value.isLiked = res.data.isLiked;
      showToast(note.value.isLiked ? '点赞成功' : '已取消点赞');
    } else { throw new Error(res.message) }
  } catch (e) {
    note.value.isLiked = prevLiked;
    note.value.likes = (note.value.likes || 0) + (prevLiked ? 1 : -1);
    showToast('操作失败');
  }
};

const handleUpload = async (e) => {
  const file = e.target.files?.[0];
  if (!file) return;
  isUploading.value = true;
  try {
    const res = await uploadApi.uploadFile(file);
    if (res.code === 0) {
      if (res.data.type === 'image') { commentImage.value = res.data.url; commentVideo.value = '' }
      else { commentVideo.value = res.data.url; commentImage.value = '' }
      showToast(res.data.type === 'image' ? '图片已上传' : '视频已上传');
    } else { showToast(res.message || '上传失败') }
  } catch (e) { showToast('上传失败') }
  finally { isUploading.value = false; if (fileInput.value) fileInput.value.value = '' }
};

const clearMedia = () => { commentImage.value = ''; commentVideo.value = '' };

const handleSendComment = async () => {
  const text = commentText.value.trim();
  const img = commentImage.value;
  const vid = commentVideo.value;
  if (!text && !img && !vid) return;
  if (!getToken()) { showToast('请先登录'); return }
  isSending.value = true;
  try {
    const parentId = replyTo.value?.id || null
    const res = await commentApi.addComment(note.value.id, text || null, img || null, vid || null, parentId);
    if (res.code === 0) {
      commentText.value = ''; commentImage.value = ''; commentVideo.value = '';
      replyTo.value = null
      comments.value.push(res.data);
      note.value.comments = (note.value.comments || 0) + 1;
      showToast(parentId ? '回复成功' : '评论成功');
    } else { showToast(res.message || '评论失败') }
  } catch (e) { showToast('评论失败') }
  finally { isSending.value = false }
};

const handleDeleteComment = async (comment) => {
  try {
    const res = await commentApi.deleteComment(comment.id);
    if (res.code === 0) {
      comments.value = comments.value.filter(c => c.id !== comment.id);
      note.value.comments = Math.max(0, (note.value.comments || 1) - 1);
      showToast('已删除');
    } else { showToast(res.message || '删除失败') }
  } catch (e) { showToast('删除失败') }
};

const handleEdit = () => { router.push(`/write-note?id=${note.value.id}`) };

onMounted(() => {
  currentUserId.value = parseUserId();
  loadNote();
  loadComments();
});
</script>

<template>
  <div class="note-detail-page">
    <!-- 自定义顶部栏 -->
    <div class="custom-nav-bar" :style="{ paddingTop: 'env(safe-area-inset-top, 0px)' }">
      <div class="nav-left" @click="goBack"><van-icon name="arrow-left" size="22" color="#1f2937" /></div>
      <div class="nav-center">
        <van-image round width="30" height="30" :src="note.authorAvatar || 'https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png'" fit="cover" class="nav-avatar" />
        <span class="nav-username">{{ note.authorName || '匿名用户' }}</span>
      </div>
      <div class="nav-right">
        <button type="button" class="follow-btn" :class="{ followed: isFollowing }" :disabled="followLoading" @click.stop="handleFollow">
          {{ isFollowing ? '已关注' : '+关注' }}
        </button>
      </div>
    </div>

    <van-skeleton v-if="isLoading" title avatar row="5" />

    <template v-else-if="note.id">
      <div class="detail-header"><h2 class="detail-title">{{ note.title }}</h2></div>

      <!-- 图片轮播 -->
      <div v-if="contentImages.length" class="image-swipe-wrap">
        <van-swipe :autoplay="0" indicator-color="transparent" indicator-active-color="transparent" :circular="true" class="image-swipe" @change="onSwipeChange">
          <van-swipe-item v-for="(img, idx) in contentImages" :key="idx"><img :src="img" class="swipe-image" /></van-swipe-item>
        </van-swipe>
        <div class="swipe-dots-row">
          <span v-for="(_, idx) in contentImages" :key="idx" class="swipe-dot" :class="{ active: idx === swipeIndex }"></span>
        </div>
        <div class="location-row" v-if="note.address || note.location || note.city || note.authorCity">
          <span class="location-pill"><van-icon name="location-o" size="11" style="margin-right:4px" />{{ note.address || note.location || note.city || note.authorCity }}</span>
        </div>
      </div>

      <div class="detail-tags" v-if="note.tags && note.tags.length">
        <van-tag v-for="tag in note.tags" :key="tag" type="primary" size="medium" plain>{{ tag }}</van-tag>
      </div>
      <div class="detail-content" v-html="cleanedContent"></div>
      <div class="detail-date-row"><span class="detail-date">{{ note.date }}</span></div>

      <!-- 评论区（分组显示） -->
      <div class="comments-section">
        <div class="comments-title">全部评论 ({{ comments.length }})</div>
        <div v-if="comments.length === 0" class="no-comments">暂无评论，来说两句吧</div>
        <template v-for="g in groupedComments" :key="g.parent.id">
          <div class="comment-item">
            <van-image round width="28px" height="28px" src="https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png" fit="cover" class="comment-avatar" />
            <div class="comment-body">
              <div class="comment-top"><span class="comment-user">用户{{ g.parent.userId }}</span><span class="comment-date">{{ g.parent.date }}</span></div>
              <div class="comment-content" v-if="g.parent.content">{{ g.parent.content }}</div>
              <van-image v-if="g.parent.image" :src="g.parent.image" fit="cover" width="100%" class="comment-media-img" />
              <video v-if="g.parent.video" :src="g.parent.video" controls preload="metadata" class="comment-media-video"></video>
              <button type="button" class="comment-reply-btn" @click="openDrawer({ id: g.parent.id, userId: g.parent.userId })">回复</button>
            </div>
            <van-icon v-if="currentUserId === g.parent.userId" name="delete-o" size="16" color="#999" class="comment-delete" @click="handleDeleteComment(g.parent)" />
          </div>
          <template v-if="g.replies.length">
            <div v-for="(r, i) in g.replies" :key="r.id" v-show="i < 2 || expandedGroups.has(g.parent.id)" class="comment-item is-reply">
              <van-image round width="20px" height="20px" src="https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png" fit="cover" class="comment-avatar" />
              <div class="comment-body">
                <div class="comment-top"><span class="comment-user">用户{{ r.userId }}</span><span class="comment-date">{{ r.date }}</span></div>
                <div class="comment-content" v-if="withMention(r).mention || withMention(r).content">
                  <span v-if="withMention(r).mention" class="comment-mention">{{ withMention(r).mention }}</span>{{ withMention(r).content }}
                </div>
                <van-image v-if="r.image" :src="r.image" fit="cover" width="100%" class="comment-media-img" />
                <video v-if="r.video" :src="r.video" controls preload="metadata" class="comment-media-video"></video>
                <button type="button" class="comment-reply-btn" @click="openDrawer({ id: r.id, userId: r.userId })">回复</button>
              </div>
              <van-icon v-if="currentUserId === r.userId" name="delete-o" size="16" color="#999" class="comment-delete" @click="handleDeleteComment(r)" />
            </div>
            <button v-if="g.replies.length > 2" type="button" class="expand-replies-btn" @click="toggleGroup(g.parent.id)">
              {{ expandedGroups.has(g.parent.id) ? '收起' : `展开剩下的 ${g.replies.length - 2} 条回复` }}
            </button>
          </template>
        </template>
      </div>
    </template>

    <!-- 底部操作栏 -->
    <div class="bottom-bar">
      <button type="button" class="comment-oval-btn" @click="openDrawer()">写评论</button>
      <div class="bottom-actions">
        <div class="action-item" :class="{ liked: note.isLiked }" @click="handleLike">
          <van-icon :name="note.isLiked ? 'good-job' : 'good-job-o'" size="24" /><span>{{ note.likes || 0 }}</span>
        </div>
        <div class="action-item" @click="showCommentsDrawer = true">
          <van-icon name="chat-o" size="24" /><span>{{ note.comments || 0 }}</span>
        </div>
        <div class="action-item" @click="showShareSheet = true">
          <van-icon name="share-o" size="24" /><span>分享</span>
        </div>
        <div class="action-item" :class="{ hearted: isHearted }" @click="toggleHeart">
          <van-icon :name="isHearted ? 'like' : 'like-o'" size="24" /><span>{{ heartCount || '喜欢' }}</span>
        </div>
      </div>
    </div>

    <!-- 评论列表抽屉 -->
    <van-popup v-model:show="showCommentsDrawer" position="bottom" :style="{ height: '65%', borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="comments-drawer">
        <div class="comments-drawer-header">
          <span class="comments-drawer-title">全部评论 ({{ comments.length }})</span>
          <van-icon name="cross" size="20" color="#94a3b8" @click="showCommentsDrawer = false" />
        </div>
        <div class="comments-drawer-body">
          <div v-if="comments.length === 0" class="no-comments">暂无评论，来说两句吧</div>
          <template v-for="g in groupedComments" :key="'d'+g.parent.id">
            <div class="comment-item">
              <van-image round width="28px" height="28px" src="https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png" fit="cover" class="comment-avatar" />
              <div class="comment-body">
                <div class="comment-top"><span class="comment-user">用户{{ g.parent.userId }}</span><span class="comment-date">{{ g.parent.date }}</span></div>
                <div class="comment-content" v-if="g.parent.content">{{ g.parent.content }}</div>
                <van-image v-if="g.parent.image" :src="g.parent.image" fit="cover" width="100%" class="comment-media-img" />
                <video v-if="g.parent.video" :src="g.parent.video" controls preload="metadata" class="comment-media-video"></video>
                <button type="button" class="comment-reply-btn" @click="openDrawer({ id: g.parent.id, userId: g.parent.userId })">回复</button>
              </div>
              <van-icon v-if="currentUserId === g.parent.userId" name="delete-o" size="16" color="#999" class="comment-delete" @click="handleDeleteComment(g.parent)" />
            </div>
            <template v-if="g.replies.length">
              <div v-for="(r, i) in g.replies" :key="r.id" v-show="i < 2 || expandedGroups.has(g.parent.id)" class="comment-item is-reply">
                <van-image round width="20px" height="20px" src="https://img.zcool.cn/community/01e5e35c5c5c5ea80121985c5c5c5c.png" fit="cover" class="comment-avatar" />
                <div class="comment-body">
                  <div class="comment-top"><span class="comment-user">用户{{ r.userId }}</span><span class="comment-date">{{ r.date }}</span></div>
                  <div class="comment-content" v-if="withMention(r).mention || withMention(r).content">
                    <span v-if="withMention(r).mention" class="comment-mention">{{ withMention(r).mention }}</span>{{ withMention(r).content }}
                  </div>
                  <van-image v-if="r.image" :src="r.image" fit="cover" width="100%" class="comment-media-img" />
                  <video v-if="r.video" :src="r.video" controls preload="metadata" class="comment-media-video"></video>
                  <button type="button" class="comment-reply-btn" @click="openDrawer({ id: r.id, userId: r.userId })">回复</button>
                </div>
                <van-icon v-if="currentUserId === r.userId" name="delete-o" size="16" color="#999" class="comment-delete" @click="handleDeleteComment(r)" />
              </div>
              <button v-if="g.replies.length > 2" type="button" class="expand-replies-btn" @click="toggleGroup(g.parent.id)">
                {{ expandedGroups.has(g.parent.id) ? '收起' : `展开剩下的 ${g.replies.length - 2} 条回复` }}
              </button>
            </template>
          </template>
        </div>
        <div class="comments-drawer-footer">
          <button type="button" class="comment-oval-btn drawer-write-btn" @click="openDrawer()">写评论</button>
        </div>
      </div>
    </van-popup>

    <!-- 分享面板 -->
    <van-popup v-model:show="showShareSheet" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" round safe-area-inset-bottom>
      <div class="share-sheet">
        <div class="share-sheet-title">分享至</div>
        <div class="share-grid">
          <div v-for="opt in shareOptions" :key="opt.name" class="share-option" @click="handleShare(opt)">
            <div class="share-icon-circle" :style="{ background: opt.color }"><van-icon :name="opt.icon" size="22" color="#fff" /></div>
            <span class="share-label">{{ opt.name }}</span>
          </div>
        </div>
        <button type="button" class="share-cancel" @click="showShareSheet = false">取消</button>
      </div>
    </van-popup>

    <!-- 评论输入抽屉 -->
    <van-popup v-model:show="showDrawer" @update:show="onDrawerShowChange" position="bottom" :style="{ borderRadius: '20px 20px 0 0' }" closeable close-icon-position="top-right">
      <div class="drawer-content">
        <div class="drawer-input-area">
          <div v-if="replyTo" class="reply-indicator">
            <span class="reply-indicator-text">回复 <strong>用户{{ replyTo.userId }}</strong></span>
            <van-icon name="cross" size="14" color="#94a3b8" @click="cancelReply" class="reply-cancel" />
          </div>
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
          <div class="drawer-input-row">
            <div class="drawer-textarea-wrap">
              <textarea ref="textareaRef" v-model="commentText" class="drawer-textarea" :placeholder="inputPlaceholder" rows="3"></textarea>
              <div class="textarea-icons">
                <input ref="fileInput" type="file" accept="image/*,video/*" style="display:none" @change="handleUpload" />
                <span class="textarea-icon" @click.stop="fileInput?.click()" :class="{ disabled: isUploading }"><van-icon name="photograph" size="16" /></span>
                <span class="textarea-icon"><van-icon name="at" size="16" /></span>
                <span class="textarea-icon"><van-icon name="smile-o" size="16" /></span>
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
.note-detail-page { width: 100%; min-height: 100vh; background: transparent; padding-bottom: 80px; box-sizing: border-box; }

.custom-nav-bar { display: flex; align-items: center; justify-content: space-between; padding: 8px 12px 8px 4px; background: #fff; border-bottom: 1px solid #f3f4f6; position: sticky; top: 0; z-index: 1000; }
.nav-left { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; cursor: pointer; flex-shrink: 0; }
.nav-center { display: flex; align-items: center; gap: 8px; flex: 1; min-width: 0; }
.nav-avatar { flex-shrink: 0; border: 1.5px solid rgba(139, 92, 246, 0.2); }
.nav-username { font-size: 15px; font-weight: 600; color: #1f2937; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.nav-right { flex-shrink: 0; margin-left: 10px; }

.follow-btn { padding: 5px 14px; border: 1px solid #8B5CF6; border-radius: 20px; background: transparent; color: #8B5CF6; font-size: 12px; font-weight: 500; cursor: pointer; white-space: nowrap; transition: all 0.2s; }
.follow-btn:active { transform: scale(0.94); background: rgba(139, 92, 246, 0.06); }
.follow-btn.followed { background: linear-gradient(135deg, #8B5CF6, #6366F1); color: #fff; border-color: transparent; }
.follow-btn:disabled { opacity: 0.6; }

.image-swipe-wrap { position: relative; background: #f5f5f5; padding-bottom: 10px; }
.image-swipe { width: 100%; }
.swipe-image { width: 100%; height: auto; object-fit: contain; display: block; background: #f5f5f5; }
.swipe-dots-row { display: flex; justify-content: center; gap: 8px; padding: 10px 0 6px; }
.swipe-dot { width: 6px; height: 6px; border-radius: 50%; background: #d1d5db; transition: all 0.25s; }
.swipe-dot.active { width: 18px; border-radius: 3px; background: #8B5CF6; }
.location-row { display: flex; flex-wrap: wrap; gap: 8px; padding: 0 16px 8px; }
.location-pill { display: inline-flex; align-items: center; padding: 4px 14px; border: 1px solid #e5e7eb; border-radius: 20px; font-size: 12px; color: #6b7280; background: #fafafa; }

.detail-header { padding: 16px 16px 0; }
.detail-title { font-size: 20px; font-weight: 700; color: #1f2937; line-height: 1.4; margin: 0 0 8px; }
.detail-tags { display: flex; flex-wrap: wrap; gap: 8px; padding: 12px 16px; }
.detail-content { padding: 0 16px 16px; font-size: 15px; line-height: 1.8; color: #374151; word-break: break-word; white-space: pre-wrap; max-width: 23em; }
.detail-content :deep(img) { display: none; }
.detail-date-row { padding: 8px 16px 16px; }
.detail-date { font-size: 12px; color: #9ca3af; }

/* 底部操作栏 */
.bottom-bar { position: fixed; bottom: 0; left: 0; right: 0; display: flex; align-items: center; justify-content: space-between; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px)); background: #fff; border-top: 1px solid #f3f4f6; z-index: 500; }
.comment-oval-btn { padding: 6px 42px; border: 1px solid #e5e7eb; border-radius: 24px; background: #fafafa; color: #6b7280; font-size: 14px; cursor: pointer; transition: all 0.2s; margin-right: 16px; }
.comment-oval-btn:active { background: #f3f4f6; transform: scale(0.97); }
.bottom-actions { display: flex; align-items: center; gap: 16px; }
.bottom-actions .action-item { display: flex; flex-direction: column; align-items: center; gap: 2px; font-size: 11px; color: #6b7280; cursor: pointer; min-width: 36px; }
.bottom-actions .action-item.liked { color: #e74c3c; }
.bottom-actions .action-item.hearted { color: #ff2d55; }

/* 评论区 */
.comments-section { padding: 16px; }
.comments-title { font-size: 15px; font-weight: 600; color: #1f2937; margin-bottom: 12px; }
.no-comments { text-align: center; font-size: 14px; color: #9ca3af; padding: 24px 0; }
.comment-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px 0; border-bottom: 1px solid #f3f4f6; }
.comment-avatar { flex-shrink: 0; margin-top: 2px; }
.comment-body { flex: 1; min-width: 0; }
.comment-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.comment-user { font-size: 13px; color: #6b7280; }
.comment-date { font-size: 11px; color: #9ca3af; }
.comment-content { font-size: 14px; color: #374151; line-height: 1.6; word-break: break-word; }
.comment-delete { flex-shrink: 0; margin-top: 4px; cursor: pointer; }
.comment-media-img { margin-top: 6px; border-radius: 8px; max-height: 200px; cursor: pointer; }
.comment-media-video { width: 100%; max-height: 200px; margin-top: 6px; border-radius: 8px; background: #000; }

/* 回复样式 */
.comment-mention { color: #8B5CF6; font-weight: 500; }
.comment-reply-btn { display: inline-block; margin-top: 6px; padding: 2px 10px; border: none; border-radius: 10px; background: rgba(139,92,246,0.06); color: #8B5CF6; font-size: 11px; cursor: pointer; transition: all 0.15s; }
.comment-reply-btn:active { background: rgba(139,92,246,0.14); transform: scale(0.95); }
.comment-item.is-reply { margin-left: 12px; padding: 6px 0 6px 6px; border-left: 1.5px solid rgba(139,92,246,0.1); border-bottom: none; gap: 4px; }
.comment-item.is-reply .comment-avatar { width: 20px !important; height: 20px !important; }
.comment-item.is-reply .comment-user { font-size: 11px; }
.comment-item.is-reply .comment-content { font-size: 12px; }
.comment-item.is-reply .comment-reply-btn { font-size: 10px; padding: 1px 8px; }
.expand-replies-btn { display: block; margin: 0 0 2px 12px; padding: 4px 0; border: none; background: transparent; color: #8B5CF6; font-size: 11px; font-weight: 500; cursor: pointer; }
.expand-replies-btn:active { opacity: 0.7; }

/* 评论抽屉 */
.comments-drawer { display: flex; flex-direction: column; height: 100%; background: #fff; }
.comments-drawer-header { display: flex; align-items: center; justify-content: space-between; padding: 18px 20px 14px; border-bottom: 1px solid #f1f5f9; flex-shrink: 0; }
.comments-drawer-title { font-size: 17px; font-weight: 700; color: #1e293b; }
.comments-drawer-body { flex: 1; overflow-y: auto; padding: 8px 16px 16px; }
.comments-drawer-footer { flex-shrink: 0; padding: 10px 16px; padding-bottom: calc(10px + env(safe-area-inset-bottom, 0px)); border-top: 1px solid #f3f4f6; }
.drawer-write-btn { margin-right: 0; }

/* 分享面板 */
.share-sheet { padding: 24px 20px; padding-bottom: calc(20px + env(safe-area-inset-bottom, 0px)); background: #fff; }
.share-sheet-title { font-size: 16px; font-weight: 600; color: #1e293b; text-align: center; margin-bottom: 24px; }
.share-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px 8px; margin-bottom: 28px; }
.share-option { display: flex; flex-direction: column; align-items: center; gap: 8px; cursor: pointer; transition: transform 0.15s; }
.share-option:active { transform: scale(0.9); }
.share-icon-circle { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; }
.share-label { font-size: 11px; color: #64748b; }
.share-cancel { width: 100%; padding: 12px; border: none; border-radius: 14px; background: #f5f5f5; color: #64748b; font-size: 15px; cursor: pointer; transition: background 0.15s; }
.share-cancel:active { background: #e5e5e5; }

/* 输入抽屉 */
.drawer-content { padding: 16px 12px; padding-bottom: calc(16px + env(safe-area-inset-bottom, 0px)); }
.reply-indicator { display: flex; align-items: center; justify-content: space-between; padding: 6px 10px; margin-bottom: 8px; background: rgba(139,92,246,0.06); border-radius: 10px; font-size: 12px; color: #64748b; }
.reply-indicator-text strong { color: #8B5CF6; }
.reply-cancel { cursor: pointer; flex-shrink: 0; padding: 2px; }
.media-preview-row { display: flex; gap: 8px; margin-bottom: 8px; }
.media-preview-item { position: relative; border-radius: 6px; overflow: hidden; }
.preview-video { width: 50px; height: 50px; object-fit: cover; border-radius: 6px; }
.media-remove { position: absolute; top: 2px; right: 2px; background: rgba(0,0,0,0.5); border-radius: 50%; padding: 2px; cursor: pointer; }
.drawer-input-row { display: flex; align-items: flex-end; gap: 8px; }
.drawer-textarea-wrap { flex: 1; position: relative; background: #f5f5f5; border-radius: 16px; padding: 10px 12px; min-height: 60px; }
.drawer-textarea { width: 100%; border: none; outline: none; background: transparent; font-size: 14px; color: #374151; line-height: 1.5; resize: none; font-family: inherit; }
.drawer-textarea::placeholder { color: #9ca3af; }
.textarea-icons { display: flex; align-items: center; gap: 6px; justify-content: flex-end; margin-top: 4px; }
.textarea-icon { display: flex; align-items: center; justify-content: center; width: 26px; height: 26px; border-radius: 50%; color: #9ca3af; cursor: pointer; transition: all 0.15s; }
.textarea-icon:active { background: rgba(139,92,246,0.08); color: #8B5CF6; }
.textarea-icon.disabled { opacity: 0.4; pointer-events: none; }
.drawer-send-btn { font-size: 14px; font-weight: 500; color: #d1d5db; cursor: default; flex-shrink: 0; padding: 6px 4px; user-select: none; transition: color 0.2s; }
.drawer-send-btn.active { color: #8B5CF6; cursor: pointer; }
.drawer-send-btn.active:active { transform: scale(0.92); }
</style>
