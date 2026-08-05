<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { showToast } from 'vant';
import { getToken } from '../utils/auth';
import { noteApi, commentApi } from '../api';

const router = useRouter();
const route = useRoute();
const { t } = useI18n();

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
const replyShowCount = reactive({});    // { commentId: number } 每次展开5条
const replyInputs = reactive({});       // { commentId: 'text' } 回复输入框文字
const replyTarget = ref(null);          // { id, authorName, rootId } 当前正在回复的评论
const isPlaying = ref(true);
const videoRef = ref(null);
const videoCurrentTime = ref(0);
const videoDuration = ref(0);
const videoProgress = ref(0);       // 0~100
const isFullscreen = ref(false);
const showControls = ref(true);
let hideControlsTimer = null;

const onVideoTimeUpdate = () => {
  if (!videoRef.value) return
  videoCurrentTime.value = videoRef.value.currentTime
  videoDuration.value = videoRef.value.duration || 0
  videoProgress.value = videoDuration.value ? (videoCurrentTime.value / videoDuration.value) * 100 : 0
};

const onVideoLoadedMetadata = () => {
  if (videoRef.value) {
    videoDuration.value = videoRef.value.duration || 0
    videoRef.value.play()
    isPlaying.value = true
  }
};

const seekVideo = (e) => {
  const bar = e.currentTarget
  const rect = bar.getBoundingClientRect()
  const pct = (e.clientX - rect.left) / rect.width
  if (videoRef.value && videoDuration.value) {
    videoRef.value.currentTime = pct * videoDuration.value
  }
};

// 全屏按钮的 fixed 坐标
const fullscreenBtnStyle = computed(() => {
  if (isFullscreen.value) return { bottom: 'auto', top: '12px', right: '12px' }
  return { bottom: '86px', right: '12px' }
})

const toggleFullscreen = async () => {
  if (isFullscreen.value) {
    // 退出全屏
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    }
    isFullscreen.value = false
  } else {
    // 进入全屏
    const el = videoRef.value?.parentElement || videoRef.value
    if (el?.requestFullscreen) {
      await el.requestFullscreen()
    } else if (el?.webkitRequestFullscreen) {
      await el.webkitRequestFullscreen()
    }
    isFullscreen.value = true
    if (videoRef.value) {
      videoRef.value.style.objectFit = 'contain'
      videoRef.value.play()
    }
  }
};

// 监听原生全屏变化
const onFullscreenChange = () => {
  if (!document.fullscreenElement && isFullscreen.value) {
    isFullscreen.value = false
  }
}
document.addEventListener('fullscreenchange', onFullscreenChange)
document.addEventListener('webkitfullscreenchange', onFullscreenChange)
onUnmounted(() => {
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
})

const formatTime = (s) => {
  if (!s || !isFinite(s)) return '0:00'
  const m = Math.floor(s / 60)
  const sec = Math.floor(s % 60)
  return m + ':' + String(sec).padStart(2, '0')
};

const resetHideControls = () => {
  showControls.value = true
  clearTimeout(hideControlsTimer)
  hideControlsTimer = setTimeout(() => { showControls.value = false }, 3000)
};

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
    const res = await noteApi.getAllNotes(1, 50);
    if (res?.code === 0 && res.data) {
      // 兼容旧数组结构 与 新分页结构 { list, total, hasMore }
      const all = Array.isArray(res.data) ? res.data : (res.data.list || []);
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
  try {
    const res = await commentApi.getComments(id);
    if (res.code === 0) {
      const list = Array.isArray(res.data) ? res.data : [];
      comments.value = list;
      for (const c of list) {
        if (!c.parentId) {
          try {
            const r = await commentApi.getReplies(c.id);
            if (r.code === 0 && r.data?.length) {
              c.replyCount = r.data.length;
              c.topReply = r.data.sort((a, b) => (b.likes || 0) - (a.likes || 0))[0];
            }
          } catch {}
        }
      }
    }
  } catch {}
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
  if (!getToken()) { showToast(t('common.notLoggedIn')); return; }
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
      showToast(t('community.commentSuccess'));
    }
  } catch { showToast(t('community.commentFailed')); }
};

