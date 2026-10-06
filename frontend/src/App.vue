<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import router from './router'
import api from './api'

import ArtifactView from './views/ArtifactView.vue'
import ExhibitionView from './views/ExhibitionView.vue'
import HandoverView from './views/HandoverView.vue'
import GalleryView from './views/GalleryView.vue'
import TourView from './views/TourView.vue'
import MemberView from './views/MemberView.vue'

// ------------------------------------------------------------------
// 导航形态：径向环绕（radial surround）。
// 中心是博物馆 logo，六个业务模块以环形排布在四周，点击环上模块即切换。
// ------------------------------------------------------------------

const modules = [
  { path: '/artifact',   label: '藏品', sub: '文物 · 特征', comp: ArtifactView },
  { path: '/exhibition', label: '展览', sub: '展期 · 亮点', comp: ExhibitionView },
  { path: '/handover',   label: '交接', sub: '出展 · 返库', comp: HandoverView },
  { path: '/gallery',    label: '展厅', sub: '容量 · 状态', comp: GalleryView },
  { path: '/tour',       label: '导览', sub: '讲解 · 排期', comp: TourView },
  { path: '/member',     label: '会员', sub: '等级 · 积分', comp: MemberView }
]

const RADIUS = 210 // 环半径(px)
const ring = modules.map((m, i) => {
  const angle = -Math.PI / 2 + (i * 2 * Math.PI) / modules.length // 从正上方开始顺时针
  return {
    ...m,
    x: Math.cos(angle) * RADIUS,
    y: Math.sin(angle) * RADIUS
  }
})

const currentPath = ref(router.currentRoute.value.path)
const counts = ref({})

const currentView = computed(() => {
  const m = modules.find((mm) => mm.path === currentPath.value)
  return m ? m.comp : ArtifactView
})

async function loadCounts() {
  try {
    const [a, e, h, g, t, m] = await Promise.all([
      api.listArtifacts(), api.listExhibitions(), api.listHandovers(),
      api.listGalleries(), api.listTours(), api.listMembers()
    ])
    counts.value = { '/artifact': a.length, '/exhibition': e.length, '/handover': h.length, '/gallery': g.length, '/tour': t.length, '/member': m.length }
  } catch (e) { /* 计数失败不影响主流程 */ }
}

function go(path) {
  router.push(path)
}

watch(() => router.currentRoute.value.path, (p) => { currentPath.value = p })

onMounted(async () => {
  currentPath.value = router.currentRoute.value.path
  await loadCounts()
})
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand">
        <span class="brand-mark">博</span>
        <div class="brand-text">
          <div class="brand-title">博物馆 · 藏品展览与导览会员</div>
          <div class="brand-sub">径向环绕导航 · 赭石主题 · 全流程管理</div>
        </div>
      </div>
    </header>

    <div class="stage">
      <!-- 中心 logo -->
      <div class="core">
        <div class="core-mark">博</div>
        <div class="core-sub">MUSEUM</div>
      </div>

      <!-- 环形排布的模块 -->
      <button
        v-for="m in ring"
        :key="m.path"
        class="node"
        :class="{ on: currentPath === m.path }"
        :style="{ transform: `translate(calc(-50% + ${m.x}px), calc(-50% + ${m.y}px))` }"
        @click="go(m.path)"
      >
        <span class="node-label">{{ m.label }}</span>
        <span class="node-sub">{{ m.sub }}</span>
        <span class="node-count" v-if="counts[m.path] !== undefined">{{ counts[m.path] }}</span>
      </button>

      <!-- 业务内容面板 -->
      <section class="panel">
        <component :is="currentView" @changed="loadCounts" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.app-shell { min-height: 100vh; display: flex; flex-direction: column; }

.topbar {
  display: flex; align-items: center; gap: 16px;
  padding: 0 22px; height: 64px;
  background: linear-gradient(100deg, var(--primary-deep) 0%, var(--primary) 60%, var(--primary-soft) 100%);
  color: #fff; box-shadow: 0 2px 12px rgba(125, 102, 94, 0.22);
}
.brand { display: flex; align-items: center; gap: 12px; }
.brand-mark {
  width: 38px; height: 38px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  background: #fff; color: var(--primary-deep); font-weight: 700; font-size: 20px;
}
.brand-title { font-size: 17px; font-weight: 600; }
.brand-sub { font-size: 12px; opacity: 0.85; }

.stage {
  position: relative;
  flex: 1;
  margin: 28px auto;
  width: 760px; height: 620px;
}

/* 中心 logo */
.core {
  position: absolute; left: 50%; top: 50%;
  transform: translate(-50%, -50%);
  width: 130px; height: 130px; border-radius: 50%;
  background: radial-gradient(circle at 30% 30%, var(--primary-soft), var(--primary-deep));
  color: #fff; display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  box-shadow: 0 6px 22px rgba(125, 102, 94, 0.35);
  z-index: 2;
}
.core-mark { font-size: 46px; font-weight: 800; line-height: 1; }
.core-sub { font-size: 11px; letter-spacing: 3px; opacity: 0.8; margin-top: 4px; }

/* 环形节点 */
.node {
  position: absolute; left: 50%; top: 50%;
  width: 96px; height: 96px; border-radius: 50%;
  border: 2px solid var(--line); background: #fff; color: var(--ink);
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  cursor: pointer; transition: all 0.18s ease; gap: 2px;
  box-shadow: 0 3px 12px rgba(125, 102, 94, 0.12);
  z-index: 3; /* 六个节点时外环会经过右下角面板上方，保证节点不被面板遮住 */
}
.node:hover { border-color: var(--primary); box-shadow: 0 8px 22px rgba(125, 102, 94, 0.28); filter: brightness(1.04); }
.node.on {
  background: var(--primary); color: #fff; border-color: var(--primary-deep);
  box-shadow: 0 6px 18px rgba(125, 102, 94, 0.4);
}
.node-label { font-size: 17px; font-weight: 700; }
.node-sub { font-size: 11px; opacity: 0.8; }
.node-count {
  position: absolute; top: -8px; right: -8px;
  min-width: 22px; height: 22px; padding: 0 5px; border-radius: 11px;
  background: var(--primary-deep); color: #fff; font-size: 12px;
  display: flex; align-items: center; justify-content: center;
}

/* 业务内容面板：右下角卡片 */
.panel {
  position: absolute; right: 0; bottom: 0;
  width: 430px; height: 360px;
  background: #fff; border: 1px solid var(--line); border-radius: 14px;
  box-shadow: 0 8px 24px rgba(125, 102, 94, 0.14);
  padding: 14px 16px; overflow: auto;
}
</style>
