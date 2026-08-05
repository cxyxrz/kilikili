<script setup lang="ts">
import axios from "axios";
import { computed, ref } from "vue";

type Page = "dashboard" | "audit" | "videos" | "categories" | "danmakus" | "comments" | "users" | "settings" | "logs";
type UserItem = { id: number; username: string; nickname: string; status: string; createdAt?: string };
type VideoItem = { id: number; title: string; description: string; status: string; username: string; nickname: string; viewCount: number; likeCount: number; commentCount: number; createdAt?: string };
type DanmakuItem = { id: number; content: string; nickname: string; videoTitle: string; timeMs: number; createdAt: string };
type CategoryItem = { id: number; name: string; code: string; parentId: number | null; sortOrder: number; childCount: number; videoCount: number };

const credentials = ref({ username: "", password: "" });
const token = ref(sessionStorage.getItem("kilikili_admin_token") || "");
const adminName = ref(sessionStorage.getItem("kilikili_admin_name") || "");
const ready = ref(Boolean(token.value));
const activePage = ref<Page>("categories");
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
const selectedCategoryId = ref<number | null>(null);
const expandedCategoryId = ref<number | null>(null);
const categoryDialog = ref(false);
const editingCategoryId = ref<number | null>(null);
const categoryForm = ref({ name: "", code: "", parentId: null as number | null, sortOrder: 0 });
const danmakuTotal = ref(0);
const danmakuPage = ref(1);
const danmakuPageSize = ref(10);
const userKeyword = ref("");
const videoKeyword = ref("");
const videoStatus = ref("");

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
  users.value = (await axios.get("/api/admin/users", { headers: headers(), params: { keyword: userKeyword.value } })).data.data;
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
async function login() {
  try {
    const data = (await axios.post("/api/admin/auth/login", credentials.value)).data.data;
    token.value = data.token;
    adminName.value = data.user.nickname;
    sessionStorage.setItem("kilikili_admin_token", token.value);
    sessionStorage.setItem("kilikili_admin_name", adminName.value);
    ready.value = true;
    notice.value = "";
    await openPage("categories");
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
  } catch (error) {
    fail(error);
  }
}
async function setUserStatus(id: number, status: string) {
  try {
    await axios.patch(`/api/admin/users/${id}/status`, { status }, { headers: headers() });
    await Promise.all([loadUsers(), dashboard()]);
  } catch (error) { fail(error); }
}
async function setVideoStatus(id: number, status: string) {
  try {
    await axios.patch(`/api/admin/videos/${id}/status`, { status }, { headers: headers() });
    await Promise.all([loadVideos(), dashboard()]);
  } catch (error) { fail(error); }
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
function changeDanmakuPage(page: number) {
  danmakuPage.value = Math.min(Math.max(1, page), danmakuPages.value);
  loadDanmakus();
}
function changePageSize() {
  danmakuPage.value = 1;
  loadDanmakus();
}
function formatDuration(timeMs = 0) {
  const seconds = Math.max(0, Math.floor(timeMs / 1000));
  return `${String(Math.floor(seconds / 60)).padStart(2, "0")}:${String(seconds % 60).padStart(2, "0")}`;
}
function formatDate(value?: string) {
  return value ? value.replace("T", " ").replace("Z", "").slice(0, 19) : "-";
}

if (ready.value) openPage("categories");
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
          <div class="admin-table-wrap"><table><thead><tr><th>视频名称</th><th>作者</th><th>数据</th><th>状态</th><th>操作</th></tr></thead><tbody>
            <tr v-for="video in videos" :key="video.id"><td><b>{{ video.title }}</b><small>{{ video.description }}</small></td><td>{{ video.nickname }}<small>@{{ video.username }}</small></td><td>{{ video.viewCount }} 播放 · {{ video.likeCount }} 赞 · {{ video.commentCount }} 评论</td><td><span class="status" :class="video.status">{{ video.status }}</span></td><td class="table-actions"><button v-if="video.status !== 'PUBLISHED'" @click="setVideoStatus(video.id, 'PUBLISHED')">通过</button><button v-if="video.status !== 'REJECTED'" @click="setVideoStatus(video.id, 'REJECTED')">驳回</button><button v-if="video.status !== 'OFFLINE'" class="danger" @click="setVideoStatus(video.id, 'OFFLINE')">下架</button></td></tr>
            <tr v-if="!videos.length"><td colspan="5" class="empty-cell">没有匹配的视频</td></tr>
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

        <section v-else-if="activePage === 'users'" class="management-page">
          <div class="filter-bar"><input v-model="userKeyword" placeholder="用户名或昵称" @keyup.enter="loadUsers" /><button @click="loadUsers">搜索</button></div>
          <div class="admin-table-wrap"><table><thead><tr><th>ID</th><th>用户名</th><th>昵称</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in users" :key="item.id"><td>{{ item.id }}</td><td>{{ item.username }}</td><td>{{ item.nickname }}</td><td><span class="status" :class="item.status">{{ item.status }}</span></td><td><button v-if="item.status === 'ACTIVE'" class="text-danger" @click="setUserStatus(item.id, 'DISABLED')">禁用</button><button v-else @click="setUserStatus(item.id, 'ACTIVE')">启用</button></td></tr></tbody></table></div>
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
