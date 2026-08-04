<script setup lang="ts">
import axios from "axios";
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";

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
type Category = { id: number; name: string };
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
type Page =
  | "recommend"
  | "search"
  | "create"
  | "history"
  | "favorite"
  | "message"
  | "following"
  | "fans"
  | "settings";

const token = ref(localStorage.getItem("kilikili_token") || "");
const user = ref<User | null>(
  JSON.parse(localStorage.getItem("kilikili_user") || "null"),
);
const activePage = ref<Page>("fans");
const sidebarCollapsed = ref(false);
const dark = ref(false);
const query = ref("");
const videos = ref<Video[]>([]);
const categories = ref<Category[]>([]);
const selectedCategory = ref(0);
const recommendPage = ref(1);
const recommendLoading = ref(false);
const hasMore = ref(true);
const creatorTab = ref<"manage" | "upload" | "stats">("manage");
const creatorVideos = ref<CreatorVideo[]>([]);
const creatorKeyword = ref("");
const creatorStatus = ref("");
const creatorStats = ref<Record<string, number>>({});
const historyVideos = ref<HistoryVideo[]>([]);
const historyLoading = ref(false);
const favoriteVideos = ref<FavoriteVideo[]>([]);
const favoriteLoading = ref(false);
const selectedFavorited = ref(false);
const selectedFollowing = ref(false);
const fans = ref<Follower[]>([]);
const fansLoading = ref(false);
const notifications = ref<NotificationItem[]>([]);
const notificationLoading = ref(false);
const notificationTab = ref<"ALL" | NotificationItem["type"]>("ALL");
const selected = ref<Video | null>(null);
const playUrl = ref("");
const comments = ref<Comment[]>([]);
const commentSort = ref<"hot" | "latest">("hot");
const replyTo = ref<Comment | null>(null);
const notice = ref("");
const authVisible = ref(false);
const authMode = ref<"login" | "register">("login");
const auth = ref({ username: "", password: "", nickname: "" });
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

