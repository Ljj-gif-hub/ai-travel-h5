<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { showToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi, commentApi } from '../api';

const router = useRouter();
const route = useRoute();

const goBack = () => { try { router.back() } catch (e) { router.push('/community') } };

const notes = ref([]);
const currentIdx = ref(0);
const isLoading = ref(true);
const isLiked = ref(false);
const likeCount = ref(0);
const commentCount = ref(0);
const commentInput = ref('');
const comments = ref([]);
// ===== 回复功能（抖音风格） =====
const expandedReplies = reactive({});   // { commentId: true/false } 是否已展开回复
const replyList = reactive({});         // { commentId: [reply, ...] } 缓存已加载的回复
const replyInputs = reactive({});       // { commentId: 'text' } 回复输入框文字
const replyTarget = ref(null);          // { id, authorName } 当前正在回复的评论
const isPlaying = ref(true);
const videoRef = ref(null);
// ===== 视频滑动（抖音风格跟手拖拽） =====
const videoDragY = ref(0);          // 当前拖拽偏移量(px)
const isVideoDragging = ref(false); // 手指按下中
const videoSnapping = ref(false);   // 松手回弹/吸附动画中
const showSwipeHint = ref(true);    // 上滑提示，几秒后自动消失
const touchStartY = ref(0);
const isTransitioning = ref(false);
const slideDirection = ref('up'); // 'up'=下一个视频, 'down'=上一个视频
const heartBurst = ref(false);
const isHearted = ref(false);
const heartCount = ref(0);

// ===== 抽屉状态 =====
const drawerLevel = ref(0);       // 0=关闭, 1=半屏, 2=大部分, 3=全屏(视频隐藏)
const dragOffset = ref(0);
const dragStartY = ref(0);
const dragging = ref(false);

// 每个level对应的视频占比 (0~1)：第一段抽屉就露出大部分
const LEVEL_VIDEO_RATIO = [0.9, 0.35, 0.15, 0];

const closeDrawer = () => { drawerLevel.value = 0; dragOffset.value = 0; };
const openDrawer = () => { drawerLevel.value = 1; dragOffset.value = 0; };

// 视频区弹性比例（连续值，0~1）
const videoFlexRatio = computed(() => {
  const base = LEVEL_VIDEO_RATIO[drawerLevel.value];
  if (!dragging.value || dragOffset.value === 0) return base;
  const vh = window.innerHeight;
  const delta = (dragOffset.value / vh);
  return Math.max(0, Math.min(1, base + delta * 0.7));
});

// 视频区style
const videoStyle = computed(() => {
  const ratio = videoFlexRatio.value;
  if (ratio >= 0.99) return { flex: '1 1 100%' };
  if (ratio <= 0.01) return { flex: '0 0 0%', opacity: 0 };
  return { flex: `0 0 ${Math.round(ratio * 100)}%` };
});

// 抽屉是否可见
const drawerVisible = computed(() => drawerLevel.value > 0);

// 拖拽
const onDragStart = (e) => {
  dragging.value = true;
  dragStartY.value = e.touches[0].clientY;
};
const onDragMove = (e) => {
  if (!dragging.value) return;
  dragOffset.value = e.touches[0].clientY - dragStartY.value;
};
const onDragEnd = () => {
  if (!dragging.value) return;
  dragging.value = false;
  const vh = window.innerHeight;
  const ratio = (dragOffset.value / vh) * 100;
  if (ratio > 8) {
    // 下拉 → 降一级
    if (drawerLevel.value <= 1) closeDrawer();
    else drawerLevel.value -= 1;
  } else if (ratio < -8) {
    // 上拉 → 升一级
    drawerLevel.value = Math.min(3, drawerLevel.value + 1);
  }
  dragOffset.value = 0;
};

const current = computed(() => notes.value[currentIdx.value] || {});
const extractVideoUrl = (note) => {
  if (!note) return '';
  if (note.cover && /\.(mp4|webm|mov)(\?|$)/i.test(note.cover)) return note.cover;
  if (note.content) {
    const m = note.content.match(/<video[^>]*src="([^">]+)"/i) || note.content.match(/<source[^>]*src="([^">]+)"/i);
    if (m) return m[1];
  }
  return '';
};
const videoUrl = computed(() => extractVideoUrl(current.value));

