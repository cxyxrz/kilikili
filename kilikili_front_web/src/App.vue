<script setup lang="ts">
import axios from "axios";
import Hls from "hls.js";
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from "vue";

type User = { id: number; username: string; nickname: string };
type Video = {
  id: number;
  authorId?: number;
  title: string;
  description?: string;
  authorName: string;
  authorAvatar?: string | null;
  coverUrl?: string | null;
  durationSeconds?: number;
  likeCount: number;
  commentCount: number;
  viewCount: number;
  categoryId?: number;
  createdAt?: string;
};
type HistoryVideo = Video & { watchedAt: string };
type FavoriteVideo = Video & { favoritedAt: string };
type Follower = { id: number; nickname: string; avatar?: string | null; followedAt: string };
type Comment = { id: number; content: string; nickname: string; parentId?: number | null; createdAt?: string; avatar?: string | null };
type Danmaku = { id: number; content: string; mode: "SCROLL" | "TOP" | "BOTTOM"; color: string; timeMs: number };
type NotificationItem = {
  id: number;
  type: "LIKE" | "COMMENT" | "FOLLOW" | "SYSTEM";
  content: string;
  videoId?: number | null;
  readAt?: string | null;
  createdAt: string;
  actorName: string;
  actorAvatar?: string | null;
};
type FollowingUser = {
  id: number;
  nickname: string;
  avatar?: string | null;
  bio?: string | null;
  followedAt: string;
};
type Category = { id: number; name: string };
type HotKeyword = { keyword: string; score: number; rank: number };
type SpaceUser = {
  id: number;
  nickname: string;
  avatar?: string | null;
  bio?: string | null;
  followerCount: number;
  followingCount: number;
  likeCount: number;
  videoCount: number;
  isSelf: boolean;
  following: boolean;
};
type Conversation = {
  peerId: number;
  peerName: string;
  peerAvatar?: string | null;
  lastContent: string;
  lastAt: string;
  unread: number;
};
type PrivateMessage = {
  id: number;
  fromId: number;
  toId: number;
  content: string;
  readAt?: string | null;
  createdAt: string;
};
type CreatorVideo = {
  id: number;
  title: string;
  coverUrl: string | null;
  status: string;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  createdAt: string;
};
type Profile = {
  id: number;
  username: string;
  nickname: string;
  avatar: string | null;
  gender: "PRIVATE" | "MALE" | "FEMALE";
  birthday: string | null;
  bio: string | null;
};
type LiveRoom = {
  id: number;
  userId: number;
  title: string;
  status: "READY" | "LIVING" | "ENDED";
  coverUrl?: string | null;
  authorName: string;
  authorAvatar?: string | null;
  viewerCount: number;
  danmakuCount: number;
  startedAt?: string | null;
  endedAt?: string | null;
  createdAt?: string;
  playUrl?: string | null;
  streamKey?: string;
  pushUrl?: string;
  hlsUrl?: string;
};
type LiveMessage = {
  id: number;
  roomId: number;
  userId?: number | null;
  type: string;
  content: string;
  createdAt?: string;
  nickname: string;
  avatar?: string | null;
};
type Page =
  | "recommend"
  | "search"
  | "live"
  | "liveRoom"
  | "create"
  | "history"
  | "favorite"
  | "message"
  | "chat"
  | "following"
  | "fans"
  | "space"
  | "settings";

const token = ref(localStorage.getItem("kilikili_token") || "");
const user = ref<User | null>(
  JSON.parse(localStorage.getItem("kilikili_user") || "null"),
);
const activePage = ref<Page>("recommend");
const sidebarCollapsed = ref(false);
const dark = ref(false);
const query = ref("");
const videos = ref<Video[]>([]);
const searchResults = ref<Video[]>([]);
const searchLoading = ref(false);
// 搜索联想、热搜榜与本地搜索历史
const suggestions = ref<string[]>([]);
const suggestVisible = ref(false);
const hotKeywords = ref<HotKeyword[]>([]);
const searchHistory = ref<string[]>(readSearchHistory());
const categories = ref<Category[]>([]);
const selectedCategory = ref(0);
const recommendPage = ref(1);
const recommendLoading = ref(false);
const hasMore = ref(true);
// 首页轮播位：取推荐列表前 5 条，6 秒自动切换
const heroIndex = ref(0);
const heroVideos = computed(() => videos.value.slice(0, 5));
function heroNext() {
  if (!heroVideos.value.length) return;
  heroIndex.value = (heroIndex.value + 1) % heroVideos.value.length;
}
function heroPrev() {
  if (!heroVideos.value.length) return;
  heroIndex.value = (heroIndex.value - 1 + heroVideos.value.length) % heroVideos.value.length;
}
// 换一换：把首条挪到末尾，轮播同步回到第一条，不额外请求接口
function rotateFeed() {
  if (videos.value.length < 2) return;
  const first = videos.value.shift();
  if (first) videos.value.push(first);
  heroIndex.value = 0;
  window.scrollTo({ top: 0, behavior: "smooth" });
}
function categoryName(categoryId?: number | null) {
  if (!categoryId) return "";
  return categories.value.find((item) => item.id === categoryId)?.name || "";
}
const creatorTab = ref<"manage" | "upload" | "stats">("manage");
const creatorVideos = ref<CreatorVideo[]>([]);
const creatorKeyword = ref("");
const creatorStatus = ref("");
type TrendPoint = { date: string; label: string; views: number; likes: number; comments: number; followers: number };
type AudienceItem = { label: string; count: number };
type CreatorVideoStat = {
  id: number;
  title: string;
  coverUrl?: string | null;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  favoriteCount: number;
  createdAt: string;
  interactionRate: number;
};
type CreatorDashboard = {
  days: number;
  overview: {
    videoCount: number;
    viewCount: number;
    likeCount: number;
    commentCount: number;
    favoriteCount: number;
    followerCount: number;
  };
  period: { views: number; likes: number; comments: number; favorites: number; followers: number };
  trend: TrendPoint[];
  audience: { gender: AudienceItem[]; age: AudienceItem[]; hours: { hour: number; count: number }[] };
  videos: CreatorVideoStat[];
};
const dashboard = ref<CreatorDashboard | null>(null);
const dashboardDays = ref<7 | 30>(7);
const dashboardMetric = ref<"views" | "likes" | "comments" | "followers">("views");
const dashboardLoading = ref(false);
const historyVideos = ref<HistoryVideo[]>([]);
const historyLoading = ref(false);
const favoriteVideos = ref<FavoriteVideo[]>([]);
const favoriteLoading = ref(false);
const selectedFavorited = ref(false);
const selectedFollowing = ref(false);
const fans = ref<Follower[]>([]);
const fansLoading = ref(false);
const followingUsers = ref<FollowingUser[]>([]);
const followingTotal = ref(0);
const followingLoading = ref(false);
// UP 主主页
const spaceUserId = ref<number | null>(null);
const spaceUser = ref<SpaceUser | null>(null);
const spaceVideos = ref<Video[]>([]);
const spaceLoading = ref(false);
const spaceSort = ref<"latest" | "popular">("latest");
const spaceFollowBusy = ref(false);
const notifications = ref<NotificationItem[]>([]);
const notificationLoading = ref(false);
const notificationTab = ref<"ALL" | NotificationItem["type"]>("ALL");
const unreadNotificationCount = ref(0);
const conversations = ref<Conversation[]>([]);
const conversationLoading = ref(false);
const activePeerId = ref<number | null>(null);
const chatPeer = ref<{ id: number; nickname: string; avatar?: string | null } | null>(null);
const chatMessages = ref<PrivateMessage[]>([]);
const chatLoading = ref(false);
const chatInput = ref("");
const sendingMessage = ref(false);
const unreadMessageCount = ref(0);
const chatBody = ref<HTMLElement | null>(null);
const selected = ref<Video | null>(null);
const playUrl = ref("");
const comments = ref<Comment[]>([]);
const commentSort = ref<"hot" | "latest">("hot");
// 右栏标签：简介（含 UP 信息与接下来播放）/ 评论
const watchTab = ref<"about" | "comments">("about");
const replyTo = ref<Comment | null>(null);
const notice = ref("");
const authVisible = ref(false);
const authMode = ref<"login" | "register">("login");
const auth = ref({ username: "", password: "", nickname: "" });
// 装饰性二维码图案（扫码登录尚未接入，仅用于界面展示）
const qrCells = Array.from({ length: 441 }, (_, index) => {
  const row = Math.floor(index / 21);
  const col = index % 21;
  const inFinder = (row < 7 && col < 7) || (row < 7 && col > 13) || (row > 13 && col < 7);
  return !inFinder && (index * 7 + row * 3) % 5 < 2;
});
const upload = ref({
  title: "",
  description: "",
  categoryId: 1,
  durationSeconds: 0,
  creationDeclaration: "ORIGINAL",
  cover: null as File | null,
  file: null as File | null,
});
const comment = ref("");
const replyContent = ref("");
const danmaku = ref("");
const danmakuMode = ref<Danmaku["mode"]>("SCROLL");
const danmakuColor = ref("#FFFFFF");
const danmakus = ref<Danmaku[]>([]);
// 弹幕防挡脸：后端抽帧检测出的人脸时间片段；热度图：按时间桶聚合的弹幕条数
type DanmakuMaskSegment = { startMs: number; endMs: number; maskTop: number; maskBottom: number; faceRatio: number };
type DanmakuHeatBucket = { startMs: number; endMs: number; count: number };
const danmakuMaskSegments = ref<DanmakuMaskSegment[]>([]);
const danmakuMaskStatus = ref("NONE");
const danmakuHeat = ref<{
  bucketSeconds: number;
  durationMs: number;
  totalCount: number;
  maxCount: number;
  peakMs: number;
  buckets: DanmakuHeatBucket[];
}>({ bucketSeconds: 10, durationMs: 0, totalCount: 0, maxCount: 0, peakMs: 0, buckets: [] });
const profile = ref<Profile>({
  id: 0,
  username: "",
  nickname: "",
  avatar: null,
  gender: "PRIVATE",
  birthday: null,
  bio: "",
});
const avatarInput = ref<HTMLInputElement | null>(null);
const videoInput = ref<HTMLInputElement | null>(null);
const coverInput = ref<HTMLInputElement | null>(null);
const coverPreview = ref("");
const isDraggingVideo = ref(false);
const videoPlayer = ref<HTMLVideoElement | null>(null);
const danmakuCanvas = ref<HTMLCanvasElement | null>(null);
let danmakuFrame = 0;
let searchTimer = 0;
let suggestTimer = 0;
const SEARCH_HISTORY_KEY = "kilikili:search:history";
// WebSocket 实时通知：连接句柄、重连定时器、心跳定时器
let realtimeSocket: WebSocket | null = null;
let realtimeReconnectTimer = 0;
let realtimeHeartbeatTimer = 0;
let realtimeRetry = 0;
let hlsInstance: Hls | null = null;
const uploadSuccessVisible = ref(false);
const uploadSuccessMessage = ref("");
const uploadWarningVisible = ref(false);
const uploadWarningMessage = ref("");
// 清晰度以「分辨率高度」标识，菜单优先用后端返回的转码档位填充，hls.js 解析后再校正
const qualityLevels = ref<{ height: number; label: string }[]>([]);
const selectedQuality = ref<number | null>(null);
// 后端声明的转码档位高度，用于在 hls.js 解析完成前先渲染清晰度菜单
const serverHeights = ref<number[]>([]);
const qualityMenuOpen = ref(false);
const theaterMode = ref(false);
const isPlaying = ref(false);
const currentTime = ref(0);
// 打开视频时从后端取到的续播进度（秒），元数据加载完成后跳转
let resumeSeconds = 0;
const duration = ref(0);
const volume = ref(1);
const muted = ref(false);
const playbackRate = ref(1);
const danmakuOn = ref(true);
// 防挡脸开关：开启后弹幕轨道自动避开人脸区域
const faceMaskOn = ref(true);
const danmakuSettingsOpen = ref(false);
const rateMenuOpen = ref(false);
const stageRef = ref<HTMLElement | null>(null);

// 直播：房间列表、开播台与直播间弹幕
const liveRooms = ref<LiveRoom[]>([]);
const liveLoading = ref(false);
const liveMineRoom = ref<LiveRoom | null>(null);
const liveTitle = ref("");
const liveCover = ref<File | null>(null);
const liveCoverPreview = ref("");
const liveCreating = ref(false);
const liveCopied = ref(false);
const liveCurrent = ref<LiveRoom | null>(null);
const liveMessages = ref<LiveMessage[]>([]);
const liveInput = ref("");
const liveViewerCount = ref(0);
const liveStatusTip = ref("");
const liveRoomLoading = ref(false);
const livePlayer = ref<HTMLVideoElement | null>(null);
const liveMessageList = ref<HTMLElement | null>(null);
const liveStudioRef = ref<HTMLElement | null>(null);
let liveSocket: WebSocket | null = null;
let liveReconnectTimer = 0;
let liveHeartbeatTimer = 0;
let liveSocketRetry = 0;
let liveHls: Hls | null = null;

const progressPercent = computed(() =>
  duration.value > 0 ? Math.min(100, (currentTime.value / duration.value) * 100) : 0
);

function togglePlay() {
  const video = videoPlayer.value;
  if (!video) return;
  if (video.paused) void video.play();
  else video.pause();
}

// 点击画面任意位置切换播放/暂停；控制栏上的点击不触发
function onStageClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null;
  if (target?.closest(".player-bar")) return;
  togglePlay();
}

function onVideoMeta() {
  const video = videoPlayer.value;
  duration.value = video?.duration || 0;
  currentTime.value = video?.currentTime || 0;
  // 续播：进度接近结尾时不跳转，避免一打开就播完
  if (video && resumeSeconds > 0 && resumeSeconds < duration.value - 3) {
    video.currentTime = resumeSeconds;
    currentTime.value = resumeSeconds;
  }
  resumeSeconds = 0;
  video && (video.playbackRate = playbackRate.value);
  startDanmakuRender();
}

function onTimeUpdate() {
  currentTime.value = videoPlayer.value?.currentTime || 0;
}

// 播放进度：每 15 秒上报一次，后端先写 Redis，再由延迟任务合并落库
let progressPoll = 0;
let heroTimer = 0;
function reportPlayProgress() {
  const player = videoPlayer.value;
  if (!loggedIn.value || !selected.value || !player || player.paused) return;
  if (!Number.isFinite(player.duration) || player.duration <= 0) return;
  void axios
    .post(
      `/api/videos/${selected.value.id}/progress`,
      {
        progressSeconds: Math.round(player.currentTime),
        durationSeconds: Math.round(player.duration),
      },
      { headers: authHeaders() },
    )
    .catch(() => undefined);
}

function formatClock(seconds: number) {
  if (!Number.isFinite(seconds) || seconds < 0) seconds = 0;
  const minutes = Math.floor(seconds / 60);
  const rest = Math.floor(seconds % 60);
  return `${String(minutes).padStart(2, "0")}:${String(rest).padStart(2, "0")}`;
}

// 进度条：点击与拖拽定位
let seeking = false;
function seekFromPointer(event: PointerEvent) {
  const video = videoPlayer.value;
  const bar = event.currentTarget as HTMLElement;
  if (!video || !duration.value) return;
  const rect = bar.getBoundingClientRect();
  const ratio = Math.min(1, Math.max(0, (event.clientX - rect.left) / rect.width));
  video.currentTime = ratio * duration.value;
  currentTime.value = video.currentTime;
}
function startSeek(event: PointerEvent) {
  seeking = true;
  (event.currentTarget as HTMLElement).setPointerCapture?.(event.pointerId);
  seekFromPointer(event);
}
function moveSeek(event: PointerEvent) {
  if (seeking) seekFromPointer(event);
}
function endSeek() {
  seeking = false;
}

function toggleMute() {
  const video = videoPlayer.value;
  if (!video) return;
  video.muted = !video.muted;
  muted.value = video.muted;
}

function onVolumeInput(event: Event) {
  const video = videoPlayer.value;
  const value = Number((event.target as HTMLInputElement).value);
  volume.value = value;
  if (!video) return;
  video.volume = value;
  video.muted = value === 0;
  muted.value = video.muted;
}

function pickRate(rate: number) {
  const video = videoPlayer.value;
  playbackRate.value = rate;
  if (video) video.playbackRate = rate;
  rateMenuOpen.value = false;
}

function toggleFullscreen() {
  const stage = stageRef.value;
  if (!stage) return;
  if (document.fullscreenElement) void document.exitFullscreen();
  else void stage.requestFullscreen?.();
}

// 按分辨率给出 B 站风格的清晰度名称
function qualityLabel(height: number) {
  if (height >= 2160) return "4K 超高清";
  if (height >= 1080) return "1080P 高清";
  if (height >= 720) return "720P 准高清";
  if (height >= 480) return "480P 标清";
  return "360P 流畅";
}

// 播放器右下角清晰度角标文案，如 "1080P 高清" / "自动"
const qualityBadgeText = computed(() => {
  if (selectedQuality.value === null) return "自动";
  const quality = qualityLevels.value.find((item) => item.height === selectedQuality.value);
  return quality ? quality.label : "自动";
});

function pickQuality(height: number | null) {
  switchQuality(height);
  qualityMenuOpen.value = false;
}

function destroyHls() {
  if (hlsInstance) {
    hlsInstance.destroy();
    hlsInstance = null;
  }
  qualityLevels.value = [];
  selectedQuality.value = null;
}

