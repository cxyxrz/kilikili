<script setup lang="ts">
import axios from 'axios'
import { ref } from 'vue'

type User = { id: number; username: string; nickname: string; status: string }
type Video = { id: number; title: string; description: string; status: string; username: string; nickname: string; viewCount: number; likeCount: number; commentCount: number }
const credentials = ref({ username: '', password: '' })
const token = ref(sessionStorage.getItem('kilikili_admin_token') || '')
const adminName = ref(sessionStorage.getItem('kilikili_admin_name') || '')
const ready = ref(Boolean(token.value))
const notice = ref('')
const stats = ref<Record<string, number>>({})
const users = ref<User[]>([])
const videos = ref<Video[]>([])
const userKeyword = ref('')
const videoKeyword = ref('')
const videoStatus = ref('PENDING_REVIEW')
const headers = () => ({ Authorization: `Bearer ${token.value}` })
const fail = (error: any) => { notice.value = error.response?.data?.message || '请求失败，请确认管理端后端已启动' }
async function dashboard() { stats.value = (await axios.get('/api/admin/dashboard', { headers: headers() })).data.data }
async function loadUsers() { users.value = (await axios.get('/api/admin/users', { headers: headers(), params: { keyword: userKeyword.value } })).data.data }
async function loadVideos() { videos.value = (await axios.get('/api/admin/videos', { headers: headers(), params: { keyword: videoKeyword.value, status: videoStatus.value } })).data.data }
async function login() { try { const data = (await axios.post('/api/admin/auth/login', credentials.value)).data.data; token.value = data.token; adminName.value = data.user.nickname; sessionStorage.setItem('kilikili_admin_token', token.value); sessionStorage.setItem('kilikili_admin_name', adminName.value); ready.value = true; await refresh(); notice.value = '登录成功' } catch (e) { fail(e) } }
async function refresh() { try { await dashboard(); await Promise.all([loadUsers(), loadVideos()]) } catch (e) { fail(e); if (notice.value.includes('登录')) logout() } }
function logout() { token.value = ''; adminName.value = ''; ready.value = false; sessionStorage.removeItem('kilikili_admin_token'); sessionStorage.removeItem('kilikili_admin_name') }
async function setUserStatus(id: number, status: string) { try { await axios.patch(`/api/admin/users/${id}/status`, { status }, { headers: headers() }); await loadUsers(); await dashboard(); notice.value = '用户状态已更新' } catch (e) { fail(e) } }
async function setVideoStatus(id: number, status: string) { try { await axios.patch(`/api/admin/videos/${id}/status`, { status }, { headers: headers() }); await loadVideos(); await dashboard(); notice.value = '视频状态已更新' } catch (e) { fail(e) } }
if (ready.value) refresh()
</script>

<template>
  <main v-if="!ready" class="login"><h1>kilikili 管理台</h1><p>仅限具有 <code>ADMIN</code> 角色的账号登录。</p><input v-model="credentials.username" autocomplete="username" placeholder="管理员账号"><input v-model="credentials.password" type="password" autocomplete="current-password" placeholder="密码" @keyup.enter="login"><button @click="login">登录</button><p v-if="notice" class="notice">{{ notice }}</p></main>
  <main v-else><header><div><h1>kilikili 管理台</h1><p>当前管理员：{{ adminName }} · 内容审核、用户与视频管理</p></div><button @click="logout">退出</button></header><p v-if="notice" class="notice">{{ notice }}</p>
    <section class="stats"><article><b>{{ stats.users || 0 }}</b><span>用户数</span></article><article><b>{{ stats.videos || 0 }}</b><span>视频数</span></article><article><b>{{ stats.pendingVideos || 0 }}</b><span>待审核</span></article><article><b>{{ stats.comments || 0 }}</b><span>评论数</span></article></section>
    <section><div class="section-head"><h2>内容审核 / 视频管理</h2><input v-model="videoKeyword" placeholder="标题或作者" @keyup.enter="loadVideos"><select v-model="videoStatus" @change="loadVideos"><option value="">全部状态</option><option value="PENDING_REVIEW">待审核</option><option value="PUBLISHED">已发布</option><option value="REJECTED">已驳回</option><option value="OFFLINE">已下架</option></select><button @click="loadVideos">查询</button></div><div class="table-wrap"><table><thead><tr><th>视频</th><th>作者</th><th>数据</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="video in videos" :key="video.id"><td><b>{{ video.title }}</b><small>{{ video.description }}</small></td><td>{{ video.nickname }}<small>@{{ video.username }}</small></td><td>{{ video.viewCount }} 播放 · {{ video.likeCount }} 赞 · {{ video.commentCount }} 评</td><td><em :class="video.status">{{ video.status }}</em></td><td class="actions"><button v-if="video.status !== 'PUBLISHED'" @click="setVideoStatus(video.id, 'PUBLISHED')">通过</button><button v-if="video.status !== 'REJECTED'" @click="setVideoStatus(video.id, 'REJECTED')">驳回</button><button v-if="video.status !== 'OFFLINE'" @click="setVideoStatus(video.id, 'OFFLINE')">下架</button></td></tr><tr v-if="!videos.length"><td colspan="5">没有匹配的视频</td></tr></tbody></table></div></section>
    <section><div class="section-head"><h2>用户管理</h2><input v-model="userKeyword" placeholder="用户名或昵称" @keyup.enter="loadUsers"><button @click="loadUsers">查询</button></div><div class="table-wrap"><table><thead><tr><th>ID</th><th>用户名</th><th>昵称</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in users" :key="item.id"><td>{{ item.id }}</td><td>{{ item.username }}</td><td>{{ item.nickname }}</td><td><em :class="item.status">{{ item.status }}</em></td><td><button v-if="item.status === 'ACTIVE'" @click="setUserStatus(item.id, 'DISABLED')">禁用</button><button v-else @click="setUserStatus(item.id, 'ACTIVE')">启用</button></td></tr></tbody></table></div></section>
  </main>
</template>