const loadVideos = async () => {
  const startId = route.query.id;
  isLoading.value = true;
  try {
    // 【修复】视频广场应加载所有用户的视频游记，而非仅当前用户
    const res = await noteApi.getAllNotes();
    if (res?.code === 0 && res.data) {
      const all = Array.isArray(res.data) ? res.data : [];
      const withVideo = all.filter(n => {
        const c = n.cover || '', t = n.content || '';
        return /\.(mp4|webm|mov)(\?|$)/i.test(c) || /<video[^>]*src=/i.test(t);
      });
      notes.value = withVideo;
      const idx = withVideo.findIndex(n => String(n.id) === String(startId));
      currentIdx.value = idx >= 0 ? idx : 0;
    }
  } catch (e) { console.warn('加载视频失败:', e); }
  finally {
    isLoading.value = false;
    await nextTick();
    updateState();
    // 上滑提示 3 秒后自动消失
    showSwipeHint.value = notes.value.length > 1;
    if (showSwipeHint.value) {
      setTimeout(() => { showSwipeHint.value = false; }, 3000);
    }
  }
};

const updateState = () => {
  const n = current.value; if (!n) return;
  isLiked.value = n.isLiked || false;
  likeCount.value = n.likes || n.likeCount || 0;
  commentCount.value = n.comments || n.commentCount || 0;
};

const loadComments = async () => {
  const id = current.value?.id; if (!id) return;
  try { const res = await commentApi.getComments(id); if (res.code === 0) comments.value = res.data; } catch {}
};

const goToVideo = async (idx) => {
  if (idx < 0 || idx >= notes.value.length || isTransitioning.value) return;
  isTransitioning.value = true;
  currentIdx.value = idx;
  comments.value = [];
  // 清理回复状态
  Object.keys(expandedReplies).forEach(k => delete expandedReplies[k]);
  Object.keys(replyList).forEach(k => delete replyList[k]);
  Object.keys(replyInputs).forEach(k => delete replyInputs[k]);
  replyTarget.value = null;
  commentInput.value = '';
  updateState();
  await nextTick();
  if (videoRef.value) { videoRef.value.load(); videoRef.value.play(); isPlaying.value = true; }
  loadComments();
  setTimeout(() => { isTransitioning.value = false; }, 400);
};

// ===== 抖音风格视频滑动 =====
const onVideoTouchStart = (e) => {
  if (drawerVisible.value) return;
  isVideoDragging.value = true;
  videoSnapping.value = false;
  touchStartY.value = e.touches[0].clientY;
};
const onVideoTouchMove = (e) => {
  if (!isVideoDragging.value) return;
  let delta = e.touches[0].clientY - touchStartY.value;
  // 边界阻力：首条下拉 / 末条上拉
  if ((currentIdx.value === 0 && delta > 0) || (currentIdx.value === notes.value.length - 1 && delta < 0)) {
    delta = delta * 0.28;
  }
  videoDragY.value = delta;
};
const onVideoTouchEnd = () => {
  if (!isVideoDragging.value) return;
  isVideoDragging.value = false;
  const delta = videoDragY.value;
  const threshold = window.innerHeight * 0.22;

  if (Math.abs(delta) > threshold) {
    // 吸附到下一个/上一个视频
    const dir = delta < 0 ? 1 : -1; // 负=上滑=下一条
    const targetIdx = currentIdx.value + dir;
    if (targetIdx >= 0 && targetIdx < notes.value.length) {
      showSwipeHint.value = false; // 用户已会滑动，隐藏提示
      videoDragY.value = dir > 0 ? -window.innerHeight : window.innerHeight;
      videoSnapping.value = true;
      setTimeout(() => {
        slideDirection.value = dir > 0 ? 'up' : 'down';
        currentIdx.value = targetIdx;
        comments.value = [];
        updateState();
        videoDragY.value = 0;
        videoSnapping.value = false;
        nextTick(() => {
          if (videoRef.value) { videoRef.value.load(); videoRef.value.play(); isPlaying.value = true; }
        });
        loadComments();
      }, 220);
      return;
    }
  }
  // 回弹
  videoSnapping.value = true;
  videoDragY.value = 0;
  setTimeout(() => { videoSnapping.value = false; }, 280);
};

const handleLike = async () => {
  if (!getToken()) { showToast('请先登录'); return; }
  const note = notes.value[currentIdx.value];
  if (!note) return;
  const prevLiked = note.isLiked;
  const prevLikes = note.likes || 0;
  // 乐观更新：本地 ref + 源数据同步
  note.isLiked = !note.isLiked;
  note.likes = note.isLiked ? prevLikes + 1 : Math.max(0, prevLikes - 1);
  isLiked.value = note.isLiked;
  likeCount.value = note.likes;
  try {
    const res = await noteApi.likeNote(note.id);
    if (res.code === 0) {
      note.isLiked = res.data.isLiked;
      note.likes = res.data.likes;
      isLiked.value = note.isLiked;
      likeCount.value = note.likes;
    } else throw new Error();
  } catch {
    note.isLiked = prevLiked;
    note.likes = prevLikes;
    isLiked.value = prevLiked;
    likeCount.value = prevLikes;
  }
};