const handleSendReply = async () => {
  if (!replyTarget.value) return
  const { id, rootId, authorName } = replyTarget.value
  const text = (replyInputs[rootId] || '').trim()
  if (!text || !getToken()) return
  try {
    // 回复到顶层父评论下，内容前加 @mention
    const content = id !== rootId ? `@${authorName} ${text}` : text
    const res = await commentApi.addComment(current.value.id, content, null, null, rootId)
    if (res.code === 0) {
      const reply = res.data
      const parent = comments.value.find(c => c.id === rootId)
      if (parent) {
        parent.replyCount = (parent.replyCount || 0) + 1
        if (!parent.topReply) parent.topReply = reply
      }
      if (expandedReplies[rootId] && replyList[rootId]) {
        replyList[rootId].push(reply)
      }
      commentCount.value++
      const note = notes.value[currentIdx.value]
      if (note) note.comments = (note.comments || 0) + 1
      replyInputs[rootId] = ''
      replyTarget.value = null
      showToast(t('community.replySuccess'))
    }
  } catch { showToast(t('community.replyFailed')); }
};

const toggleReplies = async (commentId) => {
  if (expandedReplies[commentId]) {
    // 收起
    expandedReplies[commentId] = false;
    delete replyShowCount[commentId];
  } else {
    // 展开：懒加载回复列表
    try {
      const res = await commentApi.getReplies(commentId);
      if (res.code === 0) {
        replyList[commentId] = res.data || [];
        replyShowCount[commentId] = 5;
        expandedReplies[commentId] = true;
      }
    } catch { showToast(t('community.loadRepliesFailed')); }
  }
};

const startReply = (comment) => {
  // 找到顶层父评论ID（回复回复时，归属到顶层评论区）
  const rootId = comment.parentId || comment.id
  replyTarget.value = {
    id: comment.id,
    rootId,
    authorName: comment.authorName || (t('community.user') + (comment.userId || ''))
  }
  // 确保展开该顶层评论的回复区
  if (!expandedReplies[rootId] && comment.parentId) {
    toggleReplies(rootId)
  }
  nextTick(() => {
    const input = document.querySelector(`.reply-input-${rootId}`);
    if (input) input.focus();
  });
};

const cancelReply = () => {
  replyTarget.value = null;
};

const handleDeleteComment = async (c) => {
  try {
    const res = await commentApi.deleteComment(c.id);
    if (res.code !== 0) { showToast(res.message || t('community.deleteFailed')); return; }
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
  } catch { showToast(t('community.deleteFailed')); }
};

const handleLikeComment = async (c) => {
  if (!getToken()) { showToast(t('common.notLoggedIn')); return; }
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
  navigator.clipboard?.writeText(window.location.href).then(() => showToast(t('community.linkCopied'))).catch(() => {});
};
const stripHtml = (html) => {
  if (!html) return '';
  return html.replace(/<img[^>]*>/gi,'[图片]').replace(/<video[^>]*\/?>/gi,'').replace(/<[^>]+>/g,'').trim();
};

onMounted(() => { loadVideos().then(() => loadComments()); });
onUnmounted(() => { if (videoRef.value) videoRef.value.pause(); });
</script>

