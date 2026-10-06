import { createRouter, createWebHashHistory } from 'vue-router'

// 注意：main.js 里没有 app.use(router)，路由实例由 App.vue 直接驱动，
// 所以这里全部用静态 import —— <component :is> 拿到的是组件对象本身。
import ArtifactView from '../views/ArtifactView.vue'
import ExhibitionView from '../views/ExhibitionView.vue'
import HandoverView from '../views/HandoverView.vue'
import GalleryView from '../views/GalleryView.vue'
import TourView from '../views/TourView.vue'
import MemberView from '../views/MemberView.vue'

const routes = [
  { path: '/', redirect: '/artifact' },
  { path: '/artifact', name: 'artifact', component: ArtifactView },
  { path: '/exhibition', name: 'exhibition', component: ExhibitionView },
  { path: '/handover', name: 'handover', component: HandoverView },
  { path: '/gallery', name: 'gallery', component: GalleryView },
  { path: '/tour', name: 'tour', component: TourView },
  { path: '/member', name: 'member', component: MemberView },
  { path: '/:pathMatch(.*)*', redirect: '/artifact' }
]

export default createRouter({
  history: createWebHashHistory(),
  routes
})