const togglePlay = () => {
  if (!videoRef.value) return;
  isPlaying.value ? videoRef.value.pause() : videoRef.value.play();
  isPlaying.value = !isPlaying.value;
};

// 点击视频区：抽屉打开时关闭抽屉，否则暂停/播放
const onVideoClick = () => {
  if (drawerVisible.value) {
    closeDrawer();
  } else {
    togglePlay();
  }
};

const handleSendComment = async () => {
  const text = commentInput.value.trim();
  if (!text || !getToken()) return;
  try {
    const res = await commentApi.addComment(current.value.id, text);
    if (res.code === 0) {
      comments.value.unshift({ ...res.data, replyCount: 0 });
      commentCount.value++;
      // 同步到源数据
      const note = notes.value[currentIdx.value];
      if (note) note.comments = (note.comments || 0) + 1;
      commentInput.value = '';
      showToast('评论成功');
    }
  } catch { showToast('评论失败'); }
};

const handleSendReply = async (parentId) => {
  const text = (replyInputs[parentId] || '').trim();
  if (!text || !getToken()) return;
  try {
    const res = await commentApi.addComment(current.value.id, text, null, null, parentId);
    if (res.code === 0) {
      const reply = res.data;
      // 更新评论的回复计数
      const parent = comments.value.find(c => c.id === parentId);
      if (parent) {
        parent.replyCount = (parent.replyCount || 0) + 1;
        // 如果没有热评回复，设为第一条回复
        if (!parent.topReply) parent.topReply = reply;
      }
      // 如果已展开，直接追加到列表中
      if (expandedReplies[parentId] && replyList[parentId]) {
        replyList[parentId].push(reply);
      }
      // 回复也计入总评论数
      commentCount.value++;
      const note = notes.value[currentIdx.value];
      if (note) note.comments = (note.comments || 0) + 1;
      replyInputs[parentId] = '';
      replyTarget.value = null;
      showToast('回复成功');
    }
  } catch { showToast('回复失败'); }
};

const toggleReplies = async (commentId) => {
  if (expandedReplies[commentId]) {
    // 收起
    expandedReplies[commentId] = false;
  } else {
    // 展开：懒加载回复列表
    try {
      const res = await commentApi.getReplies(commentId);
      if (res.code === 0) {
        replyList[commentId] = res.data || [];
        expandedReplies[commentId] = true;
      }
    } catch { showToast('加载回复失败'); }
  }
};

const startReply = (comment) => {
  replyTarget.value = { id: comment.id, authorName: comment.authorName || '用户' };
  nextTick(() => {
    const input = document.querySelector(`.reply-input-${comment.id}`);
    if (input) input.focus();
  });
};

const cancelReply = () => {
  replyTarget.value = null;
};

const handleDeleteComment = async (c) => {
  try {
    const res = await commentApi.deleteComment(c.id);
    if (res.code !== 0) { showToast(res.message || '删除失败'); return; }
    // 判断是顶级评论还是回复
    const isTopLevel = !c.parentId;
    if (isTopLevel) {
      comments.value = comments.value.filter(x => x.id !== c.id);
      commentCount.value = Math.max(0, commentCount.value - 1);
      // 清理关联的回复缓存
      delete expandedReplies[c.id];
      delete replyList[c.id];
    } else {
      // 从回复列表中移除
      const parentId = c.parentId;
      if (replyList[parentId]) {
        replyList[parentId] = replyList[parentId].filter(r => r.id !== c.id);
      }
      // 更新父评论的回复计数
      const parent = comments.value.find(x => x.id === parentId);
      if (parent) {
        parent.replyCount = Math.max(0, (parent.replyCount || 1) - 1);
        const remaining = replyList[parentId] || [];
        parent.topReply = remaining.length > 0 ? remaining[0] : null;
      }
      // 回复也计入总评论数，删除时同步减少
      commentCount.value = Math.max(0, commentCount.value - 1);
      const note = notes.value[currentIdx.value];
      if (note) note.comments = Math.max(0, (note.comments || 1) - 1);
    }
  } catch { showToast('删除失败'); }
};