<template>
  <div class="video-shell" :class="{ dragging, fullscreen: isFullscreen }" v-if="!isLoading">
      <!-- 顶部栏 -->
      <div class="top-bar"><van-icon name="arrow-left" size="24" color="#fff" @click.stop="goBack"/><span class="top-title">{{ current.title || t('community.video') }}</span><van-icon name="search" size="22" color="#fff" @click.stop/></div>

      <!-- ══════ 视频区 ══════ -->
      <div class="video-zone" :style="videoStyle" @click="onVideoClick" @touchstart="onVideoTouchStart" @touchmove="onVideoTouchMove" @touchend="onVideoTouchEnd">
        <div class="video-track" :class="{ snapping: videoSnapping, dragging: isVideoDragging }" :style="{ transform: `translateY(${videoDragY}px)` }">
          <Transition :name="'video-slide-' + slideDirection" mode="out-in">
            <video
              v-if="videoUrl" :key="currentIdx" ref="videoRef" :src="videoUrl" class="full-video" loop playsinline
              webkit-playsinline autoplay
              @loadedmetadata="onVideoLoadedMetadata"
              @timeupdate="onVideoTimeUpdate"
              @pause="isPlaying=false" @play="isPlaying=true"
            ></video>
            <div v-else :key="'empty-'+currentIdx" class="no-video"><van-icon name="video-o" size="60" color="rgba(255,255,255,0.3)"/><p style="margin-top:12px;color:rgba(255,255,255,0.4);font-size:14px">{{ t('community.noVideo') }}</p></div>
          </Transition>
        </div>
        <!-- 播放/暂停指示 -->
        <div class="play-indicator" :class="{ hide: isPlaying }"><van-icon name="play-circle-o" size="64" color="rgba(255,255,255,0.7)"/></div>
        <div v-if="heartBurst" class="heart-burst">❤️</div>
        <div class="swipe-hint" v-if="showSwipeHint && currentIdx<notes.length-1 && !drawerVisible"><van-icon name="arrow-up" size="16" color="rgba(255,255,255,0.5)"/><span>{{ t('community.swipeNext') }}</span></div>
        <!-- 全屏按钮（视频右下角，进度条上方） -->
        <button type="button" class="fullscreen-btn" :class="{ fullscreen: isFullscreen, hide: !showControls }" :style="fullscreenBtnStyle" @click.stop="toggleFullscreen">
          <van-icon :name="isFullscreen ? 'shrink' : 'expand-o'" size="16" color="#fff" />
        </button>
        <!-- 抖音风底部进度条 -->
        <div class="video-controls" :class="{ hide: !showControls }">
          <div class="video-progress-track" @click.stop="seekVideo">
            <div class="video-progress-fill" :style="{ width: videoProgress + '%' }"></div>
          </div>
          <div class="video-bottom-row">
            <span class="video-time-tt">{{ formatTime(videoCurrentTime) }} / {{ formatTime(videoDuration) }}</span>
          </div>
        </div>
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
            <van-icon name="share" size="30" color="#fff"/><span class="side-num">{{ t('community.share') }}</span>
          </div>
        </div>
        <div class="bottom-info">
          <div class="bottom-author"><span class="b-name">@{{ current.authorName || t('community.traveler') }}</span><span class="follow-chip">{{ t('community.follow') }}</span></div>
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
          <span class="dr-title">{{ t('community.commentCount', { n: commentCount }) }}</span>
          <van-icon name="cross" size="18" color="#999" @click.stop="closeDrawer"/>
        </div>
        <!-- 列表 -->
        <div class="dr-list">
          <div v-if="comments.length===0" class="no-cmt">{{ t('community.noComments') }}</div>
          <div v-for="c in comments" :key="c.id" class="cmt-row">
            <van-image round width="32" height="32" fit="cover" class="cmt-av" :src="c.authorAvatar||''"/>
            <div class="cmt-main">
              <div class="cmt-head">
                <span class="cmt-author-name">{{ c.authorName || (t('community.user') + c.userId) }}</span>
                <span class="cmt-tm">{{ c.date }}</span>
              </div>
              <div class="cmt-txt">{{ c.content }}</div>
              <img v-if="c.image" :src="c.image" class="cmt-img" loading="lazy" />
              <video v-if="c.video" :src="c.video" controls class="cmt-vid"/>

              <!-- ══════ 底部操作行：点赞 + 回复 ══════ -->
              <div class="cmt-actions-row">
                <span class="cmt-action" @click.stop="handleLikeComment(c)">
                  <van-icon name="good-job-o" size="14" /> {{ c.likes || '' }}
                </span>
                <span class="cmt-action" @click.stop="startReply(c)">{{ t('community.reply') }}</span>
              </div>

              <!-- ══════ 回复区域 ══════ -->
              <div v-if="c.replyCount > 0" class="reply-zone">
                <!-- 未展开：预览 → 展开按钮在下方 -->
                <template v-if="!expandedReplies[c.id]">
                  <div v-if="c.topReply" class="reply-preview">
                    <span class="reply-preview-author">{{ c.topReply.authorName || (t('community.user') + c.topReply.userId) }}</span>
                    <span v-if="c.topReply.content" class="reply-preview-text">：{{ c.topReply.content }}</span>
                    <img v-if="c.topReply.image" :src="c.topReply.image" class="cmt-img" style="max-width:80px"/>
                  </div>
                  <div class="reply-toggle" @click.stop="toggleReplies(c.id)">
                    <span class="reply-toggle-line"></span>
                    <span>{{ t('community.expandReplies', { n: c.replyCount }) }} <van-icon name="arrow-down" size="10" /></span>
                  </div>
                </template>

                <!-- 展开后：回复列表 → 收起按钮在下面 -->
                <template v-else>
                  <div class="reply-list">
                    <div v-for="r in (replyList[c.id]||[]).slice(0, replyShowCount[c.id] || 5)" :key="r.id" class="reply-item">
                      <van-image round width="24" height="24" fit="cover" :src="r.authorAvatar||''" class="reply-av"/>
                      <div class="reply-main">
                        <div class="reply-head">
                          <span class="reply-author">{{ r.authorName || (t('community.user') + r.userId) }}</span>
                          <span class="reply-tm">{{ r.date }}</span>
                        </div>
                        <div class="reply-content">{{ r.content }}</div>
                        <img v-if="r.image" :src="r.image" class="cmt-img" style="max-width:80px"/>
                        <div class="reply-actions">
                          <span class="cmt-action" @click.stop="handleLikeComment(r)"><van-icon name="good-job-o" size="12" /> {{ r.likes || '' }}</span>
                          <span class="cmt-action" @click.stop="startReply(r)">{{ t('community.reply') }}</span>
                          <van-icon v-if="getToken()" name="delete-o" size="12" color="#ccc" @click.stop="handleDeleteComment(r)"/>
                        </div>
                      </div>
                    </div>
                  </div>
                  <!-- 加载更多回复 -->
                  <div v-if="(replyList[c.id]||[]).length > (replyShowCount[c.id] || 5)" class="reply-toggle" @click.stop="replyShowCount[c.id] = (replyShowCount[c.id] || 5) + 5">
                    <span>{{ t('community.loadMoreReplies') }} <van-icon name="arrow-down" size="10" /></span>
                  </div>
                  <!-- 收起回复 -->
                  <div class="reply-toggle" @click.stop="toggleReplies(c.id)">
                    <span>{{ t('community.collapseReplies') }} <van-icon name="arrow-up" size="10" /></span>
                  </div>
                </template>
              </div>

              <!-- 无回复时的回复入口 -->
              <div v-else class="reply-zone"></div>
            </div>
            <!-- 删除按钮 -->
            <van-icon v-if="getToken()" name="delete-o" size="14" color="#ccc" class="cmt-del" @click.stop="handleDeleteComment(c)"/>
          </div>
        </div>
        <!-- 底部全局输入栏 -->
        <div class="dr-input-row" v-if="!replyTarget">
          <van-field v-model="commentInput" :placeholder="t('community.friendlyComment')" :border="false" class="dr-input"/>
          <div class="dr-send" @click.stop="handleSendComment"><van-icon name="guide-o" size="18" color="#fff"/></div>
        </div>
        <div class="dr-input-row replying" v-else>
          <span class="replying-label">{{ t('community.replyTo', { name: replyTarget.authorName }) }}</span>
          <van-field v-model="replyInputs[replyTarget.rootId]" :placeholder="t('community.writeReply')" :border="false" class="dr-input"/>
          <div class="dr-send" @click.stop="handleSendReply()"><van-icon name="guide-o" size="18" color="#fff"/></div>
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
  overflow: hidden;
  box-sizing: border-box;
}

