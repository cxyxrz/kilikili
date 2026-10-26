<script setup lang="ts">
import axios from "axios";
import { computed, ref } from "vue";

type Page = "dashboard" | "audit" | "videos" | "categories" | "danmakus" | "comments" | "users" | "settings" | "logs";
type UserItem = { id: number; username: string; email: string | null; nickname: string; avatarUrl: string | null; gender: string; status: string; createdAt?: string; lastLoginAt?: string };
type VideoItem = { id: number; title: string; description: string; status: string; username: string; nickname: string; viewCount: number; likeCount: number; commentCount: number; createdAt?: string; aiResult?: string | null; aiScore?: number | null; aiReason?: string | null };
type DanmakuItem = { id: number; content: string; nickname: string; videoTitle: string; timeMs: number; createdAt: string };
type CategoryItem = { id: number; name: string; code: string; parentId: number | null; sortOrder: number; childCount: number; videoCount: number };
type CommentItem = { id: number; parentId: number | null; content: string; username: string; nickname: string; videoTitle: string; replyCount: number; createdAt: string };
type OperationLogItem = { id: number; operatorId: number | null; operatorName: string; module: string; action: string; description: string; ipAddress: string; status: string; errorMessage: string | null; durationMs: number; createdAt: string };
type SystemSettings = { allowRegistration: boolean; danmakuEnabled: boolean; commentEnabled: boolean; videoReviewEnabled: boolean; likeEnabled: boolean; coinEnabled: boolean; favoriteEnabled: boolean; followEnabled: boolean };

const credentials = ref({ username: "", password: "" });
const token = ref(sessionStorage.getItem("kilikili_admin_token") || "");
const adminName = ref(sessionStorage.getItem("kilikili_admin_name") || "");
const ready = ref(Boolean(token.value));
const activePage = ref<Page>("settings");
const sidebarCollapsed = ref(false);
const contentExpanded = ref(true);
const systemExpanded = ref(true);
const notice = ref("");
const loading = ref(false);
const stats = ref<Record<string, number>>({});
const users = ref<UserItem[]>([]);
const videos = ref<VideoItem[]>([]);
const danmakus = ref<DanmakuItem[]>([]);
const categories = ref<CategoryItem[]>([]);
const comments = ref<CommentItem[]>([]);
const operationLogs = ref<OperationLogItem[]>([]);
const systemSettings = ref<SystemSettings>({ allowRegistration: true, danmakuEnabled: true, commentEnabled: true, videoReviewEnabled: true, likeEnabled: true, coinEnabled: true, favoriteEnabled: true, followEnabled: true });
const settingsSaving = ref(false);
const settingsSaved = ref(false);
const settingOptions: { key: keyof SystemSettings; label: string }[] = [
  { key: "allowRegistration", label: "允许注册" },
  { key: "danmakuEnabled", label: "弹幕功能" },
  { key: "commentEnabled", label: "评论功能" },
  { key: "videoReviewEnabled", label: "视频审核" },
  { key: "likeEnabled", label: "点赞功能" },
  { key: "coinEnabled", label: "投币功能" },
  { key: "favoriteEnabled", label: "收藏功能" },
  { key: "followEnabled", label: "关注功能" },
];
const selectedCategoryId = ref<number | null>(null);
const expandedCategoryId = ref<number | null>(null);
const categoryDialog = ref(false);
const editingCategoryId = ref<number | null>(null);
const categoryForm = ref({ name: "", code: "", parentId: null as number | null, sortOrder: 0 });
const danmakuTotal = ref(0);
const danmakuPage = ref(1);
const danmakuPageSize = ref(10);
const commentTotal = ref(0);
const commentPage = ref(1);
const commentPageSize = ref(10);
const commentKeyword = ref("");
const commentType = ref("");
const userKeyword = ref("");
const userStatus = ref("");
const userTotal = ref(0);
const userPage = ref(1);
const userPageSize = ref(10);
const videoKeyword = ref("");
const videoStatus = ref("");
const logOperator = ref("");
const logModule = ref("");
const logTotal = ref(0);
const logPage = ref(1);
const logPageSize = ref(20);
const logModules = ["仪表盘", "视频管理", "分类管理", "弹幕管理", "评论管理", "用户管理", "操作日志", "系统管理"];