const handleLikeComment = async (c) => {
  if (!getToken()) { showToast('请先登录'); return; }
  try {
    const res = await commentApi.likeComment(c.id);
    if (res.code === 0) {
      c.likes = res.data.likes;
      // 如果是回复，同步更新父评论的 topReply 预览
      if (c.parentId) {
        const parent = comments.value.find(x => x.id === c.parentId);
        if (parent && parent.topReply && parent.topReply.id === c.id) {
          parent.topReply.likes = res.data.likes;
        }
      }
    }
  } catch { /* ignore */ }
};
const toggleHeart = () => {
  isHearted.value = !isHearted.value;
  heartCount.value += isHearted.value ? 1 : -1;
  if (isHearted.value) {
    heartBurst.value = true;
    setTimeout(() => { heartBurst.value = false; }, 800);
  }
};

const handleShare = () => {
  navigator.clipboard?.writeText(window.location.href).then(() => showToast('链接已复制')).catch(() => {});
};
const stripHtml = (html) => {
  if (!html) return '';
  return html.replace(/<img[^>]*>/gi,'[图片]').replace(/<video[^>]*\/?>/gi,'').replace(/<[^>]+>/g,'').trim();
};

onMounted(() => { loadVideos().then(() => loadComments()); });
onUnmounted(() => { if (videoRef.value) videoRef.value.pause(); });
</script>