const navItems: { key: Page; label: string; icon: string }[] = [
  { key: "recommend", label: "推荐", icon: "▣" },
  { key: "search", label: "搜索", icon: "⌕" },
  { key: "create", label: "创作中心", icon: "□" },
  { key: "history", label: "历史记录", icon: "◷" },
  { key: "favorite", label: "我的收藏", icon: "☆" },
  { key: "message", label: "消息通知", icon: "✉" },
  { key: "following", label: "我的关注", icon: "♙" },
  { key: "fans", label: "我的粉丝", icon: "●" },
  { key: "settings", label: "账号设置", icon: "⚙" },
];
const pageTitle: Record<Page, string> = {
  recommend: "推荐视频",
  search: "搜索结果",
  create: "创作中心",
  history: "观看历史",
  favorite: "我的收藏",
  message: "消息通知",
  following: "我的关注",
  fans: "粉丝列表",
  settings: "账号设置",
};
const loggedIn = computed(() => Boolean(token.value));
const filteredVideos = computed(() =>
  videos.value.filter((video) =>
    video.title.toLowerCase().includes(query.value.trim().toLowerCase()),
  ),
);
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
  } catch (error) {
    fail(error);
  } finally {
    recommendLoading.value = false;
  }
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
    notice.value = data.following ? "已关注 UP 主" : "已取消关注";
  } catch (error) {
    fail(error);
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
      notice.value = "登录成功";
      authVisible.value = false;
      if (activePage.value === "history") loadHistory();
      if (activePage.value === "favorite") loadFavorites();
      if (activePage.value === "message") loadNotifications();
      if (activePage.value === "fans") loadFans();
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
  notifications.value = [];
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
async function loadCreatorStats() {
  if (!loggedIn.value) return;
  try {
    creatorStats.value = (
      await axios.get("/api/videos/mine/stats", { headers: authHeaders() })
    ).data.data;
  } catch (error) {
    fail(error);
  }
}
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
    notice.value = "全部消息已读";
  } catch (error) {
    fail(error);
  }
}
async function deleteNotification(id: number) {
  try {
    await axios.delete(`/api/notifications/${id}`, { headers: authHeaders() });
    notifications.value = notifications.value.filter((item) => item.id !== id);
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
  const timeMs = player.currentTime * 1000;
  const fontSize = Math.max(16, Math.min(24, width / 48));
  const lineHeight = fontSize + 10;
  const laneCount = Math.max(1, Math.floor((height * 0.62) / lineHeight));
  context.font = `600 ${fontSize}px Microsoft YaHei, sans-serif`;
  context.textBaseline = "middle";
  danmakus.value.forEach((item) => {
    const elapsed = timeMs - item.timeMs;
    const visibleFor = item.mode === "SCROLL" ? 8000 : 4200;
    if (elapsed < 0 || elapsed > visibleFor) return;
    const textWidth = context.measureText(item.content).width;
    const lane = item.id % laneCount;
    let x = 0;
    let y = lineHeight / 2 + lane * lineHeight;
    if (item.mode === "SCROLL") {
      x = width - ((width + textWidth) * elapsed) / visibleFor;
    } else {
      x = (width - textWidth) / 2;
      if (item.mode === "BOTTOM") y = height - lineHeight / 2 - lane * lineHeight;
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
  if (
    (page === "create" || page === "settings" || page === "history" || page === "favorite" || page === "message" || page === "fans") &&
    !loggedIn.value
  )
    openAuth();
  if (page === "settings") loadProfile();
  if (page === "history" && loggedIn.value) loadHistory();
  if (page === "favorite" && loggedIn.value) loadFavorites();
  if (page === "message" && loggedIn.value) loadNotifications();
  if (page === "fans" && loggedIn.value) loadFans();
  if (page === "create" && loggedIn.value) {
    loadCreatorVideos();
    loadCreatorStats();
  }
}
async function sendUpload() {
  if (!upload.value.file) {
    notice.value = "请选择视频文件";
    return;
  }
  if (!upload.value.cover) {
    notice.value = "请上传视频封面";
    return;
  }
  if (!upload.value.title.trim()) {
    notice.value = "请填写视频标题";
    return;
  }
  try {
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
    comments.value = (
      await axios.get(`/api/videos/${video.id}/comments`)
    ).data.data;
    await loadDanmakus(video.id);
    await nextTick();
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
onMounted(() => {
  loadCategories();
  loadVideos();
});
onBeforeUnmount(stopDanmakuRender);
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
        ><label class="search-box"
          ><span>⌕</span
          ><input
            v-model="query"
            placeholder="搜索视频、UP主..."
            @focus="activePage = 'search'"
          /><span class="mic">♩</span></label
        >
        <div class="top-actions">
          <button title="切换主题" @click="dark = !dark">☾</button
          ><button class="bell" title="通知">♧<i></i></button
          ><button
            class="avatar profile"
            @click="loggedIn ? navigate('settings') : openAuth()"
          >
            {{ avatarText }}
          </button>
        </div>
      </header>
      <main class="content" @scroll="onContentScroll">
        <p v-if="notice" class="notice">{{ notice }}</p>
        <template v-if="selected">
          <section class="watch-layout">
            <div class="watch-main">
              <section class="watch-player-card">
                <div class="video-stage">
                  <video ref="videoPlayer" v-if="playUrl" :src="playUrl" controls autoplay @loadedmetadata="startDanmakuRender" @seeked="startDanmakuRender"></video>
                  <canvas ref="danmakuCanvas" class="danmaku-canvas"></canvas>
                </div>
                <div v-if="loggedIn" class="danmaku-entry">
                  <input
                    v-model="danmaku"
                    maxlength="1000"
                    placeholder="发一条弹幕吧…"
                    @keyup.enter="sendDanmaku"
                  />
                  <button @click="sendDanmaku">发送</button>
                </div>
                <div class="danmaku-tools">
                  <button :class="{ active: danmakuMode === 'SCROLL' }" @click="danmakuMode = 'SCROLL'">滚动</button><button :class="{ active: danmakuMode === 'TOP' }" @click="danmakuMode = 'TOP'">顶部</button
                  ><button :class="{ active: danmakuMode === 'BOTTOM' }" @click="danmakuMode = 'BOTTOM'">底部</button
                  ><label class="danmaku-color" title="自定义颜色"><input v-model="danmakuColor" type="color" aria-label="弹幕颜色"></label>
                </div>
              </section>
              <section class="watch-detail-card">
                <h1>{{ selected.title }}</h1>
                <p class="watch-meta">
                  ◉ {{ formatCount(selected.viewCount) }} 播放　▣
                  {{ formatCount(selected.commentCount) }} 弹幕　{{
                    formatDate(selected.createdAt || "")
                  }}
                </p>
                <p v-if="selected.description" class="watch-description">
                  {{ selected.description }}
                </p>
                <div class="watch-actions">
                  <button class="watch-like" @click="like">
                    ♥ {{ selected.likeCount }}
                  </button>
                  <button @click="watchAction('投币')">◉ 投币</button>
                  <button :class="{ active: selectedFavorited }" @click="toggleFavorite">{{ selectedFavorited ? '★ 已收藏' : '☆ 收藏' }}</button>
                </div>
                <section v-if="false" class="watch-comments">
                  <h2>评论（{{ comments.length }}）</h2>
                  <p v-for="item in comments" :key="item.id" class="comment">
                    <b>{{ item.nickname }}</b
                    >：{{ item.content }}
                  </p>
                  <p v-if="!comments.length" class="no-comments">
                    暂无评论，来留下第一条评论吧
                  </p>
                </section>
              </section>
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
            </div>
            <aside class="related-section">
              <section class="up-info-card">
                <div class="up-info-head">
                  <div class="up-avatar">
                    <img v-if="selected.authorAvatar" :src="selected.authorAvatar" :alt="selected.authorName" />
                    <span v-else>{{ selected.authorName.slice(0, 1).toUpperCase() }}</span>
                  </div>
                  <div class="up-info-name">
                    <h2>{{ selected.authorName }}</h2>
                    <p>{{ formatCount(selected.likeCount) }} 获赞 · {{ formatCount(selected.viewCount) }} 播放</p>
                  </div>
                  <button class="follow-button" :class="{ followed: isFollowingSelected }" @click="toggleAuthorFollow">
                    {{ isFollowingSelected ? "✓ 已关注" : "+ 关注" }}
                  </button>
                </div>
                <p class="up-video-summary">发布了这条视频 · {{ formatCount(selected.commentCount) }} 条互动</p>
              </section>
              <h2>相关推荐</h2>
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
                  <div>
                    <h3>{{ video.title }}</h3>
                    <p>{{ formatCount(video.viewCount) }} 次播放</p>
                  </div>
                </article>
                <p v-if="!relatedVideos.length" class="related-empty">
                  暂无相关推荐
                </p>
              </div>
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
                  loadCreatorStats();
                "
              >
                ▥ 数据统计
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
            <section v-else class="creator-stat-grid">
              <article>
                <b>{{ creatorStats.videoCount || 0 }}</b
                ><span>投稿视频</span>
              </article>
              <article>
                <b>{{ creatorStats.viewCount || 0 }}</b
                ><span>总播放量</span>
              </article>
              <article>
                <b>{{ creatorStats.likeCount || 0 }}</b
                ><span>累计点赞</span>
              </article>
              <article>
                <b>{{ creatorStats.commentCount || 0 }}</b
                ><span>累计评论</span>
              </article>
            </section>
          </section>
          <template
            v-else-if="activePage === 'recommend' || activePage === 'search'"
            ><div v-if="activePage === 'recommend'" class="category-bar">
              <button
                :class="{ active: selectedCategory === 0 }"
                @click="selectCategory(0)"
              >
                全部</button
              ><button
                v-for="category in categories"
                :key="category.id"
                :class="{ active: selectedCategory === category.id }"
                @click="selectCategory(category.id)"
              >
                {{ category.name }}</button
              ><button class="more-category">⌄ 更多</button>
            </div>
            <section class="video-grid">
              <article
                v-for="video in filteredVideos"
                :key="video.id"
                class="video-card"
                @click="openVideo(video)"
              >
                <div class="video-cover">
                  <img
                    v-if="video.coverUrl"
                    :src="video.coverUrl"
                    :alt="video.title"
                  /><span v-else>▶</span
                  ><em>{{ formatDuration(video.durationSeconds) }}</em>
                </div>
                <h3>{{ video.title }}</h3>
                <p>
                  <span class="mini-avatar">{{
                    video.authorName.slice(0, 1)
                  }}</span
                  >{{ video.authorName }}
                </p>
                <small
                  >◉ {{ formatCount(video.viewCount) }}　▣
                  {{ formatCount(video.commentCount) }}</small
                >
              </article>
              <p
                v-if="!filteredVideos.length && !recommendLoading"
                class="empty"
              >
                暂时没有可展示的视频
              </p>
            </section>
            <p v-if="recommendLoading" class="load-status">正在加载推荐内容…</p>
            <p
              v-else-if="
                activePage === 'recommend' && !hasMore && videos.length
              "
              class="load-status"
            >
              已经到底啦
            </p></template
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
          <section v-else-if="activePage === 'fans' && loggedIn" class="fans-page">
            <p v-if="fansLoading" class="load-status">正在加载粉丝列表…</p>
            <div v-else-if="fans.length" class="fan-grid">
              <article v-for="fan in fans" :key="fan.id" class="fan-card fan-member">
                <div class="fan-avatar">
                  <img v-if="fan.avatar" :src="fan.avatar" :alt="fan.nickname" />
                  <span v-else>{{ fan.nickname.slice(0, 1).toUpperCase() }}</span>
                </div>
                <b>{{ fan.nickname }}</b>
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
      class="modal-mask"
      @click.self="authVisible = false"
    >
      <section class="auth-modal">
        <button class="close" @click="authVisible = false">×</button>
        <div class="modal-brand">
          <span class="brand-mark">K</span><b>Kilikili</b>
        </div>
        <h2>{{ authMode === "login" ? "欢迎回来" : "创建账号" }}</h2>
        <input v-model="auth.username" placeholder="用户名" /><input
          v-model="auth.password"
          type="password"
          placeholder="密码"
        /><input
          v-if="authMode === 'register'"
          v-model="auth.nickname"
          placeholder="昵称"
        /><button class="primary" @click="submitAuth">
          {{ authMode === "login" ? "登录" : "注册" }}
        </button>
        <p>
          <a @click="authMode = authMode === 'login' ? 'register' : 'login'">{{
            authMode === "login" ? "没有账号？去注册" : "已有账号？去登录"
          }}</a>
        </p>
      </section>
    </div>
  </div>
</template>