// 后端 render 档位（如 "1080p"）转成高度，用于在 hls.js 解析完成前就先渲染清晰度菜单
function renditionHeights(renditions: string[]) {
  const heights = renditions
    .map((name) => Number.parseInt(name, 10))
    .filter((height) => Number.isFinite(height) && height > 0);
  return [...new Set(heights)].sort((a, b) => b - a);
}

// HLS 地址交给 hls.js（优先），无法使用 MSE 时再退回浏览器原生 HLS，普通 MP4 直接设置 src
function attachVideoSource() {
  const el = videoPlayer.value;
  const url = playUrl.value;
  if (!el || !url) return;
  destroyHls();
  if (!url.includes(".m3u8")) {
    el.src = url;
    return;
  }
  // destroyHls 会清空档位，这里用后端数据立即重建，保证菜单不等 hls.js 解析就能显示
  qualityLevels.value = serverHeights.value.map((height) => ({ height, label: qualityLabel(height) }));
  if (Hls.isSupported()) {
    hlsInstance = new Hls();
    hlsInstance.loadSource(url);
    hlsInstance.attachMedia(el);
    hlsInstance.on(Hls.Events.MANIFEST_PARSED, (_event, data) => {
      const parsed = [...new Set(data.levels.map((level) => level.height).filter((height) => height > 0))].sort((a, b) => b - a);
      // hls.js 解析出的档位比后端声明更可靠，两者都为空时才留空菜单
      if (parsed.length) {
        qualityLevels.value = parsed.map((height) => ({ height, label: qualityLabel(height) }));
      }
      // 解析完成后补上之前选中的档位（用户可能在解析前已点选）
      if (selectedQuality.value !== null) switchQuality(selectedQuality.value);
    });
    // 瞬时故障自愈：后端重启等网络错误自动重试，媒体错误自动恢复
    hlsInstance.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) hlsInstance?.startLoad();
      else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) hlsInstance?.recoverMediaError();
    });
  } else if (el.canPlayType("application/vnd.apple.mpegurl")) {
    el.src = url;
  } else {
    el.src = url;
  }
}

function switchQuality(height: number | null) {
  selectedQuality.value = height;
  if (!hlsInstance) return;
  if (height === null) {
    // 自动：交还给 hls.js 按带宽自适应
    hlsInstance.currentLevel = -1;
    return;
  }
  const level = hlsInstance.levels.findIndex((item) => item.height === height);
  if (level >= 0) hlsInstance.currentLevel = level;
}

const navItems: { key: Page; label: string; icon: string }[] = [
  { key: "recommend", label: "推荐", icon: "▣" },
  { key: "live", label: "直播", icon: "◉" },
  { key: "search", label: "搜索", icon: "⌕" },
  { key: "create", label: "创作中心", icon: "□" },
  { key: "history", label: "历史记录", icon: "◷" },
  { key: "favorite", label: "我的收藏", icon: "☆" },
  { key: "message", label: "消息通知", icon: "✉" },
  { key: "chat", label: "私信", icon: "✎" },
  { key: "following", label: "我的关注", icon: "♙" },
  { key: "fans", label: "我的粉丝", icon: "●" },
  { key: "settings", label: "账号设置", icon: "⚙" },
];
const pageTitle: Record<Page, string> = {
  recommend: "推荐视频",
  live: "直播广场",
  liveRoom: "直播间",
  search: "搜索结果",
  create: "创作中心",
  history: "观看历史",
  favorite: "我的收藏",
  message: "消息通知",
  chat: "私信",
  following: "我的关注",
  fans: "粉丝列表",
  space: "个人主页",
  settings: "账号设置",
};
const loggedIn = computed(() => Boolean(token.value));
const activeConversation = computed(() =>
  conversations.value.find((item) => item.peerId === activePeerId.value) || null,
);
const displayedVideos = computed(() => activePage.value === "search" ? searchResults.value : videos.value);
const relatedVideos = computed(() =>
  videos.value.filter((video) => video.id !== selected.value?.id).slice(0, 8),
);
const isFollowingSelected = computed(() => selected.value?.authorId != null && selectedFollowing.value);
const sortedComments = computed(() => {
  const items = [...comments.value];
  return commentSort.value === "latest"
    ? items.sort((left, right) => new Date(right.createdAt || 0).getTime() - new Date(left.createdAt || 0).getTime())
    : items;
});
const topLevelComments = computed(() => sortedComments.value.filter((item) => item.parentId == null));
const repliesFor = (commentId: number) => sortedComments.value.filter((item) => item.parentId === commentId);
const authHeaders = () => ({ Authorization: `Bearer ${token.value}` });
const avatarText = computed(() =>
  (user.value?.nickname || "K").slice(0, 1).toUpperCase(),
);
const fail = (error: any) => {
  notice.value =
    error.response?.data?.message || "请求失败，请检查本地服务是否启动";
};