<template>
  <div class="video-shell" :class="{ dragging }" v-if="!isLoading">
      <!-- 顶部栏 -->
      <div class="top-bar"><van-icon name="arrow-left" size="24" color="#fff" @click.stop="goBack"/><span class="top-title">{{ current.title || '视频' }}</span><van-icon name="search" size="22" color="#fff" @click.stop/></div>

      <!-- ══════ 视频区 ══════ -->
      <div class="video-zone" :style="videoStyle" @click="onVideoClick" @touchstart="onVideoTouchStart" @touchmove="onVideoTouchMove" @touchend="onVideoTouchEnd">
        <div class="video-track" :class="{ snapping: videoSnapping, dragging: isVideoDragging }" :style="{ transform: `translateY(${videoDragY}px)` }">
          <Transition :name="'video-slide-' + slideDirection" mode="out-in">
            <video
              v-if="videoUrl" :key="currentIdx" ref="videoRef" :src="videoUrl" class="full-video" loop playsinline
              webkit-playsinline autoplay
              @loadedmetadata="() => { if(videoRef) videoRef.play(); isPlaying=true }"
              @pause="isPlaying=false" @play="isPlaying=true"
            ></video>
            <div v-else :key="'empty-'+currentIdx" class="no-video"><van-icon name="video-o" size="60" color="rgba(255,255,255,0.3)"/><p style="margin-top:12px;color:rgba(255,255,255,0.4);font-size:14px">暂无视频</p></div>
          </Transition>
        </div>
        <div class="play-indicator" :class="{ hide: isPlaying }"><van-icon name="play-circle-o" size="64" color="rgba(255,255,255,0.7)"/></div>
        <div v-if="heartBurst" class="heart-burst">❤️</div>
        <div class="swipe-hint" v-if="showSwipeHint && currentIdx<notes.length-1 && !drawerVisible"><van-icon name="arrow-up" size="16" color="rgba(255,255,255,0.5)"/><span>上滑下一个</span></div>
      </div>

      <!-- 右侧操作（独立于视频区，被抽屉遮挡） -->
      <div class="side-layer" :class="{ under: drawerVisible, snapping: videoSnapping, dragging: isVideoDragging }" :style="{ transform: `translateY(${videoDragY}px)` }">
        <div class="swipe-dots" v-if="notes.length > 1"><span v-for="(_,i) in notes" :key="i" class="dot" :class="{ on: i===currentIdx }"/></div>
        <div class="side-actions">
          <div class="side-avatar-ring"><van-image round width="44" height="44" :src="current.authorAvatar||''" fit="cover"/></div>
          <div class="side-btn" @click.stop="toggleHeart">
            <van-icon name="like" size="36" :color="isHearted?'#ff2d55':'#fff'"/><span class="side-num">{{ heartCount || '' }}</span>
          </div>
          <div class="side-btn" @click.stop="handleLike">
            <van-icon name="good-job" size="32" :color="isLiked?'#ff2d55':'#fff'"/><span class="side-num">{{ likeCount }}</span>
          </div>
          <div class="side-btn" @click.stop="drawerVisible ? closeDrawer() : openDrawer()">
            <van-icon name="chat" size="32" color="#fff"/><span class="side-num">{{ commentCount }}</span>
          </div>
          <div class="side-btn" @click.stop="handleShare">
            <van-icon name="share" size="30" color="#fff"/><span class="side-num">分享</span>
          </div>
        </div>
        <div class="bottom-info">
          <div class="bottom-author"><span class="b-name">@{{ current.authorName||'旅行者' }}</span><span class="follow-chip">+ 关注</span></div>
          <div class="b-desc">{{ stripHtml(current.content) }}</div>
          <div class="location-chip" v-if="current.authorCity||current.city"><van-icon name="location-o" size="11"/><span>{{ current.authorCity||current.city }}</span></div>
        </div>
      </div>

      <!-- ══════ 评论抽屉 ══════ -->
      <Transition name="drawer-slide">
        <div class="comment-drawer" v-if="drawerVisible"
          @touchstart.stop="onDragStart" @touchmove.stop="onDragMove" @touchend.stop="onDragEnd"
        >
        <!-- 手柄 -->
        <div class="handle-row"><div class="handle-bar"/></div>
        <!-- 标题 -->
        <div class="dr-header">
          <span class="dr-title">{{ commentCount }} 条评论</span>
          <van-icon name="cross" size="18" color="#999" @click.stop="closeDrawer"/>
        </div>
        <!-- 列表 -->
        <div class="dr-list">
          <div v-if="comments.length===0" class="no-cmt">暂无评论，来说两句吧</div>
          <div v-for="c in comments" :key="c.id" class="cmt-row">
            <van-image round width="32" height="32" fit="cover" class="cmt-av" :src="c.authorAvatar||''"/>
            <div class="cmt-main">
              <div class="cmt-head">
                <span class="cmt-author-name">{{ c.authorName || ('用户'+c.userId) }}</span>
                <span class="cmt-tm">{{ c.date }}</span>
              </div>
              <div class="cmt-txt">{{ c.content }}</div>
              <img v-if="c.image" :src="c.image" class="cmt-img"/>
              <video v-if="c.video" :src="c.video" controls class="cmt-vid"/>

              <!-- ══════ 底部操作行：点赞 + 回复 ══════ -->
              <div class="cmt-actions-row">
                <span class="cmt-action" @click.stop="handleLikeComment(c)">
                  <van-icon name="good-job-o" size="14" /> {{ c.likes || '' }}
                </span>
                <span class="cmt-action" @click.stop="startReply(c)">回复</span>
              </div>

              <!-- ══════ 回复区域（抖音风格） ══════ -->
              <div v-if="c.replyCount > 0" class="reply-zone">
                <!-- 展开/收起按钮 -->
                <div class="reply-toggle" @click.stop="toggleReplies(c.id)">
                  <span class="reply-toggle-line"></span>
                  <span v-if="!expandedReplies[c.id]">
                    展开{{ c.replyCount }}条回复
                    <van-icon name="arrow-down" size="10" />
                  </span>
                  <span v-else>收起回复 <van-icon name="arrow-up" size="10" /></span>
                </div>

                <!-- 热评回复预览（未展开时只显示点赞最多的那条） -->
                <div v-if="!expandedReplies[c.id] && c.topReply" class="reply-preview">
                  <span class="reply-preview-author">{{ c.topReply.authorName || ('用户'+c.topReply.userId) }}</span>
                  <span v-if="c.topReply.content" class="reply-preview-text">：{{ c.topReply.content }}</span>
                  <img v-if="c.topReply.image" :src="c.topReply.image" class="cmt-img" style="max-width:80px"/>
                </div>

                <!-- 展开后的回复列表 -->
                <div v-if="expandedReplies[c.id]" class="reply-list">
                  <div v-for="r in (replyList[c.id]||[])" :key="r.id" class="reply-item">
                    <van-image round width="24" height="24" fit="cover" :src="r.authorAvatar||''" class="reply-av"/>
                    <div class="reply-main">
                      <div class="reply-head">
                        <span class="reply-author">{{ r.authorName || ('用户'+r.userId) }}</span>
                        <span class="reply-tm">{{ r.date }}</span>
                      </div>
                      <div class="reply-content">{{ r.content }}</div>
                      <img v-if="r.image" :src="r.image" class="cmt-img" style="max-width:80px"/>
                      <div class="reply-actions">
                        <span class="cmt-action" @click.stop="handleLikeComment(r)">
                          <van-icon name="good-job-o" size="12" /> {{ r.likes || '' }}
                        </span>
                        <span class="cmt-action" @click.stop="startReply(r)">回复</span>
                        <van-icon v-if="getToken()" name="delete-o" size="12" color="#ccc" @click.stop="handleDeleteComment(r)"/>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 内联回复输入框 -->
                <div v-if="replyTarget?.id === c.id" class="reply-input-inline">
                  <span class="reply-input-hint">回复 {{ replyTarget.authorName }}：</span>
                  <input
                    :class="'reply-input-'+c.id"
                    v-model="replyInputs[c.id]"
                    :placeholder="'回复 '+replyTarget.authorName"
                    class="reply-field"
                    @keyup.enter="handleSendReply(c.id)"
                  />
                  <span class="reply-send" @click.stop="handleSendReply(c.id)">发送</span>
                  <van-icon name="cross" size="14" color="#999" @click.stop="cancelReply"/>
                </div>
              </div>

              <!-- 无回复时的回复入口 -->
              <div v-else class="reply-zone">
                <div v-if="replyTarget?.id === c.id" class="reply-input-inline">
                  <span class="reply-input-hint">回复 {{ replyTarget.authorName }}：</span>
                  <input
                    :class="'reply-input-'+c.id"
                    v-model="replyInputs[c.id]"
                    :placeholder="'回复 '+replyTarget.authorName"
                    class="reply-field"
                    @keyup.enter="handleSendReply(c.id)"
                  />
                  <span class="reply-send" @click.stop="handleSendReply(c.id)">发送</span>
                  <van-icon name="cross" size="14" color="#999" @click.stop="cancelReply"/>
                </div>
              </div>
            </div>
            <!-- 删除按钮 -->
            <van-icon v-if="getToken()" name="delete-o" size="14" color="#ccc" class="cmt-del" @click.stop="handleDeleteComment(c)"/>
          </div>
        </div>
        <!-- 底部全局输入栏 -->
        <div class="dr-input-row" v-if="!replyTarget">
          <van-field v-model="commentInput" placeholder="发一条友善的评论" :border="false" class="dr-input"/>
          <div class="dr-send" @click.stop="handleSendComment"><van-icon name="guide-o" size="18" color="#fff"/></div>
        </div>
        <div class="dr-input-row replying" v-else>
          <span class="replying-label">回复 @{{ replyTarget.authorName }}</span>
          <van-field v-model="replyInputs[replyTarget.id]" placeholder="写下你的回复..." :border="false" class="dr-input"/>
          <div class="dr-send" @click.stop="handleSendReply(replyTarget.id)"><van-icon name="guide-o" size="18" color="#fff"/></div>
          <van-icon name="cross" size="18" color="#999" @click.stop="cancelReply" style="margin-left:8px;cursor:pointer"/>
        </div>
      </div>
      </Transition>
  </div>
  <div v-else class="video-loading"><van-loading size="40" color="#fff"/></div>