const headers = () => ({ Authorization: `Bearer ${token.value}` });
const pageTitle: Record<Page, string> = {
  dashboard: "仪表盘", audit: "视频审核", videos: "视频列表", categories: "分类管理",
  danmakus: "弹幕管理", comments: "评论管理", users: "用户管理", settings: "系统设置", logs: "操作日志",
};
const danmakuPages = computed(() => Math.max(1, Math.ceil(danmakuTotal.value / danmakuPageSize.value)));
const visiblePages = computed(() => {
  const total = danmakuPages.value;
  const current = danmakuPage.value;
  const start = Math.max(1, Math.min(current - 1, total - 2));
  return Array.from({ length: Math.min(3, total) }, (_, index) => start + index);
});
const commentPages = computed(() => Math.max(1, Math.ceil(commentTotal.value / commentPageSize.value)));
const visibleCommentPages = computed(() => {
  const total = commentPages.value;
  const current = commentPage.value;
  const start = Math.max(1, Math.min(current - 1, total - 2));
  return Array.from({ length: Math.min(3, total) }, (_, index) => start + index);
});
const userPages = computed(() => Math.max(1, Math.ceil(userTotal.value / userPageSize.value)));
const visibleUserPages = computed(() => {
  const total = userPages.value;
  const current = userPage.value;
  const start = Math.max(1, Math.min(current - 1, total - 2));
  return Array.from({ length: Math.min(3, total) }, (_, index) => start + index);
});
const logPages = computed(() => Math.max(1, Math.ceil(logTotal.value / logPageSize.value)));
const visibleLogPages = computed(() => {
  const total = logPages.value;
  const current = logPage.value;
  const start = Math.max(1, Math.min(current - 1, total - 2));
  return Array.from({ length: Math.min(3, total) }, (_, index) => start + index);
});
const rootCategories = computed(() => categories.value
  .filter((item) => item.parentId == null)
  .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id));
const selectedCategory = computed(() => rootCategories.value.find((item) => item.id === selectedCategoryId.value) || null);
const selectedChildren = computed(() => selectedCategoryId.value == null ? [] : childrenOf(selectedCategoryId.value));