async function loadCategories() {
  try {
    categories.value = (await axios.get("/api/categories")).data.data;
  } catch (error) {
    fail(error);
  }
}
async function loadVideos(reset = true) {
  if (recommendLoading.value || (!reset && !hasMore.value)) return;
  const page = reset ? 1 : recommendPage.value + 1;
  recommendLoading.value = true;
  try {
    const data = (
      await axios.get("/api/recommendations", {
        params: {
          categoryId: selectedCategory.value || undefined,
          page,
          pageSize: 20,
        },
      })
    ).data.data;
    videos.value = reset ? data.items : [...videos.value, ...data.items];
    recommendPage.value = page;
    hasMore.value = data.hasMore;
    if (reset) heroIndex.value = 0;
  } catch (error) {
    fail(error);
  } finally {
    recommendLoading.value = false;
  }
}
async function loadSearchResults() {
  const keyword = query.value.trim();
  if (!keyword) {
    searchResults.value = [];
    return;
  }
  searchLoading.value = true;
  try {
    searchResults.value = (await axios.get("/api/search", {
      params: { keyword, page: 1, pageSize: 40 },
    })).data.data.items;
    rememberSearch(keyword);
    loadHotKeywords();
  } catch (error) {
    fail(error);
  } finally {
    searchLoading.value = false;
  }
}
// 搜索联想：输入停顿 200ms 后请求，避免每次按键都打后端
function scheduleSuggest() {
  window.clearTimeout(suggestTimer);
  const keyword = query.value.trim();
  if (!keyword) {
    suggestions.value = [];
    return;
  }
  suggestTimer = window.setTimeout(async () => {
    try {
      suggestions.value = (await axios.get("/api/search/suggest", { params: { keyword } })).data.data;
    } catch {
      suggestions.value = [];
    }
  }, 200);
}
async function loadHotKeywords() {
  try {
    hotKeywords.value = (await axios.get("/api/search/hot")).data.data;
  } catch {
    hotKeywords.value = [];
  }
}
function submitSearch(keyword: string) {
  const value = keyword.trim();
  if (!value) return;
  query.value = value;
  suggestions.value = [];
  suggestVisible.value = false;
  activePage.value = "search";
  window.clearTimeout(searchTimer);
  loadSearchResults();
}
function clearSearchHistory() {
  searchHistory.value = [];
  localStorage.removeItem(SEARCH_HISTORY_KEY);
}
function rememberSearch(keyword: string) {
  const next = [keyword, ...searchHistory.value.filter((item) => item !== keyword)].slice(0, 10);
  searchHistory.value = next;
  localStorage.setItem(SEARCH_HISTORY_KEY, JSON.stringify(next));
}
function readSearchHistory(): string[] {
  try {
    const raw = localStorage.getItem(SEARCH_HISTORY_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === "string").slice(0, 10) : [];
  } catch {
    return [];
  }
}
function hideSuggestSoon() {
  window.setTimeout(() => (suggestVisible.value = false), 160);
}
function selectCategory(categoryId: number) {
  selectedCategory.value = categoryId;
  loadVideos();
}
function onContentScroll(event: Event) {
  const target = event.target as HTMLElement;
  if (
    activePage.value === "recommend" &&
    target.scrollTop + target.clientHeight >= target.scrollHeight - 180
  )
    loadVideos(false);
}
function formatDuration(seconds = 0) {
  const minute = Math.floor(seconds / 60);
  const second = seconds % 60;
  return `${minute}:${String(second).padStart(2, "0")}`;
}
function formatCount(value = 0) {
  return value >= 10000 ? `${(value / 10000).toFixed(1)}万` : value;
}
async function toggleAuthorFollow() {
  if (!selected.value?.authorId) return;
  if (!loggedIn.value) {
    openAuth();
    return;
  }
  try {
    const data = (await axios.post(`/api/users/${selected.value.authorId}/follow`, {}, { headers: authHeaders() })).data.data;
    selectedFollowing.value = data.following;
    if (activePage.value === "following") loadFollowingUsers();
    notice.value = data.following ? "已关注 UP 主" : "已取消关注";
  } catch (error) {
    fail(error);
  }
}
// 关注列表中本页取关/回关的本地状态：取关后保留卡片，方便再次关注
const unfollowedAuthorIds = reactive(new Set<number>());
const followingToggleIds = reactive(new Set<number>());
async function toggleFollowingUser(author: FollowingUser) {
  if (followingToggleIds.has(author.id)) return;
  followingToggleIds.add(author.id);
  try {
    const data = (await axios.post(`/api/users/${author.id}/follow`, {}, { headers: authHeaders() })).data.data;
    if (data.following) unfollowedAuthorIds.delete(author.id);
    else unfollowedAuthorIds.add(author.id);
    notice.value = data.following ? `已关注 ${author.nickname}` : `已取消关注 ${author.nickname}`;
  } catch (error) {
    fail(error);
  } finally {
    followingToggleIds.delete(author.id);
  }
}
function formatDate(value: string) {
  return value ? value.replace("T", " ").slice(0, 16) : "-";
}
function formatHistoryTime(value: string) {
  const watchedAt = new Date(value.replace(" ", "T")).getTime();
  const seconds = Math.max(0, Math.floor((Date.now() - watchedAt) / 1000));
  if (seconds < 60) return "刚刚";
  if (seconds < 3600) return `${Math.floor(seconds / 60)} 分钟前`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} 小时前`;
  if (seconds < 172800) return "昨天";
  return formatDate(value);
}
function openAuth(mode: "login" | "register" = "login") {
  authMode.value = mode;
  authVisible.value = true;
}
async function submitAuth() {
  try {
    if (authMode.value === "login") {
      const response = (await axios.post("/api/auth/login", auth.value)).data
        .data;
      token.value = response.token;
      user.value = response.user;
      localStorage.setItem("kilikili_token", token.value);
      localStorage.setItem("kilikili_user", JSON.stringify(user.value));
      loadProfile();
      notice.value = "登录成功";
      authVisible.value = false;
      if (activePage.value === "history") loadHistory();
      if (activePage.value === "favorite") loadFavorites();
      if (activePage.value === "message") loadNotifications();
      if (activePage.value === "chat") loadConversations();
      loadUnreadNotificationCount();
      loadUnreadMessageCount();
      connectRealtime();
      if (activePage.value === "fans") loadFans();
      if (activePage.value === "following") loadFollowingUsers();
    } else {
      await axios.post("/api/auth/register", auth.value);
      notice.value = "注册成功，请登录";
      authMode.value = "login";
    }
  } catch (error) {
    fail(error);
  }
}
function logout() {
  token.value = "";
  user.value = null;
  historyVideos.value = [];
  favoriteVideos.value = [];
  fans.value = [];
  followingUsers.value = [];
  followingTotal.value = 0;
  notifications.value = [];
  unreadNotificationCount.value = 0;
  conversations.value = [];
  activePeerId.value = null;
  chatPeer.value = null;
  chatMessages.value = [];
  unreadMessageCount.value = 0;
  disconnectRealtime();
  localStorage.removeItem("kilikili_token");
  localStorage.removeItem("kilikili_user");
  notice.value = "已退出登录";
}
async function loadProfile() {
  if (!loggedIn.value) return;
  try {
    profile.value = (
      await axios.get("/api/auth/me/profile", { headers: authHeaders() })
    ).data.data;
  } catch (error) {
    fail(error);
  }
}
async function saveProfile() {
  try {
    const saved = (
      await axios.patch("/api/auth/me/profile", profile.value, {
        headers: authHeaders(),
      })
    ).data.data;
    profile.value = saved;
    if (user.value) {
      user.value.nickname = saved.nickname;
      localStorage.setItem("kilikili_user", JSON.stringify(user.value));
    }
    notice.value = "资料已保存";
  } catch (error) {
    fail(error);
  }
}
async function uploadAvatar(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0];
  if (!file) return;
  try {
    const form = new FormData();
    form.append("file", file);
    profile.value = (
      await axios.post("/api/auth/me/avatar", form, { headers: authHeaders() })
    ).data.data;
    notice.value = "头像已更新";
  } catch (error) {
    fail(error);
  } finally {
    if (avatarInput.value) avatarInput.value.value = "";
  }
}
async function loadCreatorVideos() {
  if (!loggedIn.value) return;
  try {
    creatorVideos.value = (
      await axios.get("/api/videos/mine", {
        headers: authHeaders(),
        params: {
          keyword: creatorKeyword.value,
          status: creatorStatus.value,
          page: 1,
          pageSize: 10,
        },
      })
    ).data.data.items;
  } catch (error) {
    fail(error);
  }
}
async function loadCreatorDashboard(days: 7 | 30 = dashboardDays.value) {
  if (!loggedIn.value) return;
  dashboardDays.value = days;
  dashboardLoading.value = true;
  try {
    dashboard.value = (
      await axios.get("/api/creator/dashboard", {
        headers: authHeaders(),
        params: { days },
      })
    ).data.data;
  } catch (error) {
    fail(error);
  } finally {
    dashboardLoading.value = false;
  }
}
const trendPoints = computed(() => dashboard.value?.trend || []);
const trendChart = computed(() => {
  const points = trendPoints.value;
  const metric = dashboardMetric.value;
  const width = 760;
  const height = 240;
  const left = 48;
  const right = 16;
  const top = 18;
  const bottom = 26;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const rawMax = Math.max(1, ...points.map((point) => point[metric]));
  const step = Math.max(1, Math.ceil(rawMax / 4));
  const max = step * 4;
  const coords = points.map((point, index) => ({
    x: left + (points.length === 1 ? plotWidth / 2 : (plotWidth * index) / (points.length - 1)),
    y: top + plotHeight - (point[metric] / max) * plotHeight,
    label: point.label,
    value: point[metric],
  }));
  const line = coords.map((point, index) => `${index === 0 ? "M" : "L"}${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(" ");
  const baseline = top + plotHeight;
  const area = coords.length
    ? `${line} L${coords[coords.length - 1].x.toFixed(1)},${baseline} L${coords[0].x.toFixed(1)},${baseline} Z`
    : "";
  const ticks = [0, 1, 2, 3, 4].map((index) => ({
    y: baseline - (plotHeight * index) / 4,
    value: step * index,
  }));
  // 天数较多时隔几个点显示横轴标签，避免拥挤
  const labelEvery = Math.ceil(points.length / 8);
  return { width, height, left, right, top, bottom, plotWidth, plotHeight, coords, line, area, ticks, baseline, labelEvery };
});
const audienceTotal = computed(() =>
  (dashboard.value?.audience.gender || []).reduce((sum, item) => sum + item.count, 0),
);
const audienceMax = computed(() => {
  const counts = [...(dashboard.value?.audience.age || []), ...(dashboard.value?.audience.gender || [])].map((item) => item.count);
  return Math.max(1, ...counts);
});
const hoursMax = computed(() => Math.max(1, ...(dashboard.value?.audience.hours || []).map((item) => item.count)));
const maxVideoViews = computed(() => Math.max(1, ...(dashboard.value?.videos || []).map((video) => video.viewCount)));
async function loadHistory() {
  if (!loggedIn.value) {
    historyVideos.value = [];
    return;
  }
  historyLoading.value = true;
  try {
    historyVideos.value = (
      await axios.get("/api/videos/history", {
        headers: authHeaders(),
        params: { page: 1, pageSize: 50 },
      })
    ).data.data.items;
  } catch (error) {
    fail(error);
  } finally {
    historyLoading.value = false;
  }
}
async function loadFavorites() {
  if (!loggedIn.value) {
    favoriteVideos.value = [];
    return;
  }
  favoriteLoading.value = true;
  try {
    favoriteVideos.value = (await axios.get("/api/videos/favorites", {
      headers: authHeaders(),
      params: { page: 1, pageSize: 50 },
    })).data.data.items;
  } catch (error) {
    fail(error);
  } finally {
    favoriteLoading.value = false;
  }
}
async function loadNotifications() {
  if (!loggedIn.value) {
    notifications.value = [];
    return;
  }
  notificationLoading.value = true;
  try {
    notifications.value = (await axios.get("/api/notifications", {
      headers: authHeaders(),
      params: {
        type: notificationTab.value === "ALL" ? undefined : notificationTab.value,
        page: 1,
        pageSize: 50,
      },
    })).data.data.items;
  } catch (error) {
    fail(error);
  } finally {
    notificationLoading.value = false;
  }
}
async function loadUnreadNotificationCount() {
  if (!loggedIn.value) {
    unreadNotificationCount.value = 0;
    return;
  }
  try {
    unreadNotificationCount.value = (await axios.get("/api/notifications/unread-count", {
      headers: authHeaders(),
    })).data.data.count;
  } catch {
    // The notification badge should not interrupt normal page use when the server is unavailable.
  }
}
async function loadUnreadMessageCount() {
  if (!loggedIn.value) {
    unreadMessageCount.value = 0;
    return;
  }
  try {
    unreadMessageCount.value = (await axios.get("/api/messages/unread-count", {
      headers: authHeaders(),
    })).data.data.count;
  } catch {
    // 私信红点失败不影响主流程
  }
}
async function loadConversations() {
  if (!loggedIn.value) {
    conversations.value = [];
    return;
  }
  conversationLoading.value = true;
  try {
    conversations.value = (await axios.get("/api/messages/conversations", {
      headers: authHeaders(),
    })).data.data.items;
    if (activePeerId.value && !conversations.value.some((item) => item.peerId === activePeerId.value)) {
      activePeerId.value = null;
      chatPeer.value = null;
      chatMessages.value = [];
    }
  } catch (error) {
    fail(error);
  } finally {
    conversationLoading.value = false;
  }
}
async function loadChatThread(peerId: number) {
  if (!loggedIn.value) return;
  chatLoading.value = true;
  try {
    const data = (await axios.get(`/api/messages/${peerId}`, {
      headers: authHeaders(),
      params: { page: 1, pageSize: 50 },
    })).data.data;
    chatMessages.value = data.items;
    chatPeer.value = data.peer;
    await nextTick();
    if (chatBody.value) chatBody.value.scrollTop = chatBody.value.scrollHeight;
  } catch (error) {
    fail(error);
  } finally {
    chatLoading.value = false;
  }
}
async function openConversation(peerId: number) {
  activePeerId.value = peerId;
  await loadChatThread(peerId);
  await markConversationRead(peerId);
}
async function markConversationRead(peerId: number) {
  try {
    unreadMessageCount.value = (
      await axios.post(`/api/messages/${peerId}/read`, {}, { headers: authHeaders() })
    ).data.data.unread;
    const target = conversations.value.find((item) => item.peerId === peerId);
    if (target) target.unread = 0;
  } catch {
    // 标记已读失败时保持原红点，等待下次拉取
  }
}
async function sendPrivateMessage() {
  if (!activePeerId.value || !chatInput.value.trim() || sendingMessage.value) return;
  const peerId = activePeerId.value;
  sendingMessage.value = true;
  try {
    await axios.post("/api/messages", { toId: peerId, content: chatInput.value.trim() }, { headers: authHeaders() });
    chatInput.value = "";
    await loadChatThread(peerId);
    await loadConversations();
  } catch (error) {
    fail(error);
  } finally {
    sendingMessage.value = false;
  }
}
function realtimeUrl() {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/notifications?token=${encodeURIComponent(token.value)}`;
}
function connectRealtime() {
  if (!loggedIn.value || realtimeSocket) return;
  const socket = new WebSocket(realtimeUrl());
  realtimeSocket = socket;
  socket.onopen = () => {
    realtimeRetry = 0;
    window.clearInterval(realtimeHeartbeatTimer);
    // 心跳：及时感知断线，触发自动重连
    realtimeHeartbeatTimer = window.setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) socket.send("ping");
    }, 25000);
  };
  socket.onmessage = (event) => {
    if (event.data === "pong") return;
    try {
      handleRealtimeEvent(JSON.parse(event.data));
    } catch {
      // 忽略无法解析的推送内容
    }
  };
  socket.onclose = () => {
    window.clearInterval(realtimeHeartbeatTimer);
    realtimeHeartbeatTimer = 0;
    if (realtimeSocket === socket) realtimeSocket = null;
    scheduleRealtimeReconnect();
  };
  socket.onerror = () => socket.close();
}
function scheduleRealtimeReconnect() {
  if (!loggedIn.value || realtimeReconnectTimer) return;
  const delay = Math.min(30000, 1000 * 2 ** realtimeRetry);
  realtimeRetry = Math.min(realtimeRetry + 1, 5);
  realtimeReconnectTimer = window.setTimeout(() => {
    realtimeReconnectTimer = 0;
    connectRealtime();
  }, delay);
}
function disconnectRealtime() {
  window.clearTimeout(realtimeReconnectTimer);
  realtimeReconnectTimer = 0;
  window.clearInterval(realtimeHeartbeatTimer);
  realtimeHeartbeatTimer = 0;
  realtimeRetry = 0;
  if (realtimeSocket) {
    const socket = realtimeSocket;
    realtimeSocket = null;
    socket.close();
  }
}
function handleRealtimeEvent(event: any) {
  if (event.kind === "NOTIFICATION") {
    unreadNotificationCount.value =
      typeof event.unread === "number" ? event.unread : unreadNotificationCount.value + 1;
    if (activePage.value === "message") loadNotifications();
    return;
  }
  if (event.kind === "MESSAGE") {
    unreadMessageCount.value =
      typeof event.unread === "number" ? event.unread : unreadMessageCount.value + 1;
    if (activePage.value === "chat") {
      loadConversations();
      if (activePeerId.value === event.fromId) {
        loadChatThread(event.fromId);
        markConversationRead(event.fromId);
      }
    }
  }
}
async function loadFans() {
  if (!loggedIn.value) {
    fans.value = [];
    return;
  }
  fansLoading.value = true;
  try {
    fans.value = (await axios.get("/api/users/me/followers", {
      headers: authHeaders(),
      params: { page: 1, pageSize: 50 },
    })).data.data.items;
  } catch (error) {
    fail(error);
  } finally {
    fansLoading.value = false;
  }
}
async function loadFollowingUsers() {
  if (!loggedIn.value) {
    followingUsers.value = [];
    followingTotal.value = 0;
    return;
  }
  followingLoading.value = true;
  try {
    const data = (await axios.get("/api/users/me/following", {
      headers: authHeaders(),
      params: { page: 1, pageSize: 50 },
    })).data.data;
    followingUsers.value = data.items;
    followingTotal.value = data.total;
    // 重新拉取后以服务端状态为准，清空本地取关标记
    unfollowedAuthorIds.clear();
  } catch (error) {
    fail(error);
  } finally {
    followingLoading.value = false;
  }
}
async function openSpace(userId: number) {
  activePage.value = "space";
  selected.value = null;
  spaceUserId.value = userId;
  spaceSort.value = "latest";
  await loadSpace();
}
async function loadSpace() {
  if (!spaceUserId.value) return;
  spaceLoading.value = true;
  try {
    const data = (await axios.get(`/api/users/${spaceUserId.value}/space`, {
      headers: authHeaders(),
      params: { sort: spaceSort.value, page: 1, pageSize: 12 },
    })).data.data;
    spaceUser.value = data.user;
    spaceVideos.value = data.videos;
  } catch (error) {
    fail(error);
  } finally {
    spaceLoading.value = false;
  }
}
async function switchSpaceSort(sort: "latest" | "popular") {
  if (spaceSort.value === sort) return;
  spaceSort.value = sort;
  await loadSpace();
}
async function toggleSpaceFollow() {
  if (!spaceUser.value || spaceUser.value.isSelf || spaceFollowBusy.value) return;
  if (!loggedIn.value) {
    openAuth();
    return;
  }
  spaceFollowBusy.value = true;
  try {
    const data = (await axios.post(`/api/users/${spaceUser.value.id}/follow`, {}, { headers: authHeaders() })).data.data;
    spaceUser.value.following = data.following;
    spaceUser.value.followerCount += data.following ? 1 : -1;
    notice.value = data.following ? "已关注 UP 主" : "已取消关注";
  } catch (error) {
    fail(error);
  } finally {
    spaceFollowBusy.value = false;
  }
}
async function openChatWith(peerId: number) {
  activePage.value = "chat";
  selected.value = null;
  await loadConversations();
  await openConversation(peerId);
}
function selectNotificationTab(tab: "ALL" | NotificationItem["type"]) {
  if (notificationTab.value === tab) return;
  notificationTab.value = tab;
  loadNotifications();
}
async function markAllNotificationsRead() {
  try {
    await axios.post("/api/notifications/read-all", {}, { headers: authHeaders() });
    notifications.value = notifications.value.map((item) => ({
      ...item,
      readAt: item.readAt || new Date().toISOString(),
    }));
    unreadNotificationCount.value = 0;
    notice.value = "全部消息已读";
  } catch (error) {
    fail(error);
  }
}
async function deleteNotification(id: number) {
  const target = notifications.value.find((item) => item.id === id);
  try {
    await axios.delete(`/api/notifications/${id}`, { headers: authHeaders() });
    notifications.value = notifications.value.filter((item) => item.id !== id);
    if (target && !target.readAt) unreadNotificationCount.value = Math.max(0, unreadNotificationCount.value - 1);
  } catch (error) {
    fail(error);
  }
}
async function removeHistory(videoId: number) {
  try {
    await axios.delete(`/api/videos/history/${videoId}`, {
      headers: authHeaders(),
    });
    historyVideos.value = historyVideos.value.filter(
      (video) => video.id !== videoId,
    );
  } catch (error) {
    fail(error);
  }
}
async function clearHistory() {
  if (
    !historyVideos.value.length ||
    !window.confirm("确定清空全部观看历史吗？")
  )
    return;
  try {
    await axios.delete("/api/videos/history", { headers: authHeaders() });
    historyVideos.value = [];
  } catch (error) {
    fail(error);
  }
}
function creatorAction(action: string) {
  notice.value = `${action}功能将在下一阶段开放`;
}
function watchAction(action: string) {
  notice.value = `${action}功能将在后续阶段开放`;
}
async function loadDanmakus(videoId: number) {
  try {
    danmakus.value = (await axios.get(`/api/videos/${videoId}/danmakus`)).data.data;
  } catch (error) {
    fail(error);
  }
}
// 后端首次请求会触发 FFmpeg 抽帧分析，RUNNING 期间短轮询直到拿到遮罩
async function loadDanmakuMask(videoId: number, attempt = 0) {
  try {
    const data = (await axios.get(`/api/videos/${videoId}/danmaku/mask`)).data.data;
    if (selected.value?.id !== videoId) return;
    danmakuMaskStatus.value = data.status;
    danmakuMaskSegments.value = data.segments ?? [];
    const pending = data.status === "RUNNING" || data.status === "PENDING";
    if (pending && attempt < 20 && selected.value?.id === videoId) {
      window.setTimeout(() => loadDanmakuMask(videoId, attempt + 1), 3000);
    }
  } catch {
    danmakuMaskStatus.value = "FAILED";
    danmakuMaskSegments.value = [];
  }
}
async function loadDanmakuHeatmap(videoId: number) {
  try {
    const data = (await axios.get(`/api/videos/${videoId}/danmaku/heatmap`, { params: { bucketSeconds: 10 } })).data.data;
    if (selected.value?.id !== videoId) return;
    danmakuHeat.value = data;
  } catch {
    danmakuHeat.value = { bucketSeconds: 10, durationMs: 0, totalCount: 0, maxCount: 0, peakMs: 0, buckets: [] };
  }
}
// 取当前播放时间命中的人脸区域（0~1 比例），没有命中的片段返回 null
function currentMaskSegment(timeMs: number): DanmakuMaskSegment | null {
  if (!faceMaskOn.value) return null;
  for (const segment of danmakuMaskSegments.value) {
    if (timeMs >= segment.startMs && timeMs < segment.endMs) return segment;
  }
  return null;
}
function heatLeft(bucket: { startMs: number }, durationMs: number) {
  if (!durationMs) return 0;
  return Math.max(0, Math.min(100, (bucket.startMs / durationMs) * 100));
}
function heatWidth(bucket: { startMs: number; endMs: number }, durationMs: number) {
  if (!durationMs) return 0;
  return Math.max(0.4, Math.min(100, ((bucket.endMs - bucket.startMs) / durationMs) * 100));
}
function heatHeight(count: number, maxCount: number) {
  if (!maxCount) return 8;
  return Math.max(8, Math.round((count / maxCount) * 100));
}
function stopDanmakuRender() {
  if (danmakuFrame) cancelAnimationFrame(danmakuFrame);
  danmakuFrame = 0;
  const canvas = danmakuCanvas.value;
  const context = canvas?.getContext("2d");
  if (canvas && context) context.clearRect(0, 0, canvas.width, canvas.height);
}
function renderDanmakus() {
  const player = videoPlayer.value;
  const canvas = danmakuCanvas.value;
  if (!player || !canvas) return;
  const width = player.clientWidth;
  const height = player.clientHeight;
  if (!width || !height) {
    danmakuFrame = requestAnimationFrame(renderDanmakus);
    return;
  }
  const ratio = window.devicePixelRatio || 1;
  const pixelWidth = Math.round(width * ratio);
  const pixelHeight = Math.round(height * ratio);
  if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
    canvas.width = pixelWidth;
    canvas.height = pixelHeight;
  }
  const context = canvas.getContext("2d");
  if (!context) return;
  context.setTransform(ratio, 0, 0, ratio, 0, 0);
  context.clearRect(0, 0, width, height);
  // 弹幕关闭时只清屏，不绘制
  if (!danmakuOn.value) {
    danmakuFrame = requestAnimationFrame(renderDanmakus);
    return;
  }
  const timeMs = player.currentTime * 1000;
  const fontSize = Math.max(16, Math.min(24, width / 48));
  const lineHeight = fontSize + 10;
  const laneCount = Math.max(1, Math.floor((height * 0.62) / lineHeight));
  // 防挡脸：命中人脸片段时，把落在遮罩带内的轨道剔除，弹幕只在剩余轨道里分配
  const maskSegment = currentMaskSegment(timeMs);
  const maskTop = maskSegment ? maskSegment.maskTop * height - lineHeight * 0.55 : -1;
  const maskBottom = maskSegment ? maskSegment.maskBottom * height + lineHeight * 0.55 : -1;
  const topLanes: number[] = [];
  const bottomLanes: number[] = [];
  for (let lane = 0; lane < laneCount; lane++) {
    const topY = lineHeight / 2 + lane * lineHeight;
    if (!maskSegment || topY < maskTop || topY > maskBottom) topLanes.push(topY);
    const bottomY = height - lineHeight / 2 - lane * lineHeight;
    if (!maskSegment || bottomY < maskTop || bottomY > maskBottom) bottomLanes.push(bottomY);
  }
  // 人脸区域吃掉大部分上部轨道时，把滚动/顶部弹幕扩展到画面下方，避免全部挤在一条线上
  if (maskSegment && topLanes.length < 2) {
    const extendedLanes = Math.max(laneCount, Math.floor((height * 0.86) / lineHeight));
    for (let lane = laneCount; lane < extendedLanes; lane++) {
      const topY = lineHeight / 2 + lane * lineHeight;
      if (topY < maskTop || topY > maskBottom) topLanes.push(topY);
    }
  }
  context.font = `600 ${fontSize}px Microsoft YaHei, sans-serif`;
  context.textBaseline = "middle";
  danmakus.value.forEach((item) => {
    const elapsed = timeMs - item.timeMs;
    const visibleFor = item.mode === "SCROLL" ? 8000 : 4200;
    if (elapsed < 0 || elapsed > visibleFor) return;
    const textWidth = context.measureText(item.content).width;
    const lanes = item.mode === "BOTTOM" ? bottomLanes : topLanes;
    const y = lanes.length
      ? lanes[item.id % lanes.length]
      : lineHeight / 2 + (item.id % laneCount) * lineHeight;
    let x = 0;
    if (item.mode === "SCROLL") {
      x = width - ((width + textWidth) * elapsed) / visibleFor;
    } else {
      x = (width - textWidth) / 2;
    }
    context.lineWidth = 3;
    context.strokeStyle = "rgba(0, 0, 0, .58)";
    context.strokeText(item.content, x, y);
    context.fillStyle = item.color;
    context.fillText(item.content, x, y);
  });
  danmakuFrame = requestAnimationFrame(renderDanmakus);
}
function startDanmakuRender() {
  stopDanmakuRender();
  danmakuFrame = requestAnimationFrame(renderDanmakus);
}
async function sendDanmaku() {
  if (!loggedIn.value) return openAuth();
  if (!danmaku.value.trim()) return;
  if (!selected.value || !videoPlayer.value) return;
  try {
    await axios.post(`/api/videos/${selected.value.id}/danmakus`, {
      content: danmaku.value,
      mode: danmakuMode.value,
      color: danmakuColor.value,
      timeMs: Math.round(videoPlayer.value.currentTime * 1000),
    }, { headers: authHeaders() });
    danmaku.value = "";
    await loadDanmakus(selected.value.id);
    await loadDanmakuHeatmap(selected.value.id);
    notice.value = "弹幕发送成功";
  } catch (error) {
    fail(error);
  }
}
function startReply(item: Comment) {
  if (!loggedIn.value) return openAuth();
  replyTo.value = item;
  replyContent.value = "";
}
function cancelReply() {
  replyTo.value = null;
  replyContent.value = "";
}
function selectVideoFile(file: File | null | undefined) {
  if (!file) return;
  const allowed = ["video/mp4", "video/quicktime", "video/x-matroska"];
  const nameAllowed = /\.(mp4|mov|mkv)$/i.test(file.name);
  if (!allowed.includes(file.type) && !nameAllowed) {
    notice.value = "请选择 MP4、MOV 或 MKV 视频文件";
    return;
  }
  if (file.size > 16 * 1024 * 1024 * 1024) {
    notice.value = "视频大小不能超过 16GB";
    return;
  }
  upload.value.file = file;
  if (!upload.value.title.trim())
    upload.value.title = file.name.replace(/\.[^.]+$/, "").slice(0, 80);
}
function handleVideoDrop(event: DragEvent) {
  isDraggingVideo.value = false;
  selectVideoFile(event.dataTransfer?.files[0]);
}
function selectCoverFile(file: File | null | undefined) {
  if (!file) return;
  const allowed = ["image/jpeg", "image/png", "image/webp"];
  if (!allowed.includes(file.type)) {
    notice.value = "请选择 JPG、PNG 或 WebP 格式的封面";
    return;
  }
  if (file.size > 10 * 1024 * 1024) {
    notice.value = "封面图片不能超过 10MB";
    return;
  }
  if (coverPreview.value) URL.revokeObjectURL(coverPreview.value);
  upload.value.cover = file;
  coverPreview.value = URL.createObjectURL(file);
}
function navigate(page: Page) {
  activePage.value = page;
  selected.value = null;
  // 离开直播间时释放弹幕连接与 HLS 播放器，避免后台占用
  if (page !== "liveRoom") {
    closeLiveRoomSocket();
    destroyLiveHls();
  }
  if (page === "live") {
    loadLiveRooms();
    loadLiveMine();
  }
  if (
    (page === "create" || page === "settings" || page === "history" || page === "favorite" || page === "message" || page === "chat" || page === "fans" || page === "following") &&
    !loggedIn.value
  )
    openAuth();
  if (page === "settings") loadProfile();
  if (page === "history" && loggedIn.value) loadHistory();
  if (page === "favorite" && loggedIn.value) loadFavorites();
  if (page === "message" && loggedIn.value) loadNotifications();
  if (page === "message" && loggedIn.value) loadUnreadNotificationCount();
  if (page === "chat" && loggedIn.value) {
    loadConversations();
    loadUnreadMessageCount();
  }
  if (page === "fans" && loggedIn.value) loadFans();
  if (page === "following" && loggedIn.value) loadFollowingUsers();
  if (page === "recommend") loadVideos();
  if (page === "search") loadSearchResults();
  if (page === "create" && loggedIn.value) {
    loadCreatorVideos();
    loadCreatorDashboard();
  }
}
function showUploadWarning(message: string) {
  uploadSuccessVisible.value = false;
  uploadWarningMessage.value = message;
  uploadWarningVisible.value = true;
}

async function sendUpload() {
  if (!upload.value.file) {
    showUploadWarning("「视频文件」未选择，请先选择要上传的视频");
    return;
  }
  if (!upload.value.cover) {
    showUploadWarning("「视频封面」未上传，请先选择封面图片");
    return;
  }
  if (!upload.value.title.trim()) {
    showUploadWarning("「视频标题」未填写，请填写后再提交");
    return;
  }
  try {
    const file = upload.value.file;
    const CHUNK_SIZE = 5 * 1024 * 1024;
    const totalParts = Math.ceil(file.size / CHUNK_SIZE);
    const isLargeFile = file.size > CHUNK_SIZE;

    if (isLargeFile) {
      const initRes = (
        await axios.post(
          "/api/videos/upload/init",
          null,
          {
            params: { filename: file.name, contentType: file.type || "video/mp4" },
            headers: authHeaders(),
          },
        )
      ).data.data;
      const sessionId = initRes.sessionId;

      for (let i = 0; i < totalParts; i++) {
        const start = i * CHUNK_SIZE;
        const end = Math.min(start + CHUNK_SIZE, file.size);
        const chunk = file.slice(start, end);
        const chunkForm = new FormData();
        chunkForm.append("chunk", chunk, file.name);
        await axios.put("/api/videos/upload/chunk", chunkForm, {
          params: { sessionId, partNumber: i + 1 },
          headers: { ...authHeaders(), "Content-Type": "multipart/form-data" },
        });
        notice.value = `上传中... ${Math.round(((i + 1) / totalParts) * 100)}%`;
      }

      const completeForm = new FormData();
      completeForm.append("title", upload.value.title);
      completeForm.append("description", upload.value.description);
      completeForm.append("categoryId", String(upload.value.categoryId));
      completeForm.append("durationSeconds", String(upload.value.durationSeconds));
      completeForm.append("creationDeclaration", upload.value.creationDeclaration);
      completeForm.append("cover", upload.value.cover);
      notice.value = (
        await axios.post(
          "/api/videos/upload/complete",
          completeForm,
          {
            params: { sessionId },
            headers: { ...authHeaders(), "Content-Type": "multipart/form-data" },
          },
        )
      ).data.data.message;
    } else {
      const form = new FormData();
      form.append("title", upload.value.title);
      form.append("description", upload.value.description);
      form.append("categoryId", String(upload.value.categoryId));
      form.append("durationSeconds", String(upload.value.durationSeconds));
      form.append("creationDeclaration", upload.value.creationDeclaration);
      form.append("cover", upload.value.cover);
      form.append("file", upload.value.file);
      notice.value = (
        await axios.post("/api/videos/upload", form, { headers: authHeaders() })
      ).data.data.message;
    }

    uploadSuccessMessage.value = notice.value || "投稿提交成功";
    uploadWarningVisible.value = false;
    uploadSuccessVisible.value = true;

    if (coverPreview.value) URL.revokeObjectURL(coverPreview.value);
    coverPreview.value = "";
    upload.value = {
      title: "",
      description: "",
      categoryId: 1,
      durationSeconds: 0,
      creationDeclaration: "ORIGINAL",
      cover: null,
      file: null,
    };
    if (videoInput.value) videoInput.value.value = "";
    if (coverInput.value) coverInput.value.value = "";
  } catch (error) {
    fail(error);
  }
}
async function openVideo(video: Video) {
  try {
    selected.value = video;
    selectedFavorited.value = false;
    selectedFollowing.value = false;
    const response = (await axios.get(`/api/videos/${video.id}`)).data.data;
    selected.value = { ...video, ...response.video };
    playUrl.value = response.playUrl;
    // 切换视频后回到「简介」标签
    watchTab.value = "about";
    // 重置播放器状态，避免沿用上一个视频的进度/时长
    currentTime.value = 0;
    duration.value = 0;
    isPlaying.value = false;
    qualityMenuOpen.value = false;
    rateMenuOpen.value = false;
    danmakuSettingsOpen.value = false;
    danmakuMaskSegments.value = [];
    danmakuMaskStatus.value = "NONE";
    danmakuHeat.value = { bucketSeconds: 10, durationMs: 0, totalCount: 0, maxCount: 0, peakMs: 0, buckets: [] };
    // 先用后端返回的转码档位渲染清晰度菜单，hls.js 解析完成后再用它校正
    serverHeights.value = response.playUrl?.includes(".m3u8") ? renditionHeights(response.transcode?.renditions ?? []) : [];
    qualityLevels.value = serverHeights.value.map((height) => ({ height, label: qualityLabel(height) }));
    selectedQuality.value = null;
    // 登录用户上次的播放进度，播放器元数据就绪后自动续播
    resumeSeconds = 0;
    if (loggedIn.value) {
      const progress = (await axios.get(`/api/videos/${video.id}/progress`, { headers: authHeaders() })).data.data;
      resumeSeconds = Number(progress?.progressSeconds ?? 0);
    }
    comments.value = (
      await axios.get(`/api/videos/${video.id}/comments`)
    ).data.data;
    await loadDanmakus(video.id);
    // 遮罩首次查询会触发后端抽帧分析，不阻塞播放器启动
    loadDanmakuMask(video.id);
    await loadDanmakuHeatmap(video.id);
    await nextTick();
    attachVideoSource();
    startDanmakuRender();
    cancelReply();
    if (loggedIn.value) {
      await axios
        .post(`/api/videos/${video.id}/history`, {}, { headers: authHeaders() })
        .catch(() => undefined);
      selectedFavorited.value = (await axios.get(`/api/videos/${video.id}/favorite-status`, { headers: authHeaders() })).data.data.favorited;
      const authorId = selected.value?.authorId;
      if (authorId) {
        selectedFollowing.value = (await axios.get(`/api/users/${authorId}/follow-status`, { headers: authHeaders() })).data.data.following;
      }
    }
  } catch (error) {
    fail(error);
  }
}
async function like() {
  if (!selected.value || !loggedIn.value) return openAuth();
  try {
    const data = (
      await axios.post(
        `/api/videos/${selected.value.id}/like`,
        {},
        { headers: authHeaders() },
      )
    ).data.data;
    selected.value.likeCount += data.liked ? 1 : -1;
  } catch (error) {
    fail(error);
  }
}
async function toggleFavorite() {
  if (!selected.value || !loggedIn.value) return openAuth();
  try {
    const data = (await axios.post(`/api/videos/${selected.value.id}/favorite`, {}, { headers: authHeaders() })).data.data;
    selectedFavorited.value = data.favorited;
    if (activePage.value === "favorite") loadFavorites();
    notice.value = data.favorited ? "已收藏" : "已取消收藏";
  } catch (error) {
    fail(error);
  }
}
async function sendComment() {
  if (!selected.value || !comment.value.trim()) return;
  if (!loggedIn.value) return openAuth();
  try {
    await axios.post(
      `/api/videos/${selected.value.id}/comments`,
      { content: comment.value },
      { headers: authHeaders() },
    );
    comment.value = "";
    selected.value.commentCount += 1;
    comments.value = (
      await axios.get(`/api/videos/${selected.value!.id}/comments`)
    ).data.data;
  } catch (error) {
    fail(error);
  }
}
async function sendReply() {
  if (!selected.value || !replyTo.value || !replyContent.value.trim()) return;
  if (!loggedIn.value) return openAuth();
  try {
    await axios.post(`/api/videos/${selected.value.id}/comments/${replyTo.value.id}/replies`, { content: replyContent.value }, { headers: authHeaders() });
    selected.value.commentCount += 1;
    cancelReply();
    comments.value = (await axios.get(`/api/videos/${selected.value.id}/comments`)).data.data;
  } catch (error) {
    fail(error);
  }
}
const liveStatusLabel = (status?: string) =>
  status === "LIVING" ? "直播中" : status === "READY" ? "待开播" : status === "ENDED" ? "已结束" : "";

function scrollToLiveStudio() {
  liveStudioRef.value?.scrollIntoView({ behavior: "smooth", block: "start" });
}

async function loadLiveRooms() {
  liveLoading.value = true;
  try {
    liveRooms.value = (await axios.get("/api/live/rooms")).data.data;
  } catch (error) {
    fail(error);
  } finally {
    liveLoading.value = false;
  }
}

async function loadLiveMine() {
  if (!loggedIn.value) {
    liveMineRoom.value = null;
    return;
  }
  try {
    const data = (await axios.get("/api/live/rooms/mine", { headers: authHeaders() })).data.data;
    liveMineRoom.value = data.room || null;
    if (liveMineRoom.value && !liveTitle.value) liveTitle.value = liveMineRoom.value.title;
  } catch (error) {
    fail(error);
  }
}

function selectLiveCover(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!file.type.startsWith("image/")) {
    notice.value = "封面仅支持图片文件";
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    notice.value = "封面图片不能超过 5MB";
    return;
  }
  if (liveCoverPreview.value) URL.revokeObjectURL(liveCoverPreview.value);
  liveCover.value = file;
  liveCoverPreview.value = URL.createObjectURL(file);
}

async function submitLiveRoom() {
  if (!loggedIn.value) return openAuth();
  if (!liveTitle.value.trim()) {
    notice.value = "请填写直播标题";
    return;
  }
  liveCreating.value = true;
  try {
    const form = new FormData();
    form.append("title", liveTitle.value.trim());
    if (liveCover.value) form.append("cover", liveCover.value);
    const data = (await axios.post("/api/live/rooms", form, {
      headers: { ...authHeaders(), "Content-Type": "multipart/form-data" },
    })).data.data;
    liveMineRoom.value = data.room;
    liveCover.value = null;
    if (liveCoverPreview.value) {
      URL.revokeObjectURL(liveCoverPreview.value);
      liveCoverPreview.value = "";
    }
    notice.value = "直播间已就绪，按下方地址推流即可开播";
    await loadLiveRooms();
  } catch (error) {
    fail(error);
  } finally {
    liveCreating.value = false;
  }
}

async function copyLivePushUrl() {
  const url = liveMineRoom.value?.pushUrl;
  if (!url) return;
  try {
    await navigator.clipboard.writeText(url);
    liveCopied.value = true;
    window.setTimeout(() => (liveCopied.value = false), 1800);
  } catch {
    notice.value = `推流地址：${url}`;
  }
}

async function refreshLiveMine() {
  await loadLiveMine();
  await loadLiveRooms();
  notice.value = "直播间状态已刷新";
}

async function startLiveRoom() {
  const roomId = liveMineRoom.value?.id;
  if (!roomId) return;
  try {
    const data = (await axios.post(`/api/live/rooms/${roomId}/start`, {}, { headers: authHeaders() })).data.data;
    liveMineRoom.value = data.room;
    notice.value = "已标记为直播中，可以开始推流了";
    await loadLiveRooms();
  } catch (error) {
    fail(error);
  }
}

async function stopLiveRoom() {
  const roomId = liveCurrent.value?.id ?? liveMineRoom.value?.id;
  if (!roomId) return;
  try {
    const data = (await axios.post(`/api/live/rooms/${roomId}/stop`, {}, { headers: authHeaders() })).data.data;
    if (liveMineRoom.value && liveMineRoom.value.id === roomId) liveMineRoom.value = data.room;
    if (liveCurrent.value && liveCurrent.value.id === roomId) {
      liveCurrent.value = { ...data.room, playUrl: null };
      destroyLiveHls();
      liveStatusTip.value = "直播已结束";
    }
    notice.value = "已下播";
    await loadLiveRooms();
  } catch (error) {
    fail(error);
  }
}

async function openLiveRoom(roomId: number) {
  closeLiveRoomSocket();
  destroyLiveHls();
  activePage.value = "liveRoom";
  selected.value = null;
  liveRoomLoading.value = true;
  liveStatusTip.value = "";
  liveMessages.value = [];
  try {
    const data = (await axios.get(`/api/live/rooms/${roomId}`, { headers: authHeaders() })).data.data;
    liveCurrent.value = data.room;
    liveMessages.value = data.messages || [];
    liveViewerCount.value = data.room?.viewerCount || 0;
    connectLiveSocket(roomId);
    await nextTick();
    attachLiveHls();
    scrollLiveMessages();
  } catch (error) {
    fail(error);
  } finally {
    liveRoomLoading.value = false;
  }
}

function destroyLiveHls() {
  if (liveHls) {
    liveHls.destroy();
    liveHls = null;
  }
}

// 直播流交给 hls.js；Safari 原生支持 HLS 时直接使用 video 标签
function attachLiveHls() {
  const el = livePlayer.value;
  const url = liveCurrent.value?.playUrl;
  if (!el || !url) return;
  if (Hls.isSupported()) {
    liveHls = new Hls({ liveDurationInfinity: true, lowLatencyMode: true });
    liveHls.loadSource(url);
    liveHls.attachMedia(el);
    liveHls.on(Hls.Events.ERROR, (_event, data) => {
      if (!data.fatal) return;
      if (data.type === Hls.ErrorTypes.NETWORK_ERROR) liveHls?.startLoad();
      else if (data.type === Hls.ErrorTypes.MEDIA_ERROR) liveHls?.recoverMediaError();
    });
  } else {
    el.src = url;
  }
}

function liveSocketUrl(roomId: number) {
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}/ws/live?roomId=${roomId}`;
}