.video-shell.fullscreen { padding-bottom: 0; }
.video-shell.fullscreen .video-zone { position: fixed; top:0; left:0; width:100%; height:100%; z-index: 20000; }
.video-shell.fullscreen .side-layer,
.video-shell.fullscreen .top-bar,
.video-shell.fullscreen .comment-drawer,
.video-shell.fullscreen .swipe-hint { display:none; }
.video-shell.fullscreen .fullscreen-btn { bottom:auto; top:12px; right:12px; }
.video-shell.fullscreen .video-controls { left:12px; right:12px; bottom:12px; }
/* 原生全屏时视频铺满 */
.video-shell.fullscreen .full-video { object-fit: contain; }
.video-shell.fullscreen .video-controls {
  position: fixed; bottom: 16px; left: 20px; right: 20px; z-index: 20001;
  opacity: 1;
  transition: opacity 0.3s;
}
.video-shell.fullscreen .fullscreen-btn {
  position: fixed; top: 20px; right: 20px; z-index: 20001;
}

/* ====== 视频区 - 全屏沉浸 ====== */
.video-zone {
  position: fixed; top:0; left:0; width:100%; height:100%;
  background: #000; overflow: hidden;
}
.video-shell.dragging .video-zone { transition: none !important; }
.full-video { width: 100%; height: 100%; object-fit: contain; background: #000; }
.no-video { text-align: center; position: absolute; top:50%; left:50%; transform: translate(-50%,-50%); }

.video-track {
  width: 100%; height: 100%; position: relative;
  will-change: transform; backface-visibility: hidden;
  transform: translateZ(0);
}
.video-track.dragging { transition: none !important; }
.video-track.snapping { transition: transform 0.25s linear; }

.play-indicator { position: absolute; top:50%;left:50%; transform: translate(-50%,-50%); pointer-events: none; transition: opacity .2s; }
.play-indicator.hide { opacity: 0; }

.heart-burst { position: absolute; top:45%;left:50%; transform: translate(-50%,-50%); font-size: 80px; pointer-events: none; animation: hb .8s ease-out forwards; }
@keyframes hb { 0%{opacity:1;transform:translate(-50%,-50%) scale(.3)} 50%{opacity:1;transform:translate(-50%,-50%) scale(1.3)} 100%{opacity:0;transform:translate(-50%,-50%) scale(1.8)} }

/* 顶部栏 - 悬浮在视频上方 */
.top-bar {
  position: absolute; top:0; left:0; right:0;
  display:flex; align-items:center; justify-content:space-between;
  padding: max(12px,env(safe-area-inset-top)) 16px 12px;
  background: linear-gradient(180deg, rgba(0,0,0,0.6) 0%, rgba(0,0,0,0.2) 70%, transparent 100%);
  z-index: 15;
}
.top-title { color:#fff; font-size:15px; font-weight:600; text-shadow: 0 1px 3px rgba(0,0,0,0.5); }

/* 右侧图层 */
.side-layer {
  position: absolute; inset: 0; pointer-events: none; z-index: 3;
  transition: opacity 0.25s ease;
  will-change: transform; backface-visibility: hidden;
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

/* ====== 抖音风底部控制栏 ====== */
.video-controls { position:absolute; bottom:8px; left:12px; right:12px; z-index:10; transition:opacity 0.3s; }
.video-controls.hide { opacity:0; pointer-events:none; }
.video-progress-track { width:100%; height:2px; background:rgba(255,255,255,0.2); border-radius:1px; cursor:pointer; }
.video-progress-fill { height:100%; background:#fff; border-radius:1px; transition:width 0.15s linear; min-width:0; position:relative; }
.video-progress-fill::after { content:''; position:absolute; right:-4px; top:-3px; width:8px; height:8px; background:#fff; border-radius:50%; }
.video-bottom-row { display:flex; align-items:center; margin-top:2px; }
.video-time-tt { font-size:10px; color:rgba(255,255,255,0.4); font-variant-numeric:tabular-nums; }

/* ====== 全屏按钮 ====== */
/* 竖屏：fixed 绝对定位，固定在视频画面右下角底边下方 */
.fullscreen-btn {
  position:fixed; bottom:86px; right:12px; z-index:10001;
  width:28px; height:28px; border-radius:6px;
  background:rgba(0,0,0,0.25); border:none;
  display:flex; align-items:center; justify-content:center;
  cursor:pointer; transition:opacity 0.3s;
  -webkit-tap-highlight-color:transparent;
  padding:6px; margin:-6px; box-sizing:content-box;
}
.fullscreen-btn.hide { opacity:0; pointer-events:none; }
.fullscreen-btn:active { background:rgba(0,0,0,0.45); }
/* 横屏全屏：回到右上角 */
.fullscreen-btn.fullscreen { bottom:auto; top:48px; right:12px; }
.video-shell.fullscreen .fullscreen-btn { bottom:auto; top:12px; right:12px; }
@keyframes float { 0%,100%{opacity:.6;transform:translateX(-50%) translateY(0)} 50%{opacity:1;transform:translateX(-50%) translateY(-6px)} }

/* ====== 评论抽屉 — 磨砂玻璃 ====== */
.comment-drawer {
  position: absolute; bottom:0; left:0; right:0;
  max-height: 70%;
  background: rgba(255,255,255,0.72);
  backdrop-filter: blur(20px) saturate(170%);
  -webkit-backdrop-filter: blur(20px) saturate(170%);
  display: flex; flex-direction: column;
  overflow: hidden;
  z-index: 20;
  border-radius: 16px 16px 0 0;
  border-top: 0.5px solid rgba(255,255,255,0.5);
}
.drawer-slide-enter-active { transition: all 0.35s cubic-bezier(0.4, 0, 0.2, 1); }
.drawer-slide-leave-active { transition: all 0.28s cubic-bezier(0.4, 0, 0.2, 1); }
.drawer-slide-enter-from,
.drawer-slide-leave-to { opacity: 0; transform: translateY(40%); }
.handle-row { display:flex; justify-content:center; padding:12px 0 4px; cursor:grab; }
.handle-bar { width:36px; height:4px; border-radius:2px; background:rgba(0,0,0,0.15); }
.dr-header { display:flex; justify-content:space-between; align-items:center; padding:8px 16px 12px; }
.dr-title { color:#111; font-size:16px; font-weight:600; }
.dr-list { flex:1; overflow-y:auto; padding:0 16px; -webkit-overflow-scrolling:touch; }
.no-cmt { text-align:center; color:#999; padding:40px; font-size:13px; }
.cmt-row { display:flex; gap:10px; padding:12px 0; border-bottom:0.5px solid rgba(0,0,0,0.05); align-items:flex-start; }
.cmt-av { flex-shrink:0; background:#eee; }
.cmt-main { flex:1; min-width:0; }
.cmt-head { display:flex; justify-content:space-between; color:#999; font-size:11px; margin-bottom:4px; }
.cmt-tm { color:#ccc; }
.cmt-txt { color:#333; font-size:13px; line-height:1.6; word-break:break-word; }
.cmt-img { max-width:120px; border-radius:8px; margin-top:6px; }
.cmt-vid { width:100%; max-height:150px; border-radius:8px; margin-top:6px; background:#000; }
.cmt-del { flex-shrink:0; margin-top:2px; cursor:pointer; }
.dr-input-row { display:flex; align-items:center; gap:8px; padding:8px 12px; margin:8px 12px; background:rgba(255,255,255,0.5); backdrop-filter:blur(10px); -webkit-backdrop-filter:blur(10px); border-radius:22px; border:1px solid rgba(255,255,255,0.4); }
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