</template>

<style scoped>
/* ====== 外壳 ====== */
.video-shell {
  width: 100%; height: 100dvh;
  background: #000; position: fixed; top:0;left:0; z-index: 10000;
  -webkit-user-select: none; user-select: none;
  display: flex; flex-direction: column;
  overflow: hidden;
  padding-bottom: env(safe-area-inset-bottom, 0px);
  box-sizing: border-box;
}

/* ====== 视频区 ====== */
.video-zone {
  width: 100%; position: relative;
  background: #000; overflow: hidden;
  flex: 1 1 100%;
  min-height: 0;
  margin: 0; padding: 0;
  touch-action: none; /* 禁止浏览器手势，跟手零延迟 */
  transition: flex 0.35s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.3s ease;
}
/* 拖拽中禁用过渡，跟手 */
.video-shell.dragging .video-zone {
  transition: none !important;
}
.full-video { width: 100%; height: 100%; object-fit: contain; background: #000; }
.no-video { text-align: center; position: absolute; top:50%; left:50%; transform: translate(-50%,-50%); }

/* ====== 视频滑动轨道 ====== */
.video-track {
  width: 100%; height: 100%;
  position: relative;
  will-change: transform;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  transform: translateZ(0); /* 强制GPU合成层 */
}
/* 拖拽中：完全跟手，无过渡 */
.video-track.dragging {
  transition: none !important;
}
/* 松手回弹/吸附：线性过渡 */
.video-track.snapping {
  transition: transform 0.25s linear;
}

.play-indicator { position: absolute; top:50%;left:50%; transform: translate(-50%,-50%); pointer-events: none; transition: opacity .2s; }
.play-indicator.hide { opacity: 0; }

.heart-burst { position: absolute; top:45%;left:50%; transform: translate(-50%,-50%); font-size: 80px; pointer-events: none; animation: hb .8s ease-out forwards; }
@keyframes hb { 0%{opacity:1;transform:translate(-50%,-50%) scale(.3)} 50%{opacity:1;transform:translate(-50%,-50%) scale(1.3)} 100%{opacity:0;transform:translate(-50%,-50%) scale(1.8)} }

/* 顶部栏（固定高度，在视频上方） */
.top-bar {
  flex: 0 0 auto;
  display:flex; align-items:center; justify-content:space-between;
  padding: max(10px,env(safe-area-inset-top)) 16px 8px;
  background: #000;
  z-index: 15;
}
.top-title { color:#fff; font-size:15px; font-weight:600; }

/* 右侧图层（在视频区内，随视频缩放大小时移动） */
.side-layer {
  position: absolute; inset: 0; pointer-events: none; z-index: 3;
  transition: opacity 0.25s ease;
  will-change: transform;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  transform: translateZ(0);
}
.side-layer > * { pointer-events: auto; }
.side-layer.under {
  opacity: 0.35;
}
.side-layer.under > * {
  pointer-events: none;
}
/* 拖拽中：跟手，无过渡 */
.side-layer.dragging {
  transition: none !important;
}
/* 松手回弹：与视频轨道同步 */
.side-layer.snapping {
  transition: transform 0.25s linear;
}

/* 进度点 */
.swipe-dots { position: absolute; right:8px; top:50%; transform:translateY(-50%); display:flex; flex-direction:column; gap:8px; }
.dot { width:3px; height:16px; border-radius:2px; background:rgba(255,255,255,.3); transition:.3s; }
.dot.on { background:#fff; height:28px; }

/* 右侧操作 */
.side-actions { position: absolute; right:12px; bottom:120px; display:flex; flex-direction:column; align-items:center; gap:22px; }
.side-btn { display:flex; flex-direction:column; align-items:center; gap:3px; cursor:pointer; }
.side-num { color:#fff; font-size:11px; font-weight:500; text-shadow:0 1px 2px rgba(0,0,0,.5); }

/* 底部信息 */
.bottom-info { position:absolute; bottom:16px;left:0;right:120px; padding:16px 16px max(24px,env(safe-area-inset-bottom)); background:linear-gradient(to top,rgba(0,0,0,.5),transparent); }
.bottom-author { display:flex; align-items:center; gap:10px; margin-bottom:8px; }
.b-name { color:#fff; font-weight:600; font-size:15px; }
.follow-chip { color:#ff6b81; font-size:11px; border:1px solid #ff6b81; border-radius:10px; padding:2px 10px; cursor:pointer; }
.b-desc { color:rgba(255,255,255,.88); font-size:13px; line-height:1.6; margin-bottom:10px; display:-webkit-box; -webkit-line-clamp:3; -webkit-box-orient:vertical; overflow:hidden; }
.location-chip { display:inline-flex; align-items:center; gap:4px; padding:4px 12px; border-radius:20px; background:rgba(255,255,255,.12); backdrop-filter:blur(8px); border:1px solid rgba(255,255,255,.15); color:rgba(255,255,255,.8); font-size:11px; }
.swipe-hint { position:absolute; bottom:100px;left:50%;transform:translateX(-50%); display:flex;flex-direction:column;align-items:center;gap:2px; color:rgba(255,255,255,.5);font-size:11px;z-index:5; animation:float 2s ease-in-out infinite; }
@keyframes float { 0%,100%{opacity:.6;transform:translateX(-50%) translateY(0)} 50%{opacity:1;transform:translateX(-50%) translateY(-6px)} }

/* ====== 评论抽屉 ====== */
/* ====== 评论抽屉 ====== */
.comment-drawer {
  width: 100%;
  flex: 1 1 0%;
  min-height: 0;
  background: #fff;
  display: flex; flex-direction: column;
  overflow: hidden;
  position: relative;
  z-index: 20;
  border-radius: 12px 12px 0 0;
  margin-top: -1px;
}
.drawer-slide-enter-active { transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
.drawer-slide-leave-active { transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1); }
.drawer-slide-enter-from,
.drawer-slide-leave-to { opacity: 0; transform: translateY(40%); }
.handle-row { display:flex; justify-content:center; padding:12px 0 4px; cursor:grab; }
.handle-row:active { cursor:grabbing; }
.handle-bar { width:36px; height:4px; border-radius:2px; background:#ddd; }
.dr-header { display:flex; justify-content:space-between; align-items:center; padding:8px 16px 12px; }
.dr-title { color:#111; font-size:16px; font-weight:600; }
.dr-list { flex:1; overflow-y:auto; padding:0 16px; -webkit-overflow-scrolling:touch; }
.no-cmt { text-align:center; color:#bbb; padding:40px; font-size:13px; }
.cmt-row { display:flex; gap:10px; padding:12px 0; border-bottom:1px solid #f0f0f0; align-items:flex-start; }
.cmt-av { flex-shrink:0; background:#eee; }
.cmt-main { flex:1; min-width:0; }
.cmt-head { display:flex; justify-content:space-between; color:#999; font-size:11px; margin-bottom:4px; }
.cmt-tm { color:#ccc; }
.cmt-txt { color:#333; font-size:13px; line-height:1.6; word-break:break-word; }
.cmt-img { max-width:120px; border-radius:8px; margin-top:6px; }
.cmt-vid { width:100%; max-height:150px; border-radius:8px; margin-top:6px; background:#000; }
.cmt-del { flex-shrink:0; margin-top:2px; cursor:pointer; }
.dr-input-row { display:flex; align-items:center; gap:8px; padding:8px 12px; margin:8px 12px; background:#f5f5f5; border-radius:22px; border:1px solid #eee; }
.dr-send { display:flex; align-items:center; justify-content:center; width:34px;height:34px; border-radius:50%; background:linear-gradient(135deg,#fe2c55,#ff4d6a); flex-shrink:0; cursor:pointer; }
.dr-send:active { transform:scale(.9); }
.dr-input { flex:1; background:transparent!important; color:#333; padding:4px 0!important; }
.dr-input :deep(.van-field__control) { color:#333; font-size:13px; }
.dr-input :deep(.van-field__control::placeholder) { color:#bbb; }

.video-loading { width:100%;height:100dvh;background:#000;display:flex;align-items:center;justify-content:center; }

/* ====== 评论回复区（抖音风格） ====== */
.cmt-author-name { color:#333; font-weight:500; }
.cmt-actions-row { display:flex; gap:16px; margin-top:6px; }
.cmt-action { font-size:12px; color:#999; cursor:pointer; display:inline-flex; align-items:center; gap:3px; }
.cmt-action:active { color:#fe2c55; }

/* 回复区域 */
.reply-zone {
  margin-top:6px;
  padding:6px 0 0;
}
.reply-toggle {
  display:inline-flex; align-items:center; gap:4px;
  font-size:12px; color:#5677a9; cursor:pointer;
  padding:4px 0;
}
.reply-toggle-line {
  display:inline-block; width:20px; height:0.5px; background:#d0d7e2;
  margin-right:4px;
  flex-shrink:0;
}
.reply-preview {
  font-size:12px; color:#555; line-height:1.5;
  padding:4px 0 2px; margin-left:4px;
}
.reply-preview-author { color:#5677a9; font-weight:500; }
.reply-preview-text { color:#555; }

.reply-list {
  margin-top:2px;
}
.reply-item {
  display:flex; gap:8px; padding:6px 0;
  border-bottom:0.5px solid #f5f5f5;
}
.reply-av { flex-shrink:0; margin-top:2px; }
.reply-main { flex:1; min-width:0; }
.reply-head { display:flex; justify-content:space-between; margin-bottom:2px; }
.reply-author { font-size:12px; color:#5677a9; font-weight:500; }
.reply-tm { font-size:10px; color:#ccc; }
.reply-content { font-size:13px; color:#333; line-height:1.5; word-break:break-word; }
.reply-actions { display:flex; gap:12px; align-items:center; margin-top:4px; }

/* 内联回复输入框 */
.reply-input-inline {
  display:flex; align-items:center; gap:6px;
  margin-top:6px; padding:6px 8px;
  background:#f8f8f8; border-radius:16px;
}
.reply-input-hint { font-size:11px; color:#999; white-space:nowrap; flex-shrink:0; }
.reply-field {
  flex:1; border:none; outline:none; background:transparent;
  font-size:12px; color:#333; min-width:0;
}
.reply-field::placeholder { color:#ccc; }
.reply-send {
  font-size:12px; color:#fe2c55; font-weight:500;
  cursor:pointer; white-space:nowrap; flex-shrink:0;
}
.reply-send:active { opacity:.7; }

/* 全局回复中状态栏 */
.dr-input-row.replying {
  background:#fff8f8; border-color:#ffd5d5;
}
.replying-label {
  font-size:12px; color:#fe2c55; white-space:nowrap; flex-shrink:0;
}
</style>