function connectLiveSocket(roomId: number) {
  closeLiveRoomSocket();
  const socket = new WebSocket(liveSocketUrl(roomId));
  liveSocket = socket;
  socket.onopen = () => {
    liveSocketRetry = 0;
    window.clearInterval(liveHeartbeatTimer);
    liveHeartbeatTimer = window.setInterval(() => {
      if (socket.readyState === WebSocket.OPEN) socket.send("ping");
    }, 25000);
  };
  socket.onmessage = (event) => {
    if (event.data === "pong") return;
    try {
      handleLiveEvent(JSON.parse(event.data));
    } catch {
      // 忽略无法解析的推送内容
    }
  };
  socket.onclose = () => {
    window.clearInterval(liveHeartbeatTimer);
    liveHeartbeatTimer = 0;
    if (liveSocket === socket) liveSocket = null;
    if (activePage.value === "liveRoom" && liveCurrent.value?.id === roomId) scheduleLiveReconnect(roomId);
  };
  socket.onerror = () => socket.close();
}

function scheduleLiveReconnect(roomId: number) {
  if (liveReconnectTimer) return;
  const delay = Math.min(15000, 1000 * 2 ** liveSocketRetry);
  liveSocketRetry = Math.min(liveSocketRetry + 1, 4);
  liveReconnectTimer = window.setTimeout(() => {
    liveReconnectTimer = 0;
    if (activePage.value === "liveRoom" && liveCurrent.value?.id === roomId) connectLiveSocket(roomId);
  }, delay);
}

function closeLiveRoomSocket() {
  window.clearTimeout(liveReconnectTimer);
  liveReconnectTimer = 0;
  window.clearInterval(liveHeartbeatTimer);
  liveHeartbeatTimer = 0;
  liveSocketRetry = 0;
  if (liveSocket) {
    const socket = liveSocket;
    liveSocket = null;
    socket.onclose = null;
    socket.close();
  }
}