function fail(error: any) {
  notice.value = error.response?.data?.message || "请求失败，请确认管理端后端已启动";
  if (error.response?.status === 401) logout();
}
async function dashboard() {
  stats.value = (await axios.get("/api/admin/dashboard", { headers: headers() })).data.data;
}
async function loadUsers() {
  loading.value = true;
  try {
    const data = (await axios.get("/api/admin/users", {
      headers: headers(),
      params: { keyword: userKeyword.value, status: userStatus.value, page: userPage.value, pageSize: userPageSize.value },
    })).data.data;
    users.value = data.items;
    userTotal.value = data.total;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
async function loadVideos() {
  videos.value = (await axios.get("/api/admin/videos", { headers: headers(), params: { keyword: videoKeyword.value, status: videoStatus.value } })).data.data;
}
async function loadDanmakus() {
  loading.value = true;
  try {
    const data = (await axios.get("/api/admin/danmakus", {
      headers: headers(), params: { page: danmakuPage.value, pageSize: danmakuPageSize.value },
    })).data.data;
    danmakus.value = data.items;
    danmakuTotal.value = data.total;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
function childrenOf(parentId: number) {
  return categories.value
    .filter((item) => item.parentId === parentId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id);
}
async function loadCategories() {
  loading.value = true;
  try {
    categories.value = (await axios.get("/api/admin/categories", { headers: headers() })).data.data;
    const current = rootCategories.value.find((item) => item.id === selectedCategoryId.value);
    const preferred = current || rootCategories.value.find((item) => item.code === "tech") || rootCategories.value[0];
    selectedCategoryId.value = preferred?.id ?? null;
    expandedCategoryId.value = preferred?.id ?? null;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
async function loadComments() {
  loading.value = true;
  try {
    const data = (await axios.get("/api/admin/comments", {
      headers: headers(),
      params: {
        keyword: commentKeyword.value,
        type: commentType.value,
        page: commentPage.value,
        pageSize: commentPageSize.value,
      },
    })).data.data;
    comments.value = data.items;
    commentTotal.value = data.total;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
async function loadOperationLogs() {
  loading.value = true;
  try {
    const data = (await axios.get("/api/admin/operation-logs", {
      headers: headers(),
      params: {
        operator: logOperator.value,
        module: logModule.value,
        page: logPage.value,
        pageSize: logPageSize.value,
      },
    })).data.data;
    operationLogs.value = data.items;
    logTotal.value = data.total;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
async function loadSystemSettings() {
  loading.value = true;
  settingsSaved.value = false;
  try {
    systemSettings.value = (await axios.get("/api/admin/system-settings", { headers: headers() })).data.data;
  } catch (error) {
    fail(error);
  } finally {
    loading.value = false;
  }
}
function toggleSystemSetting(key: keyof SystemSettings) {
  systemSettings.value = { ...systemSettings.value, [key]: !systemSettings.value[key] };
  settingsSaved.value = false;
}
async function saveSystemSettings() {
  settingsSaving.value = true;
  settingsSaved.value = false;
  try {
    systemSettings.value = (await axios.put("/api/admin/system-settings", systemSettings.value, { headers: headers() })).data.data;
    settingsSaved.value = true;
    window.setTimeout(() => { settingsSaved.value = false; }, 2200);
  } catch (error) {
    fail(error);
  } finally {
    settingsSaving.value = false;
  }
}
async function login() {
  try {
    const data = (await axios.post("/api/admin/auth/login", credentials.value)).data.data;
    token.value = data.token;
    adminName.value = data.user.nickname;
    sessionStorage.setItem("kilikili_admin_token", token.value);
    sessionStorage.setItem("kilikili_admin_name", adminName.value);
    ready.value = true;
    notice.value = "";
    await openPage("settings");
  } catch (error) {
    fail(error);
  }
}
function logout() {
  token.value = "";
  adminName.value = "";
  ready.value = false;
  sessionStorage.removeItem("kilikili_admin_token");
  sessionStorage.removeItem("kilikili_admin_name");
}
async function openPage(page: Page) {
  activePage.value = page;
  notice.value = "";
  try {
    if (page === "dashboard") await dashboard();
    if (page === "audit") { videoStatus.value = "PENDING_REVIEW"; await loadVideos(); }
    if (page === "videos") { videoStatus.value = ""; await loadVideos(); }
    if (page === "users") await loadUsers();
    if (page === "danmakus") await loadDanmakus();
    if (page === "categories") await loadCategories();
    if (page === "comments") await loadComments();
    if (page === "settings") await loadSystemSettings();
    if (page === "logs") await loadOperationLogs();
  } catch (error) {
    fail(error);
  }
}
async function setUserStatus(id: number, status: string) {
  try {
    await axios.patch(`/api/admin/users/${id}/status`, { status }, { headers: headers() });
    if (users.value.length === 1 && userPage.value > 1 && userStatus.value && userStatus.value !== status) userPage.value -= 1;
    await Promise.all([loadUsers(), dashboard()]);
  } catch (error) { fail(error); }
}
async function setVideoStatus(id: number, status: string) {
  try {
    await axios.patch(`/api/admin/videos/${id}/status`, { status }, { headers: headers() });
    await Promise.all([loadVideos(), dashboard()]);
  } catch (error) { fail(error); }
}
function aiResultLabel(result: string) {
  return { PASS: "通过", REVIEW: "待复核", REJECT: "违规", ERROR: "审核失败" }[result] || result;
}
async function deleteDanmaku(item: DanmakuItem) {
  if (!window.confirm(`确定删除弹幕“${item.content}”吗？`)) return;
  try {
    await axios.delete(`/api/admin/danmakus/${item.id}`, { headers: headers() });
    if (danmakus.value.length === 1 && danmakuPage.value > 1) danmakuPage.value -= 1;
    await loadDanmakus();
  } catch (error) { fail(error); }
}
function selectCategory(item: CategoryItem) {
  selectedCategoryId.value = item.id;
  expandedCategoryId.value = expandedCategoryId.value === item.id ? null : item.id;
}
function openCategoryDialog(item?: CategoryItem, parentId: number | null = null) {
  editingCategoryId.value = item?.id ?? null;
  categoryForm.value = item
    ? { name: item.name, code: item.code, parentId: item.parentId, sortOrder: item.sortOrder }
    : {
        name: "",
        code: "",
        parentId,
        sortOrder: parentId == null
          ? (rootCategories.value.at(-1)?.sortOrder ?? 0) + 1
          : (childrenOf(parentId).at(-1)?.sortOrder ?? 0) + 1,
      };
  categoryDialog.value = true;
}
async function saveCategory() {
  const payload = { ...categoryForm.value };
  try {
    if (editingCategoryId.value == null) {
      await axios.post("/api/admin/categories", payload, { headers: headers() });
    } else {
      await axios.put(`/api/admin/categories/${editingCategoryId.value}`, payload, { headers: headers() });
    }
    categoryDialog.value = false;
    await loadCategories();
  } catch (error) { fail(error); }
}
async function deleteCategory(item: CategoryItem) {
  if (!window.confirm(`确定删除分类“${item.name}”吗？`)) return;
  try {
    await axios.delete(`/api/admin/categories/${item.id}`, { headers: headers() });
    if (selectedCategoryId.value === item.id) selectedCategoryId.value = null;
    await loadCategories();
  } catch (error) { fail(error); }
}
async function deleteComment(item: CommentItem) {
  const detail = item.parentId == null && item.replyCount > 0 ? `，并同时删除 ${item.replyCount} 条回复` : "";
  if (!window.confirm(`确定删除这条评论${detail}吗？`)) return;
  try {
    await axios.delete(`/api/admin/comments/${item.id}`, { headers: headers() });
    if (comments.value.length === 1 && commentPage.value > 1) commentPage.value -= 1;
    await Promise.all([loadComments(), dashboard()]);
  } catch (error) { fail(error); }
}
function changeDanmakuPage(page: number) {
  danmakuPage.value = Math.min(Math.max(1, page), danmakuPages.value);
  loadDanmakus();
}
function changePageSize() {
  danmakuPage.value = 1;
  loadDanmakus();
}
function searchComments() {
  commentPage.value = 1;
  loadComments();
}
function changeCommentPage(page: number) {
  commentPage.value = Math.min(Math.max(1, page), commentPages.value);
  loadComments();
}
function changeCommentPageSize() {
  commentPage.value = 1;
  loadComments();
}
function searchUsers() {
  userPage.value = 1;
  loadUsers();
}
function changeUserPage(page: number) {
  userPage.value = Math.min(Math.max(1, page), userPages.value);
  loadUsers();
}
function changeUserPageSize() {
  userPage.value = 1;
  loadUsers();
}
function formatGender(gender?: string) {
  return gender === "MALE" ? "男" : gender === "FEMALE" ? "女" : "保密";
}
function searchOperationLogs() {
  logPage.value = 1;
  loadOperationLogs();
}
function changeLogPage(page: number) {
  logPage.value = Math.min(Math.max(1, page), logPages.value);
  loadOperationLogs();
}
function changeLogPageSize() {
  logPage.value = 1;
  loadOperationLogs();
}
async function clearOperationLogs() {
  if (!window.confirm("确定清空全部操作日志吗？清空后将保留本次清空操作的审计记录。")) return;
  try {
    await axios.delete("/api/admin/operation-logs", { headers: headers() });
    logPage.value = 1;
    await loadOperationLogs();
  } catch (error) { fail(error); }
}
function formatDuration(timeMs = 0) {
  const seconds = Math.max(0, Math.floor(timeMs / 1000));
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}
function formatDate(value?: string) {
  return value ? value.replace("T", " ").replace("Z", "").slice(0, 19) : "-";
}

if (ready.value) openPage("settings");
</script>

<template>
  <main v-if="!ready" class="login-page">
    <section class="login-card">
      <div class="login-brand"><span>K</span><b>Kilikili Admin</b></div>
      <h1>管理员登录</h1>
      <p>使用管理员账号和密码进入内容管理后台</p>
      <input v-model="credentials.username" autocomplete="username" placeholder="管理员账号" />
      <input v-model="credentials.password" type="password" autocomplete="current-password" placeholder="密码" @keyup.enter="login" />
      <button @click="login">登录</button>
      <p v-if="notice" class="login-error">{{ notice }}</p>
    </section>
  </main>

  <main v-else class="admin-shell" :class="{ collapsed: sidebarCollapsed }">
    <aside class="admin-sidebar">
      <div class="admin-brand"><span>K</span><b>Kilikili Admin</b></div>
      <nav class="admin-menu">
        <button :class="{ active: activePage === 'dashboard' }" @click="openPage('dashboard')"><i>▥</i><span>仪表盘</span></button>
        <button class="menu-group" @click="contentExpanded = !contentExpanded"><i>▣</i><span>内容管理</span><em>{{ contentExpanded ? '⌃' : '⌄' }}</em></button>
        <div v-show="contentExpanded" class="submenu">
          <button :class="{ active: activePage === 'audit' }" @click="openPage('audit')"><i>☷</i><span>视频审核</span></button>
          <button :class="{ active: activePage === 'videos' }" @click="openPage('videos')"><i>▤</i><span>视频列表</span></button>
          <button :class="{ active: activePage === 'categories' }" @click="openPage('categories')"><i>▦</i><span>分类管理</span></button>
          <button :class="{ active: activePage === 'danmakus' }" @click="openPage('danmakus')"><i>▢</i><span>弹幕管理</span></button>
          <button :class="{ active: activePage === 'comments' }" @click="openPage('comments')"><i>▱</i><span>评论管理</span></button>
        </div>
        <button :class="{ active: activePage === 'users' }" @click="openPage('users')"><i>♙</i><span>用户管理</span></button>
        <button class="menu-group" @click="systemExpanded = !systemExpanded"><i>⚙</i><span>系统管理</span><em>{{ systemExpanded ? '⌃' : '⌄' }}</em></button>
        <div v-show="systemExpanded" class="submenu">
          <button :class="{ active: activePage === 'settings' }" @click="openPage('settings')"><i>✿</i><span>系统设置</span></button>
          <button :class="{ active: activePage === 'logs' }" @click="openPage('logs')"><i>▧</i><span>操作日志</span></button>
        </div>
      </nav>
    </aside>

    <section class="admin-workspace">
      <header class="admin-topbar">
        <button class="collapse-button" @click="sidebarCollapsed = !sidebarCollapsed">☰</button>
        <div class="breadcrumb"><b>首页</b><span>/</span><span>{{ pageTitle[activePage] }}</span></div>
        <button class="admin-profile" @click="logout"><i>♙</i><span>{{ adminName || '管理员' }}</span><em>⌄</em></button>
      </header>

      <div class="admin-content">
        <p v-if="notice" class="admin-notice">{{ notice }}</p>

        <section v-if="activePage === 'dashboard'" class="dashboard-page">
          <div class="stat-grid">
            <article><span>用户数量</span><b>{{ stats.users || 0 }}</b></article>
            <article><span>视频数量</span><b>{{ stats.videos || 0 }}</b></article>
            <article><span>待审核视频</span><b>{{ stats.pendingVideos || 0 }}</b></article>
            <article><span>评论数量</span><b>{{ stats.comments || 0 }}</b></article>
          </div>
        </section>

        <section v-else-if="activePage === 'audit' || activePage === 'videos'" class="management-page">
          <div class="filter-bar">
            <input v-model="videoKeyword" placeholder="搜索视频标题或作者" @keyup.enter="loadVideos" />
            <select v-model="videoStatus" @change="loadVideos"><option value="">全部状态</option><option value="PENDING_REVIEW">待审核</option><option value="PUBLISHED">已发布</option><option value="REJECTED">已驳回</option><option value="OFFLINE">已下架</option></select>
            <button @click="loadVideos">搜索</button>
          </div>
          <div class="admin-table-wrap"><table><thead><tr><th>视频名称</th><th>作者</th><th>数据</th><th>AI 初审</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-for="video in videos" :key="video.id"><td><b>{{ video.title }}</b><small>{{ video.description }}</small></td><td>{{ video.nickname }}<small>@{{ video.username }}</small></td><td>{{ video.viewCount }} 播放 · {{ video.likeCount }} 赞 · {{ video.commentCount }} 评论</td><td><span v-if="video.aiResult" class="ai-badge" :class="'ai-' + String(video.aiResult).toLowerCase()" :title="video.aiReason || ''">{{ aiResultLabel(video.aiResult) }}<small v-if="video.aiScore !== null && video.aiScore !== undefined"> {{ video.aiScore }}分</small></span><span v-else class="ai-pending">待初审</span></td><td><span class="status" :class="video.status">{{ video.status }}</span></td><td class="table-actions"><button v-if="video.status !== 'PUBLISHED'" @click="setVideoStatus(video.id, 'PUBLISHED')">通过</button><button v-if="video.status !== 'REJECTED'" @click="setVideoStatus(video.id, 'REJECTED')">驳回</button><button v-if="video.status !== 'OFFLINE'" class="danger" @click="setVideoStatus(video.id, 'OFFLINE')">下架</button></td></tr>
            <tr v-if="!videos.length"><td colspan="6" class="empty-cell">没有匹配的视频</td></tr>
          </tbody></table></div>
        </section>

        <section v-else-if="activePage === 'categories'" class="category-page">
          <div class="category-toolbar">
            <div><h2>分类管理</h2><p>维护视频分区、分类编码和展示顺序</p></div>
            <button class="primary-button" @click="openCategoryDialog()">添加分类</button>
          </div>

          <div class="admin-table-wrap category-main-table">
            <table>
              <thead><tr><th>分类名称</th><th>分类编码</th><th>排序</th><th>子分类数</th><th>视频数</th><th>操作</th></tr></thead>
              <tbody>
                <template v-for="category in rootCategories" :key="category.id">
                  <tr class="category-root-row" :class="{ selected: selectedCategoryId === category.id }" @click="selectCategory(category)">
                    <td><span class="category-toggle">{{ category.childCount ? (expandedCategoryId === category.id ? '⌄' : '›') : '' }}</span>{{ category.name }}</td>
                    <td>{{ category.code }}</td><td>{{ category.sortOrder }}</td><td><span class="count-tag">{{ category.childCount }}</span></td><td>{{ category.videoCount }}</td>
                    <td class="category-actions"><button @click.stop="openCategoryDialog(category)">编辑</button><button class="text-danger" @click.stop="deleteCategory(category)">删除</button></td>
                  </tr>
                  <tr v-for="child in childrenOf(category.id)" v-show="expandedCategoryId === category.id" :key="child.id" class="category-child-row">
                    <td>{{ child.name }}</td><td>{{ child.code }}</td><td>{{ child.sortOrder }}</td><td><span class="count-tag">0</span></td><td>{{ child.videoCount }}</td>
                    <td class="category-actions"><button @click="openCategoryDialog(child)">编辑</button><button class="text-danger" @click="deleteCategory(child)">删除</button></td>
                  </tr>
                </template>
                <tr v-if="!rootCategories.length && !loading"><td colspan="6" class="empty-cell">暂无分类数据</td></tr>
              </tbody>
            </table>
          </div>

          <section class="subcategory-panel">
            <header>
              <h2>子分类<span v-if="selectedCategory"> - {{ selectedCategory.name }}</span></h2>
              <button class="primary-button" :disabled="!selectedCategory" @click="selectedCategory && openCategoryDialog(undefined, selectedCategory.id)">添加子分类</button>
            </header>
            <div class="admin-table-wrap subcategory-table"><table><thead><tr><th>分类名称</th><th>分类编码</th><th>排序</th><th>视频数</th><th>操作</th></tr></thead><tbody>
              <tr v-for="child in selectedChildren" :key="child.id"><td>{{ child.name }}</td><td>{{ child.code }}</td><td>{{ child.sortOrder }}</td><td>{{ child.videoCount }}</td><td class="category-actions"><button @click="openCategoryDialog(child)">编辑</button><button class="text-danger" @click="deleteCategory(child)">删除</button></td></tr>
              <tr v-if="selectedCategory && !selectedChildren.length"><td colspan="5" class="empty-cell">该分类下暂无子分类</td></tr>
              <tr v-if="!selectedCategory"><td colspan="5" class="empty-cell">请选择一个一级分类</td></tr>
            </tbody></table></div>
          </section>
        </section>

        <section v-else-if="activePage === 'danmakus'" class="danmaku-page">
          <div class="admin-table-wrap danmaku-table"><table><thead><tr><th>弹幕内容</th><th>用户</th><th>视频</th><th>时间点</th><th>发送时间</th><th>操作</th></tr></thead><tbody>
            <tr v-for="item in danmakus" :key="item.id"><td>{{ item.content }}</td><td>{{ item.nickname }}</td><td>{{ item.videoTitle }}</td><td>{{ formatDuration(item.timeMs) }}</td><td>{{ formatDate(item.createdAt) }}</td><td><button class="text-danger" @click="deleteDanmaku(item)">删除</button></td></tr>
            <tr v-if="!danmakus.length && !loading"><td colspan="6" class="empty-cell">暂无弹幕数据</td></tr>
          </tbody></table></div>
          <footer class="pagination"><span>Total {{ danmakuTotal }}</span><select v-model.number="danmakuPageSize" @change="changePageSize"><option :value="10">10/page</option><option :value="20">20/page</option><option :value="50">50/page</option></select><button :disabled="danmakuPage <= 1" @click="changeDanmakuPage(danmakuPage - 1)">‹</button><button v-for="page in visiblePages" :key="page" :class="{ active: page === danmakuPage }" @click="changeDanmakuPage(page)">{{ page }}</button><button :disabled="danmakuPage >= danmakuPages" @click="changeDanmakuPage(danmakuPage + 1)">›</button><span>Go to</span><input :value="danmakuPage" type="number" min="1" :max="danmakuPages" @change="changeDanmakuPage(Number(($event.target as HTMLInputElement).value))" /></footer>
        </section>

        <section v-else-if="activePage === 'comments'" class="comment-page">
          <div class="comment-filter">
            <input v-model.trim="commentKeyword" placeholder="搜索评论内容、用户或视频标题" @keyup.enter="searchComments" />
            <select v-model="commentType" @change="searchComments"><option value="">全部类型</option><option value="ROOT">主评论</option><option value="REPLY">回复</option></select>
            <button @click="searchComments">搜索</button>
            <span>共 {{ commentTotal }} 条评论</span>
          </div>
          <div class="admin-table-wrap comment-table"><table><thead><tr><th>评论内容</th><th>类型</th><th>评论用户</th><th>所属视频</th><th>发布时间</th><th>操作</th></tr></thead><tbody>
            <tr v-for="item in comments" :key="item.id">
              <td><b>{{ item.content }}</b><small v-if="item.parentId == null && item.replyCount">{{ item.replyCount }} 条回复</small><small v-else-if="item.parentId != null">回复评论 #{{ item.parentId }}</small></td>
              <td><span class="comment-type" :class="{ reply: item.parentId != null }">{{ item.parentId == null ? '主评论' : '回复' }}</span></td>
              <td>{{ item.nickname }}<small>@{{ item.username }}</small></td>
              <td>{{ item.videoTitle }}</td><td>{{ formatDate(item.createdAt) }}</td><td><button class="text-danger" @click="deleteComment(item)">删除</button></td>
            </tr>
            <tr v-if="!comments.length && !loading"><td colspan="6" class="empty-cell">暂无匹配的评论</td></tr>
          </tbody></table></div>
          <footer class="pagination"><span>Total {{ commentTotal }}</span><select v-model.number="commentPageSize" @change="changeCommentPageSize"><option :value="10">10/page</option><option :value="20">20/page</option><option :value="50">50/page</option></select><button :disabled="commentPage <= 1" @click="changeCommentPage(commentPage - 1)">‹</button><button v-for="page in visibleCommentPages" :key="page" :class="{ active: page === commentPage }" @click="changeCommentPage(page)">{{ page }}</button><button :disabled="commentPage >= commentPages" @click="changeCommentPage(commentPage + 1)">›</button><span>Go to</span><input :value="commentPage" type="number" min="1" :max="commentPages" @change="changeCommentPage(Number(($event.target as HTMLInputElement).value))" /></footer>
        </section>

        <section v-else-if="activePage === 'users'" class="user-page">
          <h1>用户管理</h1>
          <div class="user-filter">
            <input v-model.trim="userKeyword" placeholder="搜索昵称/邮箱" @keyup.enter="searchUsers" />
            <select v-model="userStatus" @change="searchUsers"><option value="">状态</option><option value="ACTIVE">正常</option><option value="DISABLED">禁用</option></select>
            <button @click="searchUsers">搜索</button>
          </div>
          <div class="admin-table-wrap user-table"><table><thead><tr><th>头像</th><th>昵称</th><th>邮箱</th><th>性别</th><th>状态</th><th>注册时间</th><th>最后登录</th></tr></thead><tbody>
            <tr v-for="item in users" :key="item.id">
              <td><img v-if="item.avatarUrl" :src="item.avatarUrl" :alt="item.nickname" /><span v-else class="avatar-fallback">{{ item.nickname?.slice(0, 1).toUpperCase() || 'K' }}</span></td>
              <td>{{ item.nickname }}<small>@{{ item.username }}</small></td><td>{{ item.email || item.username }}</td><td>{{ formatGender(item.gender) }}</td>
              <td><div class="user-status-control"><span :class="{ muted: item.status === 'ACTIVE' }">禁用</span><button class="user-switch" :class="{ active: item.status === 'ACTIVE' }" :aria-label="item.status === 'ACTIVE' ? '禁用用户' : '启用用户'" @click="setUserStatus(item.id, item.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE')"><i></i></button><span :class="{ normal: item.status === 'ACTIVE' }">正常</span></div></td>
              <td>{{ formatDate(item.createdAt) }}</td><td>{{ item.lastLoginAt ? formatDate(item.lastLoginAt) : '从未登录' }}</td>
            </tr>
            <tr v-if="!users.length && !loading"><td colspan="7" class="empty-cell">暂无匹配用户</td></tr>
          </tbody></table></div>
          <footer class="pagination"><span>Total {{ userTotal }}</span><select v-model.number="userPageSize" @change="changeUserPageSize"><option :value="10">10/page</option><option :value="20">20/page</option><option :value="50">50/page</option></select><button :disabled="userPage <= 1" @click="changeUserPage(userPage - 1)">‹</button><button v-for="page in visibleUserPages" :key="page" :class="{ active: page === userPage }" @click="changeUserPage(page)">{{ page }}</button><button :disabled="userPage >= userPages" @click="changeUserPage(userPage + 1)">›</button><span>Go to</span><input :value="userPage" type="number" min="1" :max="userPages" @change="changeUserPage(Number(($event.target as HTMLInputElement).value))" /></footer>
        </section>

        <section v-else-if="activePage === 'settings'" class="system-setting-page">
          <h1>系统设置</h1>
          <div class="system-setting-card">
            <div class="system-setting-list">
              <div v-for="option in settingOptions" :key="option.key" class="system-setting-row">
                <span>{{ option.label }}</span>
                <button class="setting-switch" :class="{ active: systemSettings[option.key] }" :aria-label="`${option.label}${systemSettings[option.key] ? '已开启' : '已关闭'}`" @click="toggleSystemSetting(option.key)"><i></i></button>
              </div>
            </div>
            <button class="save-settings-button" :disabled="settingsSaving" @click="saveSystemSettings">{{ settingsSaving ? '保存中...' : '保存设置' }}</button>
            <p v-if="settingsSaved" class="settings-saved">设置已保存并立即生效</p>
          </div>
        </section>

        <section v-else-if="activePage === 'logs'" class="operation-log-page">
          <header><h1>操作日志</h1><button class="clear-log-button" @click="clearOperationLogs">清空日志</button></header>
          <div class="operation-log-filter">
            <input v-model.trim="logOperator" placeholder="搜索操作人" @keyup.enter="searchOperationLogs" />
            <select v-model="logModule" @change="searchOperationLogs"><option value="">操作模块</option><option v-for="module in logModules" :key="module" :value="module">{{ module }}</option></select>
            <button @click="searchOperationLogs">搜索</button>
          </div>
          <div class="admin-table-wrap operation-log-table"><table><thead><tr><th>模块</th><th>操作类型</th><th>操作描述</th><th>操作人</th><th>IP地址</th><th>状态</th><th>错误信息</th><th>操作时间</th></tr></thead><tbody>
            <tr v-for="item in operationLogs" :key="item.id"><td>{{ item.module }}</td><td>{{ item.action }}</td><td>{{ item.description }}<small>耗时 {{ item.durationMs }}ms</small></td><td>{{ item.operatorName }}</td><td>{{ item.ipAddress || '-' }}</td><td><span class="log-status" :class="item.status">{{ item.status === 'SUCCESS' ? '成功' : '失败' }}</span></td><td class="log-error">{{ item.errorMessage || '-' }}</td><td>{{ formatDate(item.createdAt) }}</td></tr>
            <tr v-if="!operationLogs.length && !loading"><td colspan="8" class="empty-cell">暂无操作日志</td></tr>
          </tbody></table></div>
          <footer class="pagination"><span>Total {{ logTotal }}</span><select v-model.number="logPageSize" @change="changeLogPageSize"><option :value="10">10/page</option><option :value="20">20/page</option><option :value="50">50/page</option></select><button :disabled="logPage <= 1" @click="changeLogPage(logPage - 1)">‹</button><button v-for="page in visibleLogPages" :key="page" :class="{ active: page === logPage }" @click="changeLogPage(page)">{{ page }}</button><button :disabled="logPage >= logPages" @click="changeLogPage(logPage + 1)">›</button><span>Go to</span><input :value="logPage" type="number" min="1" :max="logPages" @change="changeLogPage(Number(($event.target as HTMLInputElement).value))" /></footer>
        </section>

        <section v-else class="placeholder-page"><span>◇</span><h2>{{ pageTitle[activePage] }}</h2><p>该模块界面已预留，可在下一阶段继续接入业务数据。</p></section>
      </div>
    </section>
  </main>

  <div v-if="categoryDialog" class="dialog-mask" @click.self="categoryDialog = false">
    <form class="category-dialog" @submit.prevent="saveCategory">
      <header><h2>{{ editingCategoryId == null ? '添加分类' : '编辑分类' }}</h2><button type="button" @click="categoryDialog = false">×</button></header>
      <label><span>分类名称</span><input v-model.trim="categoryForm.name" maxlength="32" required placeholder="请输入分类名称" /></label>
      <label><span>分类编码</span><input v-model.trim="categoryForm.code" maxlength="64" required pattern="[a-z0-9][a-z0-9-]*" placeholder="例如：programming" /><small>仅支持小写字母、数字和连字符</small></label>
      <label><span>父级分类</span><select v-model="categoryForm.parentId"><option :value="null">无（一级分类）</option><option v-for="item in rootCategories" :key="item.id" :value="item.id" :disabled="item.id === editingCategoryId">{{ item.name }}</option></select></label>
      <label><span>排序</span><input v-model.number="categoryForm.sortOrder" type="number" min="0" max="9999" required /></label>
      <footer><button type="button" class="cancel-button" @click="categoryDialog = false">取消</button><button type="submit" class="primary-button">保存</button></footer>
    </form>
  </div>
</template>