function handleLiveEvent(event: any) {
  if (event.kind === "CONNECTED" || event.kind === "LIVE_VIEWER") {
    if (typeof event.viewerCount === "number") liveViewerCount.value = event.viewerCount;
    return;
  }
  if (event.kind === "LIVE_DANMAKU" && event.message) {
    liveMessages.value = [...liveMessages.value.slice(-199), event.message];
    if (liveCurrent.value) liveCurrent.value.danmakuCount = (liveCurrent.value.danmakuCount || 0) + 1;
    scrollLiveMessages();
    return;
  }
  if (event.kind === "LIVE_STATUS" && liveCurrent.value && event.roomId === liveCurrent.value.id) {
    liveCurrent.value.status = event.status;
    if (event.status === "LIVING" && !liveCurrent.value.playUrl) {
      liveCurrent.value.playUrl = `/api/live/rooms/${liveCurrent.value.id}/hls/index.m3u8`;
      liveStatusTip.value = "主播已开播";
      nextTick(() => attachLiveHls());
    } else if (event.status === "ENDED") {
      liveCurrent.value.playUrl = null;
      destroyLiveHls();
      liveStatusTip.value = "直播已结束";
    }
  }
}

async function sendLiveMessage() {
  if (!loggedIn.value) return openAuth();
  if (!liveCurrent.value) return;
  const content = liveInput.value.trim();
  if (!content) return;
  try {
    await axios.post(`/api/live/rooms/${liveCurrent.value.id}/messages`, { content }, { headers: authHeaders() });
    liveInput.value = "";
  } catch (error) {
    fail(error);
  }
}

function scrollLiveMessages() {
  nextTick(() => {
    const el = liveMessageList.value;
    if (el) el.scrollTop = el.scrollHeight;
  });
}

onMounted(() => {
  loadCategories();
  loadVideos();
  loadHotKeywords();
  progressPoll = window.setInterval(reportPlayProgress, 15000);
  // 首页轮播自动切换；停留其他页面时不做无意义的重渲染判断
  heroTimer = window.setInterval(() => {
    if (activePage.value === "recommend" && heroVideos.value.length > 1) heroNext();
  }, 6000);
  if (loggedIn.value) {
    loadProfile();
    loadUnreadNotificationCount();
    loadUnreadMessageCount();
    connectRealtime();
  }
});
onBeforeUnmount(() => {
  stopDanmakuRender();
  destroyHls();
  disconnectRealtime();
  closeLiveRoomSocket();
  destroyLiveHls();
  window.clearInterval(progressPoll);
  window.clearInterval(heroTimer);
});
</script>

<template>
  <div class="app-shell" :class="{ compact: sidebarCollapsed, dark }">
    <aside class="sidebar">
      <div class="brand">
        <span class="brand-mark">K</span><strong>Kilikili</strong>
      </div>
      <nav>
        <button
          v-for="item in navItems"
          :key="item.key"
          class="nav-item"
          :class="{ active: activePage === item.key }"
          :title="item.label"
          @click="navigate(item.key)"
        >
          <span v-if="item.key === 'recommend'" class="nav-icon nav-monitor-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
              <rect x="2.5" y="3.5" width="19" height="13.5" rx="1.7" />
              <path d="M8.5 21h7M12 17v4" />
            </svg>
          </span>
          <span v-else-if="item.key === 'create'" class="nav-icon nav-create-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="M13.5 5H4.5A1.5 1.5 0 0 0 3 6.5v13A1.5 1.5 0 0 0 4.5 21h13a1.5 1.5 0 0 0 1.5-1.5v-8" />
              <path d="m18.2 3.8 2 2-8.8 8.8-3.3 1.3 1.3-3.3 8.8-8.8Z" />
            </svg>
          </span>
          <span v-else-if="item.key === 'search'" class="nav-icon nav-search-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round">
              <circle cx="10.8" cy="10.8" r="7.5" />
              <path d="m16.4 16.4 4.2 4.2" />
            </svg>
          </span>
          <span v-else-if="item.key === 'history'" class="nav-icon nav-history-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="9" />
              <path d="M12 7v5l3.5 2" />
            </svg>
          </span>
          <span v-else-if="item.key === 'following'" class="nav-icon nav-following-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="7.5" r="4" />
              <path d="M4.5 21v-2.1c0-3.1 2.8-5.4 7.5-5.4s7.5 2.3 7.5 5.4V21" />
            </svg>
          </span>
          <span v-else-if="item.key === 'fans'" class="nav-icon nav-fans-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="currentColor">
              <circle cx="12" cy="7.2" r="4.4" />
              <path d="M4.1 21v-1.4c0-3.55 3.35-6.15 7.9-6.15s7.9 2.6 7.9 6.15V21H4.1Z" />
            </svg>
          </span>
          <span v-else-if="item.key === 'settings'" class="nav-icon nav-settings-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round">
              <path d="m12 3 8 4.5v9L12 21l-8-4.5v-9L12 3Z" />
              <circle cx="12" cy="12" r="3" />
            </svg>
          </span>
          <span v-else class="nav-icon">{{ item.icon }}</span>
          <span class="nav-label">{{ item.label }}</span>
          <i v-if="item.key === 'message' && unreadNotificationCount > 0" class="nav-badge">{{ unreadNotificationCount > 99 ? "99+" : unreadNotificationCount }}</i>
          <i v-else-if="item.key === 'chat' && unreadMessageCount > 0" class="nav-badge">{{ unreadMessageCount > 99 ? "99+" : unreadMessageCount }}</i>
        </button>
      </nav>
      <div class="account-area">
        <template v-if="loggedIn"
          ><span class="avatar small">{{ avatarText }}</span>
          <div class="account-name">
            <b>{{ user?.nickname }}</b
            ><small>Lv.1</small>
          </div>
          <button class="logout" title="退出登录" @click="logout">
            ↪
          </button></template
        >
        <button v-else class="login-entry" @click="openAuth()">
          <span class="avatar small">K</span><span>登录 / 注册</span>
        </button>
      </div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <button
          class="menu-button"
          title="收起菜单"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          ☰</button
        ><div class="top-actions">
          <button title="切换主题" @click="dark = !dark">☾</button
          ><button class="bell" title="通知" @click="navigate('message')">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
              <path d="M18 9a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
              <path d="M10 21h4" />
            </svg><i v-if="unreadNotificationCount > 0"></i>
          </button
          ><button
            class="avatar profile"
            @click="loggedIn ? navigate('settings') : openAuth()"
          >
            <img v-if="loggedIn && profile.avatar" :src="profile.avatar" alt="我的头像" />
            <span v-else>{{ avatarText }}</span>
          </button>
        </div>
      </header>
      <main class="content" @scroll="onContentScroll">
        <template v-if="selected">
          <section class="watch-layout" :class="{ theater: theaterMode }">
            <div class="watch-main">
              <section class="watch-player-card">
                <div ref="stageRef" class="video-stage" @click="onStageClick" @dblclick="toggleFullscreen">
                  <video
                    ref="videoPlayer"
                    v-if="playUrl"
                    autoplay
                    @loadedmetadata="onVideoMeta"
                    @timeupdate="onTimeUpdate"
                    @seeked="startDanmakuRender"
                    @play="isPlaying = true"
                    @pause="isPlaying = false"
                    @volumechange="muted = videoPlayer?.muted ?? false"
                  ></video>
                  <canvas ref="danmakuCanvas" class="danmaku-canvas"></canvas>
                  <div class="player-bar">
                    <div
                      v-if="danmakuHeat.buckets.length"
                      class="player-heatmap"
                      :title="`弹幕热度：共 ${danmakuHeat.totalCount} 条，峰值 ${formatClock(danmakuHeat.peakMs / 1000)}`"
                    >
                      <span
                        v-for="bucket in danmakuHeat.buckets"
                        :key="bucket.startMs"
                        :style="{
                          left: heatLeft(bucket, danmakuHeat.durationMs) + '%',
                          width: heatWidth(bucket, danmakuHeat.durationMs) + '%',
                          height: heatHeight(bucket.count, danmakuHeat.maxCount) + '%',
                        }"
                      ></span>
                      <i
                        v-if="danmakuHeat.peakMs"
                        class="player-heatmap-peak"
                        :style="{ left: heatLeft({ startMs: danmakuHeat.peakMs }, danmakuHeat.durationMs) + '%' }"
                      ></i>
                    </div>
                    <div
                      class="player-progress"
                      @pointerdown="startSeek"
                      @pointermove="moveSeek"
                      @pointerup="endSeek"
                      @pointercancel="endSeek"
                    >
                      <div class="player-progress-played" :style="{ width: progressPercent + '%' }"></div>
                    </div>
                    <div class="player-bar-inner">
                      <button class="pb-icon" :title="isPlaying ? '暂停' : '播放'" @click="togglePlay">{{ isPlaying ? "❚❚" : "▶" }}</button>
                      <span class="pb-time">{{ formatClock(currentTime) }} / {{ formatClock(duration) }}</span>
                      <button class="pb-icon pb-danmaku-toggle" :class="{ off: !danmakuOn }" :title="danmakuOn ? '关闭弹幕' : '开启弹幕'" @click="danmakuOn = !danmakuOn">弹</button>
                      <div class="pb-menu-wrap">
                        <button class="pb-icon" title="弹幕设置" @click="danmakuSettingsOpen = !danmakuSettingsOpen">⚙</button>
                        <div v-if="danmakuSettingsOpen" class="pb-popover">
                          <div class="pb-danmaku-modes">
                            <button :class="{ active: danmakuMode === 'SCROLL' }" @click="danmakuMode = 'SCROLL'">滚动</button>
                            <button :class="{ active: danmakuMode === 'TOP' }" @click="danmakuMode = 'TOP'">顶部</button>
                            <button :class="{ active: danmakuMode === 'BOTTOM' }" @click="danmakuMode = 'BOTTOM'">底部</button>
                            <button
                              :class="{ active: faceMaskOn }"
                              :title="danmakuMaskStatus === 'RUNNING' ? '正在分析人脸区域…' : '弹幕自动避开人脸区域'"
                              @click="faceMaskOn = !faceMaskOn"
                            >防挡脸{{ danmakuMaskStatus === "RUNNING" ? "…" : "" }}</button>
                            <label class="danmaku-color" title="自定义颜色"><input v-model="danmakuColor" type="color" aria-label="弹幕颜色"></label>
                          </div>
                        </div>
                      </div>
                      <div class="pb-danmaku-input">
                        <input
                          v-model="danmaku"
                          maxlength="1000"
                          :placeholder="loggedIn ? '发一条弹幕吧…' : '登录后可发送弹幕'"
                          @focus="loggedIn || openAuth()"
                          @keyup.enter="sendDanmaku"
                        />
                        <button @click="sendDanmaku">发送</button>
                      </div>
                      <div class="pb-right">
                        <div class="pb-menu-wrap">
                          <button class="pb-text" @click="qualityMenuOpen = !qualityMenuOpen">{{ qualityLevels.length ? qualityBadgeText : "原画" }}</button>
                          <div v-if="qualityMenuOpen" class="quality-menu pb-menu">
                            <template v-if="qualityLevels.length">
                              <button
                                v-for="quality in qualityLevels"
                                :key="quality.height"
                                :class="{ active: selectedQuality === quality.height }"
                                @click="pickQuality(quality.height)"
                              >{{ quality.label }}</button
                              ><button :class="{ active: selectedQuality === null }" @click="pickQuality(null)">自动</button>
                            </template>
                            <button v-else disabled>原画（转码完成后可切换）</button>
                          </div>
                        </div>
                        <div class="pb-menu-wrap">
                          <button class="pb-text" @click="rateMenuOpen = !rateMenuOpen">{{ playbackRate === 1 ? "倍速" : playbackRate + "x" }}</button>
                          <div v-if="rateMenuOpen" class="pb-menu">
                            <button v-for="rate in [2, 1.5, 1.25, 1, 0.75, 0.5]" :key="rate" :class="{ active: playbackRate === rate }" @click="pickRate(rate)">{{ rate === 1 ? "1x 正常" : rate + "x" }}</button>
                          </div>
                        </div>
                        <button class="pb-icon" :title="muted ? '取消静音' : '静音'" @click="toggleMute">{{ muted ? "🔇" : "🔊" }}</button>
                        <input class="pb-volume" type="range" min="0" max="1" step="0.05" :value="muted ? 0 : volume" aria-label="音量" @input="onVolumeInput" />
                        <button class="pb-icon" :title="theaterMode ? '退出宽屏' : '宽屏模式'" @click="theaterMode = !theaterMode">{{ theaterMode ? "❮" : "❯" }}</button>
                        <button class="pb-icon" title="全屏" @click="toggleFullscreen">⛶</button>
                      </div>
                    </div>
                  </div>
                </div>
              </section>
            </div>
            <aside class="watch-aside">
              <nav class="watch-tabs">
                <button :class="{ active: watchTab === 'about' }" @click="watchTab = 'about'">简介</button>
                <button :class="{ active: watchTab === 'comments' }" @click="watchTab = 'comments'">评论 <em>{{ formatCount(selected.commentCount) }}</em></button>
              </nav>
              <template v-if="watchTab === 'about'">
                  <section class="watch-detail-card">
                    <div class="watch-up-row">
                      <div class="watch-up-avatar" title="查看个人主页" @click="selected.authorId && openSpace(selected.authorId)">
                        <img v-if="selected.authorAvatar" :src="selected.authorAvatar" :alt="selected.authorName" />
                        <span v-else>{{ selected.authorName.slice(0, 1).toUpperCase() }}</span>
                      </div>
                      <div class="watch-up-info">
                        <h2 class="clickable-name" @click="selected.authorId && openSpace(selected.authorId)">{{ selected.authorName }}</h2>
                        <p>{{ formatCount(selected.likeCount) }} 获赞 · {{ formatCount(selected.viewCount) }} 播放</p>
                      </div>
                      <button class="follow-button" :class="{ followed: isFollowingSelected }" @click="toggleAuthorFollow">
                        {{ isFollowingSelected ? "✓ 已关注" : "+ 关注" }}
                      </button>
                    </div>
                    <h1>{{ selected.title }}</h1>
                    <div class="watch-bar">
                      <p class="watch-meta">
                        ◉ {{ formatCount(selected.viewCount) }} 播放　▣
                        {{ formatCount(selected.commentCount) }} 弹幕　{{
                        formatDate(selected.createdAt || "")
                      }}
                      </p>
                      <div class="watch-actions">
                        <button class="watch-like" @click="like">
                          ♥ {{ selected.likeCount }}
                        </button>
                        <button @click="watchAction('投币')">◉ 投币</button>
                        <button :class="{ active: selectedFavorited }" @click="toggleFavorite">{{ selectedFavorited ? '★ 已收藏' : '☆ 收藏' }}</button>
                      </div>
                    </div>
                    <p v-if="selected.description" class="watch-description">
                      {{ selected.description }}
                    </p>
                  </section>
                <div class="related-section">
                  <h2>接下来播放</h2>
    <div class="related-list">
                    <article
                      v-for="video in relatedVideos"
                      :key="video.id"
                      class="related-video"
                      @click="openVideo(video)"
                    >
                      <div class="related-cover">
                        <img
                          v-if="video.coverUrl"
                          :src="video.coverUrl"
                          :alt="video.title"
                        /><span v-else>▶</span
                        ><em>{{ formatDuration(video.durationSeconds) }}</em>
                      </div>
                      <div class="related-meta">
                        <h3>{{ video.title }}</h3>
                        <p class="related-up"><i>UP</i>{{ video.authorName }}</p>
                        <p class="related-stats">
                          <span
                            ><svg viewBox="0 0 16 16" aria-hidden="true"
                              ><circle cx="8" cy="8" r="6.4" fill="none" stroke="currentColor" stroke-width="1.2" /><path
                                d="M6.6 5.3 10.4 8l-3.8 2.7z"
                                fill="currentColor" /></svg
                            >{{ formatCount(video.viewCount) }}</span
                          >
                          <span
                            ><svg viewBox="0 0 16 16" aria-hidden="true"
                              ><path
                                d="M2.4 3.2h11.2v7.1H7.1L4.4 12.6V10.3H2.4z"
                                fill="none"
                                stroke="currentColor"
                                stroke-width="1.2"
                                stroke-linejoin="round" /></svg
                            >{{ formatCount(video.commentCount) }}</span
                          >
                        </p>
                      </div>
                    </article>
                    <p v-if="!relatedVideos.length" class="related-empty">
                      暂无相关推荐
                    </p>
                  </div>
                </div>
              </template>
              <template v-else>
                  <section class="watch-comment-panel">
                    <header class="comment-header">
                      <h2>评论</h2>
                      <div class="comment-sort">
                        <button :class="{ active: commentSort === 'hot' }" @click="commentSort = 'hot'">最热</button>
                        <button :class="{ active: commentSort === 'latest' }" @click="commentSort = 'latest'">最新</button>
                      </div>
                    </header>
                    <div v-if="loggedIn" class="comment-composer">
                      <div class="comment-avatar">
                        <img v-if="profile.avatar" :src="profile.avatar" alt="我的头像"><span v-else>{{ avatarText }}</span>
                      </div>
                      <div class="comment-editor">
                        <textarea v-model="comment" maxlength="500" placeholder="发一条友善的评论吧…"></textarea>
                        <small>{{ comment.length }} / 500</small>
                        <button @click="sendComment">发表评论</button>
                      </div>
                    </div>
                    <button v-else class="comment-login" @click="openAuth()">登录后参与评论</button>
                    <div v-if="false" class="comment-feed">
                      <article v-for="item in sortedComments" :key="item.id" class="comment-item">
                        <div class="comment-avatar">
                          <img v-if="item.avatar" :src="item.avatar || undefined" :alt="item.nickname"><span v-else>{{ item.nickname.slice(0, 1) }}</span>
                        </div>
                        <div class="comment-content">
                          <p class="comment-user">{{ item.nickname }} <small>{{ formatHistoryTime(item.createdAt || '') }}</small></p>
                          <p class="comment-text">{{ item.content }}</p>
                          <div class="comment-actions"><button @click="watchAction('评论点赞')">♡ 赞</button><button @click="watchAction('评论回复')">▣ 回复</button></div>
                        </div>
                      </article>
                      <p v-if="!sortedComments.length" class="comment-empty">暂无评论，来留下第一条评论吧</p>
                    </div>
                    <div class="comment-thread">
                      <article v-for="item in topLevelComments" :key="item.id" class="comment-item">
                        <div class="comment-avatar">
                          <img v-if="item.avatar" :src="item.avatar || undefined" :alt="item.nickname"><span v-else>{{ item.nickname.slice(0, 1) }}</span>
                        </div>
                        <div class="comment-content">
                          <p class="comment-user">{{ item.nickname }} <small>{{ formatHistoryTime(item.createdAt || '') }}</small></p>
                          <p class="comment-text">{{ item.content }}</p>
                          <div class="comment-actions">
                            <button @click="watchAction('评论点赞')">♡ 赞</button>
                            <button class="reply-trigger" @click="startReply(item)"><span class="reply-icon"></span>回复</button>
                          </div>
                          <div v-if="repliesFor(item.id).length" class="reply-list">
                            <article v-for="reply in repliesFor(item.id)" :key="reply.id" class="reply-item">
                              <div class="comment-avatar small"><img v-if="reply.avatar" :src="reply.avatar || undefined" :alt="reply.nickname"><span v-else>{{ reply.nickname.slice(0, 1) }}</span></div>
                              <div class="comment-content"><p class="comment-user">{{ reply.nickname }} <small>{{ formatHistoryTime(reply.createdAt || '') }}</small></p><p class="comment-text">{{ reply.content }}</p></div>
                            </article>
                          </div>
                          <div v-if="replyTo?.id === item.id" class="reply-editor">
                            <textarea v-model="replyContent" maxlength="500" :placeholder="`回复 @${item.nickname}`" @keyup.ctrl.enter="sendReply"></textarea>
                            <div><small>{{ replyContent.length }} / 500</small><button @click="cancelReply">取消</button><button class="primary-reply" @click="sendReply">回复</button></div>
                          </div>
                        </div>
                      </article>
                      <p v-if="!topLevelComments.length" class="comment-empty">暂无评论，来留下第一条评论吧</p>
                    </div>
                  </section>
              </template>
            </aside>
          </section>
          <div v-if="selected && false" class="legacy-watch-content">
            <button class="back-link" @click="selected = null">
              ← 返回 {{ pageTitle[activePage] }}
            </button>
            <section class="player-panel">
              <video v-if="playUrl" :src="playUrl" controls autoplay></video>
              <h1>{{ selected?.title }}</h1>
              <p class="muted">
                UP：{{ selected?.authorName }} · {{ selected?.viewCount }} 播放
              </p>
              <p>{{ selected?.description }}</p>
              <button class="like-button" @click="like">
                ♥ 点赞 {{ selected?.likeCount }}
              </button>
              <h3>评论（{{ comments.length }}）</h3>
              <div v-if="loggedIn" class="comment-box">
                <input
                  v-model="comment"
                  maxlength="1000"
                  placeholder="说点什么..."
                  @keyup.enter="sendComment"
                /><button @click="sendComment">发布</button>
              </div>
              <p v-for="item in comments" :key="item.id" class="comment">
                <b>{{ item.nickname }}</b
                >：{{ item.content }}
              </p>
            </section>
          </div></template
        >
        <template v-else
          ><h1 v-if="activePage !== 'history' && activePage !== 'message'">{{ pageTitle[activePage] }}</h1>
          <section
            v-if="activePage === 'create' && loggedIn"
            class="creator-center"
          >
            <div class="creator-tabs">
              <button
                :class="{ active: creatorTab === 'manage' }"
                @click="
                  creatorTab = 'manage';
                  loadCreatorVideos();
                "
              >
                ▣ 视频管理</button
              ><button
                :class="{ active: creatorTab === 'upload' }"
                @click="creatorTab = 'upload'"
              >
                ↥ 上传视频</button
              ><button
                :class="{ active: creatorTab === 'stats' }"
                @click="
                  creatorTab = 'stats';
                  loadCreatorDashboard();
                "
              >
                ▥ 数据看板
              </button>
            </div>
            <template v-if="creatorTab === 'manage'"
              ><div class="creator-filter">
                <input
                  v-model="creatorKeyword"
                  placeholder="搜索视频名称"
                  @keyup.enter="loadCreatorVideos"
                /><select v-model="creatorStatus">
                  <option value="">状态</option>
                  <option value="PUBLISHED">已发布</option>
                  <option value="PENDING_REVIEW">待审核</option>
                  <option value="REJECTED">已驳回</option>
                  <option value="OFFLINE">已下架</option></select
                ><button @click="loadCreatorVideos">搜索</button>
              </div>
              <div class="creator-table">
                <table>
                  <thead>
                    <tr>
                      <th>封面</th>
                      <th>视频名称</th>
                      <th>状态</th>
                      <th>播放</th>
                      <th>点赞</th>
                      <th>发布时间</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="video in creatorVideos" :key="video.id">
                      <td>
                        <div class="table-cover">
                          <img
                            v-if="video.coverUrl"
                            :src="video.coverUrl"
                            :alt="video.title"
                          /><span v-else>▶</span>
                        </div>
                      </td>
                      <td class="title-cell">{{ video.title }}</td>
                      <td>
                        <em class="status" :class="video.status">{{
                          video.status
                        }}</em>
                      </td>
                      <td>{{ video.viewCount }}</td>
                      <td>{{ video.likeCount }}</td>
                      <td>{{ formatDate(video.createdAt) }}</td>
                      <td class="table-actions">
                        <button @click="creatorAction('编辑')">编辑</button
                        ><button @click="creatorAction('发布设置')">
                          发布设置</button
                        ><button class="danger" @click="creatorAction('删除')">
                          删除
                        </button>
                      </td>
                    </tr>
                    <tr v-if="!creatorVideos.length">
                      <td colspan="7" class="no-data">暂无投稿视频</td>
                    </tr>
                  </tbody>
                </table>
              </div>
              <div class="table-pager">
                <button>‹</button><b>1</b><button>›</button>
              </div></template
            >
            <section
              v-else-if="creatorTab === 'upload'"
              class="upload-workspace"
            >
              <div class="upload-guide">
                <article>
                  <i>◇</i>
                  <div>
                    <b>视频大小</b>
                    <p>
                      视频大小16G以内，时长10小时以内<br />粉丝数 ≥
                      1000，可自动解锁64G超大文件 ⓘ
                    </p>
                  </div>
                </article>
                <article>
                  <i>⬡</i>
                  <div>
                    <b>视频格式</b>
                    <p>
                      推荐上传 MP4/MOV/MKV 格式，转码<br />更快，过审更顺利～ ⓘ
                    </p>
                  </div>
                </article>
                <article>
                  <i>◧</i>
                  <div>
                    <b>视频分辨率</b>
                    <p>推荐分辨率：1080P、4K，高分辨率<br />更清晰流畅～ ⓘ</p>
                  </div>
                </article>
              </div>
              <input
                ref="videoInput"
                class="video-file-input"
                type="file"
                accept="video/mp4,video/quicktime,video/x-matroska,.mp4,.mov,.mkv"
                @change="
                  selectVideoFile(
                    ($event.target as HTMLInputElement).files?.[0],
                  )
                "
              />
              <div
                v-if="!upload.file"
                class="video-dropzone"
                :class="{ dragging: isDraggingVideo }"
                @dragenter.prevent="isDraggingVideo = true"
                @dragover.prevent="isDraggingVideo = true"
                @dragleave.prevent="isDraggingVideo = false"
                @drop.prevent="handleVideoDrop"
              >
                <div class="cloud-icon">☁<span>↑</span></div>
                <p>点击上传或将视频拖拽到此区域</p>
                <button @click="videoInput?.click()">上传视频</button>
              </div>
              <section v-else class="upload-config">
                <div class="selected-video upload-file-summary">
                  <span>✓</span>
                  <b>{{ upload.file.name }}</b>
                  <small
                    >{{ (upload.file.size / 1024 / 1024).toFixed(2) }} MB</small
                  >
                  <button
                    @click="
                      upload.file = null;
                      upload.cover = null;
                      coverPreview = '';
                    "
                  >
                    重新选择
                  </button>
                </div>
                <div class="config-row cover-config-row">
                  <label><i>*</i>封面</label>
                  <div>
                    <button
                      class="cover-picker"
                      :class="{ 'has-cover': coverPreview }"
                      @click="coverInput?.click()"
                    >
                      <img
                        v-if="coverPreview"
                        :src="coverPreview"
                        alt="视频封面预览"
                      />
                      <span v-else>＋ 添加封面</span>
                    </button>
                    <input
                      ref="coverInput"
                      class="cover-file-input"
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      @change="
                        selectCoverFile(
                          ($event.target as HTMLInputElement).files?.[0],
                        )
                      "
                    />
                    <small class="cover-help"
                      >支持 JPG、PNG、WebP，大小不超过 10MB</small
                    >
                  </div>
                </div>
                <div class="cover-suggestions">
                  <p>以下为系统推荐封面，使用 AI 生成可获取更优质封面 ⓘ</p>
                  <div class="suggestion-list">
                    <button
                      class="ai-cover"
                      @click="creatorAction('AI 封面生成')"
                    >
                      AI生成
                    </button>
                    <span class="cover-suggestion one"></span>
                    <span class="cover-suggestion two"></span>
                    <span class="cover-suggestion three"></span>
                  </div>
                </div>
                <div class="config-row">
                  <label><i>*</i>标题</label>
                  <div class="title-field">
                    <input
                      v-model="upload.title"
                      maxlength="80"
                      placeholder="请输入视频标题"
                    />
                    <small>{{ upload.title.length }} / 80</small>
                  </div>
                </div>
                <div class="config-row">
                  <label><i>*</i>创作声明 ⓘ</label>
                  <select v-model="upload.creationDeclaration">
                    <option value="ORIGINAL">原创</option>
                    <option value="REPRINT">转载</option>
                  </select>
                </div>
                <div class="config-row">
                  <label><i>*</i>分区</label>
                  <select v-model="upload.categoryId">
                    <option
                      v-for="category in categories"
                      :key="category.id"
                      :value="category.id"
                    >
                      {{ category.name }}
                    </option>
                  </select>
                </div>
                <div class="config-row description-config-row">
                  <label>简介</label>
                  <textarea
                    v-model="upload.description"
                    maxlength="2000"
                    placeholder="写一点视频简介吧"
                  ></textarea>
                </div>
                <div class="config-row submit-config-row">
                  <label></label
                  ><button class="primary" @click="sendUpload">
                    上传并提交审核
                  </button>
                </div>
                <div class="legacy-upload-fields" hidden>
                  <div class="selected-video">
                    <span>✓</span><b>{{ upload.file.name }}</b
                    ><small
                      >{{
                        (upload.file.size / 1024 / 1024).toFixed(2)
                      }}
                      MB</small
                    ><button @click="upload.file = null">重新选择</button>
                  </div>
                  <h2>完善投稿信息</h2>
                  <input
                    v-model="upload.title"
                    maxlength="100"
                    placeholder="请输入视频标题"
                  /><textarea
                    v-model="upload.description"
                    maxlength="2000"
                    placeholder="写一点视频简介吧"
                  ></textarea
                  ><select v-model="upload.categoryId">
                    <option
                      v-for="category in categories"
                      :key="category.id"
                      :value="category.id"
                    >
                      {{ category.name }}
                    </option></select
                  ><input
                    v-model.number="upload.durationSeconds"
                    type="number"
                    min="0"
                    max="36000"
                    placeholder="视频时长（秒，可选）"
                  /><label class="file-picker"
                    >选择封面（JPG、PNG、WebP，不超过 10MB）<input
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      @change="
                        upload.cover =
                          ($event.target as HTMLInputElement).files?.[0] || null
                      " /></label
                  ><button class="primary" @click="sendUpload">
                    上传并提交审核
                  </button>
                </div>
              </section>
            </section>
            <section v-else class="creator-dashboard">
              <header class="dash-head">
                <div>
                  <h2>数据看板</h2>
                  <p>统计范围：已发布视频，共 {{ dashboard?.overview.videoCount || 0 }} 个</p>
                </div>
                <div class="dash-range">
                  <button :class="{ active: dashboardDays === 7 }" @click="loadCreatorDashboard(7)">近 7 天</button>
                  <button :class="{ active: dashboardDays === 30 }" @click="loadCreatorDashboard(30)">近 30 天</button>
                </div>
              </header>
              <p v-if="dashboardLoading && !dashboard" class="load-status">正在加载数据…</p>
              <template v-else-if="dashboard">
                <div class="dash-cards">
                  <article>
                    <span>总播放量</span>
                    <b>{{ formatCount(dashboard.overview.viewCount) }}</b>
                    <em>近 {{ dashboard.days }} 天 +{{ formatCount(dashboard.period.views) }}</em>
                  </article>
                  <article>
                    <span>总点赞</span>
                    <b>{{ formatCount(dashboard.overview.likeCount) }}</b>
                    <em>近 {{ dashboard.days }} 天 +{{ formatCount(dashboard.period.likes) }}</em>
                  </article>
                  <article>
                    <span>总评论</span>
                    <b>{{ formatCount(dashboard.overview.commentCount) }}</b>
                    <em>近 {{ dashboard.days }} 天 +{{ formatCount(dashboard.period.comments) }}</em>
                  </article>
                  <article>
                    <span>总收藏</span>
                    <b>{{ formatCount(dashboard.overview.favoriteCount) }}</b>
                    <em>近 {{ dashboard.days }} 天 +{{ formatCount(dashboard.period.favorites) }}</em>
                  </article>
                  <article>
                    <span>粉丝数</span>
                    <b>{{ formatCount(dashboard.overview.followerCount) }}</b>
                    <em>近 {{ dashboard.days }} 天 +{{ formatCount(dashboard.period.followers) }}</em>
                  </article>
                </div>

                <section class="dash-panel">
                  <header class="dash-panel-head">
                    <h3>数据趋势</h3>
                    <div class="dash-metrics">
                      <button :class="{ active: dashboardMetric === 'views' }" @click="dashboardMetric = 'views'">播放</button>
                      <button :class="{ active: dashboardMetric === 'likes' }" @click="dashboardMetric = 'likes'">点赞</button>
                      <button :class="{ active: dashboardMetric === 'comments' }" @click="dashboardMetric = 'comments'">评论</button>
                      <button :class="{ active: dashboardMetric === 'followers' }" @click="dashboardMetric = 'followers'">涨粉</button>
                    </div>
                  </header>
                  <svg class="dash-chart" :viewBox="`0 0 ${trendChart.width} ${trendChart.height}`" role="img" aria-label="数据趋势图">
                    <defs>
                      <linearGradient id="dashFill" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stop-color="#fb7299" stop-opacity="0.26" />
                        <stop offset="100%" stop-color="#fb7299" stop-opacity="0" />
                      </linearGradient>
                    </defs>
                    <g class="dash-grid">
                      <line
                        v-for="tick in trendChart.ticks"
                        :key="`grid-${tick.value}`"
                        :x1="trendChart.left"
                        :x2="trendChart.width - trendChart.right"
                        :y1="tick.y"
                        :y2="tick.y"
                      />
                    </g>
                    <text
                      v-for="tick in trendChart.ticks"
                      :key="`y-${tick.value}`"
                      class="dash-axis-text"
                      :x="trendChart.left - 10"
                      :y="tick.y + 4"
                      text-anchor="end"
                    >
                      {{ formatCount(tick.value) }}
                    </text>
                    <path v-if="trendChart.area" :d="trendChart.area" fill="url(#dashFill)" />
                    <path v-if="trendChart.line" :d="trendChart.line" fill="none" stroke="#fb7299" stroke-width="2" stroke-linejoin="round" />
                    <g v-for="(point, index) in trendChart.coords" :key="`dot-${index}`" class="dash-dot">
                      <circle :cx="point.x" :cy="point.y" r="3.4">
                        <title>{{ point.label }}：{{ point.value }}</title>
                      </circle>
                    </g>
                    <text
                      v-for="(point, index) in trendChart.coords"
                      v-show="index % trendChart.labelEvery === 0 || index === trendChart.coords.length - 1"
                      :key="`x-${index}`"
                      class="dash-axis-text"
                      :x="point.x"
                      :y="trendChart.height - 6"
                      text-anchor="middle"
                    >
                      {{ point.label }}
                    </text>
                  </svg>
                </section>

                <div class="dash-split">
                  <section class="dash-panel">
                    <header class="dash-panel-head">
                      <h3>观众画像</h3>
                      <span>基于粉丝资料与观看记录</span>
                    </header>
                    <div class="audience-block">
                      <h4>粉丝性别</h4>
                      <p v-if="!dashboard.audience.gender.length" class="dash-empty">暂无粉丝数据</p>
                      <div v-for="item in dashboard.audience.gender" :key="`g-${item.label}`" class="audience-row">
                        <span>{{ item.label }}</span>
                        <div class="audience-track"><i :style="{ width: `${Math.max(4, Math.round((item.count * 100) / audienceMax))}%` }"></i></div>
                        <b>{{ item.count }}</b>
                        <em>{{ audienceTotal ? Math.round((item.count * 100) / audienceTotal) : 0 }}%</em>
                      </div>
                    </div>
                    <div class="audience-block">
                      <h4>粉丝年龄</h4>
                      <p v-if="!dashboard.audience.age.length" class="dash-empty">暂无粉丝数据</p>
                      <div v-for="item in dashboard.audience.age" :key="`a-${item.label}`" class="audience-row">
                        <span>{{ item.label }}</span>
                        <div class="audience-track"><i :style="{ width: `${Math.max(4, Math.round((item.count * 100) / audienceMax))}%` }"></i></div>
                        <b>{{ item.count }}</b>
                        <em>{{ audienceTotal ? Math.round((item.count * 100) / audienceTotal) : 0 }}%</em>
                      </div>
                    </div>
                  </section>
                  <section class="dash-panel">
                    <header class="dash-panel-head">
                      <h3>观众活跃时段</h3>
                      <span>登录用户播放时刻分布</span>
                    </header>
                    <div class="hour-bars">
                      <div
                        v-for="item in dashboard.audience.hours"
                        :key="item.hour"
                        class="hour-bar"
                        :title="`${item.hour}:00 - ${item.hour + 1}:00：${item.count} 次播放`"
                      >
                        <i :style="{ height: `${Math.max(3, Math.round((item.count * 100) / hoursMax))}%` }"></i>
                        <span v-if="item.hour % 4 === 0">{{ item.hour }}</span>
                      </div>
                    </div>
                    <p class="hour-tip">悬停柱状图可查看具体时段播放次数</p>
                  </section>
                </div>

                <section class="dash-panel">
                  <header class="dash-panel-head">
                    <h3>视频数据对比</h3>
                    <span>按播放量排序，最多展示 20 条</span>
                  </header>
                  <div class="dash-table">
                    <table>
                      <thead>
                        <tr>
                          <th>视频</th>
                          <th>播放</th>
                          <th>点赞</th>
                          <th>评论</th>
                          <th>收藏</th>
                          <th>互动率</th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr v-for="video in dashboard.videos" :key="video.id">
                          <td class="dash-video-cell">
                            <div class="table-cover">
                              <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" /><span v-else>▶</span>
                            </div>
                            <div class="dash-video-meta">
                              <b>{{ video.title }}</b>
                              <div class="dash-view-bar"><i :style="{ width: `${Math.max(3, Math.round((video.viewCount * 100) / maxVideoViews))}%` }"></i></div>
                              <small>{{ formatDate(video.createdAt) }}</small>
                            </div>
                          </td>
                          <td>{{ formatCount(video.viewCount) }}</td>
                          <td>{{ formatCount(video.likeCount) }}</td>
                          <td>{{ formatCount(video.commentCount) }}</td>
                          <td>{{ formatCount(video.favoriteCount) }}</td>
                          <td>{{ video.interactionRate }}%</td>
                        </tr>
                        <tr v-if="!dashboard.videos.length">
                          <td colspan="6" class="no-data">暂无已发布视频</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </section>
              </template>
            </section>
          </section>
          <section v-else-if="activePage === 'live'" class="live-page">
            <header class="live-hero">
              <div>
                <h1>直播广场</h1>
                <p>RTMP 推流接入 · HLS 拉流播放 · 实时弹幕互动</p>
              </div>
              <button class="live-primary" @click="loggedIn ? scrollToLiveStudio() : openAuth()">＋ 我要开播</button>
            </header>

            <section v-if="loggedIn" ref="liveStudioRef" class="live-studio-card">
              <header class="live-section-head">
                <h2>我的直播间</h2>
                <span v-if="liveMineRoom" class="live-status-badge" :class="liveMineRoom.status.toLowerCase()">{{ liveStatusLabel(liveMineRoom.status) }}</span>
                <span v-else class="live-status-badge ready">未创建</span>
              </header>
              <div v-if="liveMineRoom" class="live-studio-info">
                <div class="live-push-row">
                  <code>{{ liveMineRoom.pushUrl }}</code>
                  <button class="live-ghost" @click="copyLivePushUrl">{{ liveCopied ? "已复制" : "复制" }}</button>
                </div>
                <p class="live-hint">
                  OBS 推流设置：「服务器」填 <code>rtmp://localhost:1935/live</code>，「串流密钥」填
                  <code>{{ liveMineRoom.streamKey }}</code>，开始推流后直播间会自动切换为直播中。
                </p>
                <div class="live-studio-actions">
                  <button v-if="liveMineRoom.status !== 'LIVING'" class="live-primary" @click="startLiveRoom">标记开播</button>
                  <button v-else class="live-ghost danger" @click="stopLiveRoom">下播</button>
                  <button v-if="liveMineRoom.status === 'LIVING'" class="live-ghost" @click="openLiveRoom(liveMineRoom.id)">进入我的直播间</button>
                  <button class="live-ghost" @click="refreshLiveMine">刷新状态</button>
                </div>
                <dl class="live-studio-stats">
                  <div><dt>在线人数</dt><dd>{{ liveMineRoom.viewerCount }}</dd></div>
                  <div><dt>累计弹幕</dt><dd>{{ formatCount(liveMineRoom.danmakuCount) }}</dd></div>
                </dl>
              </div>
              <form class="live-form" @submit.prevent="submitLiveRoom">
                <label class="live-field">
                  <span>直播标题</span>
                  <input v-model="liveTitle" maxlength="80" placeholder="给直播间起个标题，例如：周末一起打游戏" />
                </label>
                <div class="live-cover-row">
                  <label class="live-cover-picker">
                    <input type="file" accept="image/*" hidden @change="selectLiveCover" />
                    <img v-if="liveCoverPreview || liveMineRoom?.coverUrl" :src="liveCoverPreview || liveMineRoom?.coverUrl || ''" alt="直播封面" />
                    <span v-else>上传封面</span>
                  </label>
                  <button class="live-primary" type="submit" :disabled="liveCreating">
                    {{ liveMineRoom ? "保存直播间信息" : "创建直播间" }}
                  </button>
                </div>
              </form>
            </section>

            <section class="live-list">
              <header class="live-section-head">
                <h2>全部直播间</h2>
                <small>{{ liveRooms.length }} 个</small>
              </header>
              <p v-if="liveLoading" class="load-status">正在加载直播列表…</p>
              <div v-else-if="liveRooms.length" class="live-grid">
                <article v-for="room in liveRooms" :key="room.id" class="live-card" @click="openLiveRoom(room.id)">
                  <div class="live-cover">
                    <img v-if="room.coverUrl" :src="room.coverUrl" :alt="room.title" />
                    <span v-else class="live-cover-fallback">◉</span>
                    <em class="live-tag" :class="room.status.toLowerCase()">{{ liveStatusLabel(room.status) }}</em>
                    <small v-if="room.status === 'LIVING'" class="live-viewers">◉ {{ formatCount(room.viewerCount) }}</small>
                  </div>
                  <h3>{{ room.title }}</h3>
                  <p><span class="mini-avatar">{{ room.authorName.slice(0, 1) }}</span>{{ room.authorName }}</p>
                  <small>弹幕 {{ formatCount(room.danmakuCount) }}</small>
                </article>
              </div>
              <p v-else class="empty">还没有直播间，点击右上角「我要开播」创建第一场直播</p>
            </section>
          </section>
          <section v-else-if="activePage === 'liveRoom' && liveCurrent" class="live-room-page">
            <div class="live-room-main">
              <div class="live-stage">
                <video v-if="liveCurrent.playUrl" ref="livePlayer" autoplay playsinline controls></video>
                <div v-else class="live-offline">
                  <strong>{{ liveRoomLoading ? "正在进入直播间…" : liveCurrent.status === "LIVING" ? "正在等待直播画面…" : "直播尚未开始" }}</strong>
                  <p>{{ liveStatusTip || "主播正在准备中，稍后再来看看吧" }}</p>
                  <button v-if="liveCurrent.userId === user?.id" class="live-ghost" @click="startLiveRoom">标记开播</button>
                </div>
                <em class="live-tag" :class="liveCurrent.status.toLowerCase()">{{ liveStatusLabel(liveCurrent.status) }}</em>
              </div>
              <header class="live-room-head">
                <div>
                  <h1>{{ liveCurrent.title }}</h1>
                  <p>
                    <span class="mini-avatar">{{ liveCurrent.authorName.slice(0, 1) }}</span>{{ liveCurrent.authorName }} ·
                    在线 {{ liveViewerCount }} 人 · 弹幕 {{ formatCount(liveCurrent.danmakuCount) }}
                  </p>
                </div>
                <div class="live-room-actions">
                  <button v-if="liveCurrent.userId === user?.id && liveCurrent.status === 'LIVING'" class="live-ghost danger" @click="stopLiveRoom">下播</button>
                  <button class="live-ghost" @click="navigate('live')">返回直播广场</button>
                </div>
              </header>
            </div>
            <aside class="live-chat">
              <header class="live-chat-head">
                <h2>直播弹幕</h2>
                <small>{{ liveViewerCount }} 人在看</small>
              </header>
              <div ref="liveMessageList" class="live-chat-list">
                <div v-for="message in liveMessages" :key="message.id" class="live-chat-item">
                  <img v-if="message.avatar" :src="message.avatar" alt="" />
                  <span v-else class="mini-avatar">{{ message.nickname.slice(0, 1) }}</span>
                  <p><strong>{{ message.nickname }}</strong>{{ message.content }}</p>
                </div>
                <p v-if="!liveMessages.length" class="live-chat-empty">还没有弹幕，来发第一条吧</p>
              </div>
              <form class="live-chat-input" @submit.prevent="sendLiveMessage">
                <input
                  v-model="liveInput"
                  maxlength="100"
                  :placeholder="liveCurrent.status === 'LIVING' ? '发送弹幕…' : '直播开始后即可发送弹幕'"
                />
                <button class="live-primary" type="submit" :disabled="liveCurrent.status !== 'LIVING'">发送</button>
              </form>
            </aside>
          </section>
          <template
            v-else-if="activePage === 'recommend' || activePage === 'search'"
            ><div v-if="activePage === 'search' && !query.trim()" class="search-landing">
              <section v-if="hotKeywords.length" class="search-block">
                <header>
                  <h2>热搜榜</h2>
                  <small>按真实搜索次数排序</small>
                </header>
                <div class="hot-list">
                  <button
                    v-for="item in hotKeywords"
                    :key="item.keyword"
                    @click="submitSearch(item.keyword)"
                  >
                    <em :class="`rank-${item.rank}`">{{ item.rank }}</em>
                    <span>{{ item.keyword }}</span>
                    <small v-if="item.score">{{ item.score }} 次</small>
                  </button>
                </div>
              </section>
              <section v-if="searchHistory.length" class="search-block">
                <header>
                  <h2>搜索历史</h2>
                  <button class="clear-history" @click="clearSearchHistory">清空</button>
                </header>
                <div class="history-list">
                  <button
                    v-for="item in searchHistory"
                    :key="item"
                    @click="submitSearch(item)"
                  >
                    {{ item }}
                  </button>
                </div>
              </section>
              <p v-if="!hotKeywords.length && !searchHistory.length" class="empty">
                输入视频标题或 UP 主昵称开始搜索
              </p>
            </div>
            <div v-if="activePage === 'recommend'" class="home-feed">
              <div class="search-wrap home-search">
                <label class="search-box"
                  ><span>⌕</span
                  ><input
                    v-model="query"
                    placeholder="搜索视频、UP主..."
                    @focus="suggestVisible = true"
                    @input="
                      scheduleSuggest();
                      suggestVisible = true;
                    "
                    @keyup.enter="submitSearch(query)"
                    @blur="hideSuggestSoon"
                  /></label
                >
                <div v-if="suggestVisible && suggestions.length" class="search-suggest">
                  <button
                    v-for="item in suggestions"
                    :key="item"
                    type="button"
                    @mousedown.prevent="submitSearch(item)"
                  >
                    <span class="suggest-icon">⌕</span>{{ item }}
                  </button>
                </div>
              </div>
              <section v-if="heroVideos.length" class="home-hero">
                <article
                  v-for="(item, index) in heroVideos"
                  :key="item.id"
                  class="home-hero-slide"
                  :class="{ active: index === heroIndex }"
                  @click="openVideo(item)"
                >
                  <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.title" />
                  <div class="home-hero-shade"></div>
                  <div class="home-hero-info">
                    <span class="home-hero-badge">精选推荐</span>
                    <h2>{{ item.title }}</h2>
                    <p>
                      <span class="mini-avatar">{{ item.authorName.slice(0, 1) }}</span>
                      {{ item.authorName }}<i>·</i>{{ formatCount(item.viewCount) }} 播放
                    </p>
                  </div>
                </article>
                <button class="home-hero-arrow prev" aria-label="上一个" @click.stop="heroPrev">‹</button>
                <button class="home-hero-arrow next" aria-label="下一个" @click.stop="heroNext">›</button>
                <div class="home-hero-dots">
                  <button
                    v-for="(item, index) in heroVideos"
                    :key="item.id"
                    :class="{ active: index === heroIndex }"
                    :aria-label="`第 ${index + 1} 个推荐`"
                    @click.stop="heroIndex = index"
                  ></button>
                </div>
              </section>
              <section class="home-channels">
                <div class="home-channel-grid">
                  <button class="home-channel" :class="{ active: selectedCategory === 0 }" @click="selectCategory(0)">
                    <span class="channel-icon hot">热</span><b>热门</b>
                  </button>
                  <button
                    v-for="category in categories"
                    :key="category.id"
                    class="home-channel"
                    :class="{ active: selectedCategory === category.id }"
                    @click="selectCategory(category.id)"
                  >
                    <span class="channel-icon">{{ category.name.slice(0, 1) }}</span><b>{{ category.name }}</b>
                  </button>
                </div>
                <div class="home-channel-links">
                  <button @click="navigate('live')">直播</button>
                  <button @click="navigate('create')">投稿</button>
                  <button @click="navigate('history')">历史</button>
                  <button @click="navigate('favorite')">收藏</button>
                </div>
              </section>
            </div>
            <section
              v-if="displayedVideos.length"
              class="home-video-grid"
              :class="{ 'search-grid': activePage === 'search' }"
            >
              <article
                v-for="(video, index) in displayedVideos"
                :key="video.id"
                class="home-card"
                :class="{ featured: activePage === 'recommend' && index === 0 }"
                @click="openVideo(video)"
              >
                <div class="home-card-cover">
                  <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" />
                  <span v-else class="home-card-placeholder">▶</span>
                  <div class="home-card-stats">
                    <span>▶ {{ formatCount(video.viewCount) }}</span>
                    <span>◍ {{ formatCount(video.commentCount) }}</span>
                  </div>
                  <em class="home-card-duration">{{ formatDuration(video.durationSeconds) }}</em>
                </div>
                <div class="home-card-body">
                  <h3 :title="video.title">{{ video.title }}</h3>
                  <p class="home-card-meta">
                    <span class="mini-avatar">{{ video.authorName.slice(0, 1) }}</span>
                    <span class="home-card-author">{{ video.authorName }}</span>
                    <span v-if="categoryName(video.categoryId)" class="home-card-tag">{{ categoryName(video.categoryId) }}</span>
                  </p>
                </div>
              </article>
            </section>
            <p
              v-if="!displayedVideos.length && !(activePage === 'recommend' ? recommendLoading : searchLoading)"
              class="empty"
            >
              {{ activePage === 'search' ? '没有找到匹配的视频，试试其他关键词' : '暂时没有匹配的视频' }}
            </p>
            <p v-if="activePage === 'recommend' && recommendLoading" class="load-status">正在加载推荐内容…</p>
            <p v-if="activePage === 'search' && searchLoading" class="load-status">正在搜索…</p>
            <p
              v-else-if="activePage === 'recommend' && !hasMore && videos.length"
              class="load-status"
            >
              已经到底啦
            </p>
            <button
              v-if="activePage === 'recommend' && videos.length > 1"
              class="home-refresh"
              title="换一换"
              @click="rotateFeed"
            >
              <span>↻</span>换一换
            </button></template
          >
          <section v-else-if="activePage === 'message' && loggedIn" class="notification-page">
            <header class="notification-header">
              <h1>消息通知</h1>
              <button @click="markAllNotificationsRead">全部已读</button>
            </header>
            <nav class="notification-tabs" aria-label="消息分类">
              <button :class="{ active: notificationTab === 'ALL' }" @click="selectNotificationTab('ALL')">全部</button>
              <button :class="{ active: notificationTab === 'LIKE' }" @click="selectNotificationTab('LIKE')">点赞</button>
              <button :class="{ active: notificationTab === 'COMMENT' }" @click="selectNotificationTab('COMMENT')">评论</button>
              <button :class="{ active: notificationTab === 'FOLLOW' }" @click="selectNotificationTab('FOLLOW')">关注</button>
              <button :class="{ active: notificationTab === 'SYSTEM' }" @click="selectNotificationTab('SYSTEM')">系统</button>
            </nav>
            <p v-if="notificationLoading" class="load-status">正在加载消息…</p>
            <div v-else-if="notifications.length" class="notification-list">
              <article v-for="item in notifications" :key="item.id" class="notification-item" :class="{ unread: !item.readAt }">
                <div class="notification-avatar">
                  <img v-if="item.actorAvatar" :src="item.actorAvatar" :alt="item.actorName" />
                  <span v-else>{{ item.actorName.slice(0, 1).toUpperCase() }}</span>
                </div>
                <div class="notification-body">
                  <p><b>{{ item.actorName }}</b><small>{{ formatHistoryTime(item.createdAt) }}</small></p>
                  <p>{{ item.content }}</p>
                </div>
                <button class="notification-delete" @click="deleteNotification(item.id)">删除</button>
              </article>
            </div>
            <div v-else class="notification-empty">
              <span>✉</span><p>暂时没有消息通知</p>
            </div>
          </section>
          <section v-else-if="activePage === 'chat' && loggedIn" class="chat-page">
            <header class="notification-header">
              <h1>私信</h1>
              <span class="chat-unread">未读 {{ unreadMessageCount }}</span>
            </header>
            <div class="chat-layout">
              <aside class="conversation-list">
                <p v-if="conversationLoading" class="load-status">正在加载会话…</p>
                <template v-else-if="conversations.length">
                  <button
                    v-for="item in conversations"
                    :key="item.peerId"
                    class="conversation-item"
                    :class="{ active: item.peerId === activePeerId }"
                    @click="openConversation(item.peerId)"
                  >
                    <span class="avatar small">
                      <img v-if="item.peerAvatar" :src="item.peerAvatar" :alt="item.peerName" />
                      <template v-else>{{ item.peerName.slice(0, 1).toUpperCase() }}</template>
                    </span>
                    <span class="conversation-main">
                      <b>{{ item.peerName }}</b>
                      <small>{{ item.lastContent }}</small>
                    </span>
                    <i v-if="item.unread > 0" class="conversation-badge">{{ item.unread > 99 ? "99+" : item.unread }}</i>
                  </button>
                </template>
                <div v-else class="notification-empty">
                  <span>✎</span><p>还没有私信会话</p>
                </div>
              </aside>
              <div v-if="activePeerId" class="chat-panel">
                <header class="chat-panel-header">
                  <span class="avatar small">
                    <img v-if="chatPeer?.avatar || activeConversation?.peerAvatar" :src="chatPeer?.avatar || activeConversation?.peerAvatar || ''" :alt="chatPeer?.nickname" />
                    <template v-else>{{ (chatPeer?.nickname || activeConversation?.peerName || "友").slice(0, 1).toUpperCase() }}</template>
                  </span>
                  <b>{{ chatPeer?.nickname || activeConversation?.peerName || "私信" }}</b>
                </header>
                <div ref="chatBody" class="chat-body">
                  <p v-if="chatLoading" class="load-status">正在加载私信…</p>
                  <template v-else-if="chatMessages.length">
                    <div
                      v-for="item in chatMessages"
                      :key="item.id"
                      class="chat-bubble-row"
                      :class="{ mine: item.fromId === user?.id }"
                    >
                      <div class="chat-bubble">{{ item.content }}</div>
                      <small>{{ formatHistoryTime(item.createdAt) }}</small>
                    </div>
                  </template>
                  <div v-else class="chat-empty">和 TA 打个招呼吧</div>
                </div>
                <form class="chat-composer" @submit.prevent="sendPrivateMessage">
                  <input v-model="chatInput" maxlength="500" placeholder="输入私信内容..." />
                  <button type="submit" :disabled="sendingMessage || !chatInput.trim()">发送</button>
                </form>
              </div>
              <div v-else class="chat-placeholder">选择左侧会话开始聊天</div>
            </div>
          </section>
          <section
            v-else-if="activePage === 'history' && loggedIn"
            class="history-page"
          >
            <header class="history-header">
              <h1>观看历史</h1>
              <button :disabled="!historyVideos.length" @click="clearHistory">
                清除全部
              </button>
            </header>
            <p v-if="historyLoading" class="history-loading">
              正在加载观看历史…
            </p>
            <div v-else-if="historyVideos.length" class="history-list">
              <article
                v-for="video in historyVideos"
                :key="video.id"
                class="history-item"
                @click="openVideo(video)"
              >
                <div class="history-cover">
                  <img
                    v-if="video.coverUrl"
                    :src="video.coverUrl"
                    :alt="video.title"
                  />
                  <span v-else>▶</span>
                </div>
                <div class="history-info">
                  <h2>{{ video.title }}</h2>
                  <p>{{ video.authorName }}</p>
                  <small>{{ formatHistoryTime(video.watchedAt) }}</small>
                </div>
                <button
                  class="history-remove"
                  title="删除该条记录"
                  @click.stop="removeHistory(video.id)"
                >
                  删除
                </button>
              </article>
            </div>
            <div v-else class="history-empty">
              <span>◷</span>
              <p>还没有观看历史</p>
              <button @click="navigate('recommend')">去看看推荐视频</button>
            </div>
          </section>
          <section v-else-if="activePage === 'favorite' && loggedIn" class="favorite-page">
            <p v-if="favoriteLoading" class="load-status">正在加载我的收藏…</p>
            <section v-else-if="favoriteVideos.length" class="video-grid favorite-grid">
              <article v-for="video in favoriteVideos" :key="video.id" class="video-card" @click="openVideo(video)">
                <div class="video-cover"><img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title"><span v-else>▶</span><em>{{ formatDuration(video.durationSeconds) }}</em></div>
                <h3>{{ video.title }}</h3>
                <p><span class="mini-avatar">{{ video.authorName.slice(0, 1) }}</span>{{ video.authorName }}</p>
                <small>◉ {{ formatCount(video.viewCount) }}　▣ {{ formatCount(video.commentCount) }}</small>
              </article>
            </section>
            <div v-else class="favorite-empty"><span>☆</span><p>还没有收藏视频</p><button @click="navigate('recommend')">去发现喜欢的视频</button></div>
          </section>
          <section v-else-if="activePage === 'following' && loggedIn" class="following-page">
            <header class="following-toolbar">
              <span class="following-group"><i class="following-caret">▾</i>默认分组<span class="following-count">{{ followingTotal - unfollowedAuthorIds.size }}</span></span>
              <span class="following-sort"><i>≡</i>最常访问</span>
            </header>
            <p v-if="followingLoading" class="load-status">正在加载关注列表…</p>
            <div v-else-if="followingUsers.length" class="following-grid">
              <article v-for="author in followingUsers" :key="author.id" class="following-card">
                <button class="following-avatar" title="查看个人主页" @click="openSpace(author.id)">
                  <img v-if="author.avatar" :src="author.avatar" :alt="author.nickname" />
                  <span v-else>{{ author.nickname.slice(0, 1).toUpperCase() }}</span>
                  <i class="following-mark">⚡</i>
                </button>
                <div class="following-info">
                  <b class="clickable-name" @click="openSpace(author.id)">{{ author.nickname }}</b>
                  <p>{{ author.bio || "这个人很神秘，还没有填写简介" }}</p>
                  <button
                    class="following-pill"
                    :class="{ unfollowed: unfollowedAuthorIds.has(author.id) }"
                    :disabled="followingToggleIds.has(author.id)"
                    @click="toggleFollowingUser(author)"
                  >
                    <i>≡</i>{{ unfollowedAuthorIds.has(author.id) ? "关注" : "已关注" }}
                  </button>
                </div>
              </article>
            </div>
            <div v-else class="following-empty"><span>◎</span><p>还没有关注任何 UP 主</p><button @click="navigate('recommend')">去发现更多 UP 主</button></div>
          </section>
          <section v-else-if="activePage === 'space'" class="space-page">
            <p v-if="spaceLoading && !spaceUser" class="load-status">正在加载主页…</p>
            <template v-else-if="spaceUser">
              <div class="space-banner"><span class="space-banner-mark">K</span></div>
              <div class="space-header">
                <div class="space-avatar">
                  <img v-if="spaceUser.avatar" :src="spaceUser.avatar" :alt="spaceUser.nickname" />
                  <span v-else>{{ spaceUser.nickname.slice(0, 1).toUpperCase() }}</span>
                  <i class="following-mark">⚡</i>
                </div>
                <div class="space-identity">
                  <h1>{{ spaceUser.nickname }}<em v-if="spaceUser.isSelf">我自己</em></h1>
                  <p>{{ spaceUser.bio || "这个人很神秘，还没有填写简介" }}</p>
                </div>
                <div class="space-stats">
                  <div><b>{{ formatCount(spaceUser.followingCount) }}</b><span>关注数</span></div>
                  <div><b>{{ formatCount(spaceUser.followerCount) }}</b><span>粉丝数</span></div>
                  <div><b>{{ formatCount(spaceUser.likeCount) }}</b><span>获赞数</span></div>
                </div>
                <div v-if="!spaceUser.isSelf" class="space-actions">
                  <button class="space-follow" :class="{ followed: spaceUser.following }" :disabled="spaceFollowBusy" @click="toggleSpaceFollow">
                    <i>≡</i>{{ spaceUser.following ? "已关注" : "关注" }}
                  </button>
                  <button class="space-message" @click="openChatWith(spaceUser.id)">发消息</button>
                </div>
                <div v-else class="space-actions">
                  <button class="space-message" @click="navigate('settings')">编辑资料</button>
                </div>
              </div>
              <div class="space-content">
                <nav class="space-tabs">
                  <button :class="{ active: spaceSort === 'latest' }" @click="switchSpaceSort('latest')">最新发布</button>
                  <button :class="{ active: spaceSort === 'popular' }" @click="switchSpaceSort('popular')">最多播放</button>
                  <button v-if="spaceVideos.length" class="space-play-all" @click="openVideo(spaceVideos[0])">▶ 播放全部</button>
                </nav>
                <p v-if="spaceLoading" class="load-status">正在加载投稿…</p>
                <div v-else-if="spaceVideos.length" class="video-grid space-grid">
                  <article v-for="video in spaceVideos" :key="video.id" class="video-card" @click="openVideo(video)">
                    <div class="video-cover">
                      <img v-if="video.coverUrl" :src="video.coverUrl" :alt="video.title" /><span v-else>▶</span><em>{{ formatDuration(video.durationSeconds) }}</em>
                    </div>
                    <h3>{{ video.title }}</h3>
                    <p><span class="mini-avatar">{{ video.authorName.slice(0, 1) }}</span>{{ video.authorName }}</p>
                    <small>◉ {{ formatCount(video.viewCount) }}　▣ {{ formatCount(video.commentCount) }}</small>
                  </article>
                </div>
                <p v-else class="empty">这位 UP 主还没有发布视频</p>
              </div>
            </template>
          </section>
          <section v-else-if="activePage === 'fans' && loggedIn" class="fans-page">
            <p v-if="fansLoading" class="load-status">正在加载粉丝列表…</p>
            <div v-else-if="fans.length" class="fan-grid">
              <article v-for="fan in fans" :key="fan.id" class="fan-card fan-member">
                <button class="fan-avatar" title="查看个人主页" @click="openSpace(fan.id)">
                  <img v-if="fan.avatar" :src="fan.avatar" :alt="fan.nickname" />
                  <span v-else>{{ fan.nickname.slice(0, 1).toUpperCase() }}</span>
                </button>
                <b class="clickable-name" @click="openSpace(fan.id)">{{ fan.nickname }}</b>
                <small>关注于 {{ formatHistoryTime(fan.followedAt) }}</small>
              </article>
            </div>
            <article v-else class="fan-card empty-fans">
              <div class="avatar fan-avatar">K</div>
              <b>还没有粉丝</b><small>发布优质内容，等待第一位粉丝吧</small>
            </article>
          </section>
          <section
            v-else-if="activePage === 'settings' && loggedIn"
            class="account-settings"
          >
            <div class="setting-row avatar-row">
              <label>头像</label
              ><button
                class="avatar-upload"
                title="上传头像"
                @click="avatarInput?.click()"
              >
                <img
                  v-if="profile.avatar"
                  :src="profile.avatar"
                  alt="用户头像"
                />
                <div v-else class="avatar profile-avatar">
                  {{
                    profile.nickname?.slice(0, 1).toUpperCase() || avatarText
                  }}
                </div>
                <span>更换头像</span></button
              ><input
                ref="avatarInput"
                class="avatar-file"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                @change="uploadAvatar"
              />
            </div>
            <div class="setting-row">
              <label for="nickname">昵称</label
              ><input id="nickname" v-model="profile.nickname" maxlength="64" />
            </div>
            <div class="setting-row gender-row">
              <label>性别</label
              ><label
                ><input v-model="profile.gender" type="radio" value="PRIVATE" />
                保密</label
              ><label
                ><input v-model="profile.gender" type="radio" value="MALE" />
                男</label
              ><label
                ><input v-model="profile.gender" type="radio" value="FEMALE" />
                女</label
              >
            </div>
            <div class="setting-row">
              <label for="birthday">生日</label
              ><input id="birthday" v-model="profile.birthday" type="date" />
            </div>
            <div class="setting-row bio-row">
              <label for="bio">简介</label>
              <div>
                <textarea
                  id="bio"
                  v-model="profile.bio"
                  maxlength="200"
                  placeholder="介绍一下自己吧"
                ></textarea
                ><small>{{ profile.bio?.length || 0 }} / 200</small>
              </div>
            </div>
            <div class="setting-row theme-row">
              <label>主题</label><b :class="{ selected: !dark }">浅色模式</b
              ><button
                class="theme-switch"
                :class="{ on: dark }"
                @click="dark = !dark"
              >
                <i></i></button
              ><b :class="{ selected: dark }">深色模式</b>
            </div>
            <div class="setting-row save-row">
              <label></label
              ><button class="save-button" @click="saveProfile">保存</button>
            </div>
          </section>
          <section v-else class="empty-state">
            <div class="empty-icon">◌</div>
            <p>{{ pageTitle[activePage] }}暂时没有内容</p>
          </section>
        </template>
      </main>
    </section>
    <div
      v-if="authVisible"
      class="modal-mask login-mask"
      @click.self="authVisible = false"
    >
      <section class="login-window">
        <div class="login-traffic"><i></i><i></i><i></i></div>
        <button class="login-close" @click="authVisible = false">×</button>
        <div class="login-left">
          <h2>扫描二维码登录</h2>
          <div class="login-qr">
            <div class="login-qr-grid">
              <i v-for="(cell, index) in qrCells" :key="index" :class="{ on: cell }"></i>
            </div>
            <span class="qr-finder top-left"></span>
            <span class="qr-finder top-right"></span>
            <span class="qr-finder bottom-left"></span>
            <span class="login-qr-mask">扫码登录敬请期待</span>
          </div>
          <p class="login-qr-tip">
            请使用 <b>Kilikili 客户端</b><br />扫码登录或扫码下载 APP
          </p>
        </div>
        <div class="login-right">
          <nav class="login-tabs">
            <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">密码登录</button>
            <span>|</span>
            <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">注册账号</button>
          </nav>
          <div class="login-form">
            <label class="login-field">
              <i class="login-prefix">账号</i>
              <input v-model="auth.username" placeholder="请输入用户名" />
            </label>
            <label class="login-field">
              <i class="login-prefix">密码</i>
              <input v-model="auth.password" type="password" placeholder="请输入密码" @keyup.enter="submitAuth" />
            </label>
            <label v-if="authMode === 'register'" class="login-field">
              <i class="login-prefix">昵称</i>
              <input v-model="auth.nickname" placeholder="请输入昵称" @keyup.enter="submitAuth" />
            </label>
          </div>
          <button class="login-submit" @click="submitAuth">{{ authMode === "login" ? "登录" : "注册" }}</button>
          <p class="login-note">
            未注册过的账号，可在上方「注册账号」中创建<br />
            登录或完成注册即代表你同意 <a>用户协议</a> 和 <a>隐私政策</a>
          </p>
        </div>
      </section>
    </div>

    <div
      v-if="uploadSuccessVisible"
      class="modal-mask"
      @click.self="uploadSuccessVisible = false"
    >
      <section class="auth-modal upload-success-modal">
        <button class="close" @click="uploadSuccessVisible = false">×</button>
        <div class="success-icon">✓</div>
        <h2>上传成功</h2>
        <p>{{ uploadSuccessMessage }}</p>
        <p class="muted">视频正在后台转码，完成后将支持多清晰度播放</p>
        <button class="primary" @click="uploadSuccessVisible = false">知道了</button>
      </section>
    </div>

    <div
      v-if="uploadWarningVisible"
      class="modal-mask"
      @click.self="uploadWarningVisible = false"
    >
      <section class="auth-modal upload-success-modal">
        <button class="close" @click="uploadWarningVisible = false">×</button>
        <div class="success-icon warning">!</div>
        <h2>无法上传</h2>
        <p>{{ uploadWarningMessage }}</p>
        <button class="primary" @click="uploadWarningVisible = false">去填写</button>
      </section>
    </div>
  </div>
</template>