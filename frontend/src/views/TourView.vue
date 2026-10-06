<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const allTours = ref([])
const galleries = ref([])
const dialog = ref(false)
const saving = ref(false)
const STATUS = ['待开始', '进行中', '已完成']
// 占用展厅容量的状态，与后端 Dict.TOUR_OCCUPYING 口径一致：已完成不占名额
const OCCUPYING = ['待开始', '进行中']

// 列表筛选：展厅 + 状态可组合
const filter = reactive({ galleryId: null, status: '' })

const form = reactive({ id: null, galleryCode: '', title: '', guideName: '', scheduledAt: '', status: '待开始' })

/** 某展厅当前已用场次（仅待开始 / 进行中），用于排期表单的名额提示 */
const usedOfGallery = (galleryId) =>
  allTours.value.filter(t => t.galleryId === galleryId && OCCUPYING.includes(t.status)).length

const galleryOptions = computed(() => galleries.value.map(g => {
  const used = usedOfGallery(g.id)
  return {
    ...g,
    used,
    full: used >= g.capacity,
    label: `${g.name}（已用 ${used}/${g.capacity}）`
  }
}))

async function load() {
  rows.value = await api.listTours(filter.galleryId, filter.status || undefined)
}
/** 排期表单需要全量导览来计算每个展厅的已用场次（不能用筛选后的 rows） */
async function loadGalleriesAndUsage() {
  const [gs, ts] = await Promise.all([api.listGalleries(), api.listTours()])
  galleries.value = gs
  allTours.value = ts
}
async function onFilterChange() {
  await load()
}
async function openCreate() {
  Object.assign(form, { id: null, galleryCode: '', title: '', guideName: '', scheduledAt: '', status: '待开始' })
  // 每次打开都重新拉取展厅与全部导览，保证已用场次是最新结果（别人刚结束的场次已释放）
  await loadGalleriesAndUsage()
  dialog.value = true
}
async function submit() {
  saving.value = true
  try {
    await api.createTour({ ...form })
    ElMessage.success('导览已排期')
    dialog.value = false
    await Promise.all([load(), loadGalleriesAndUsage()])
    emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function act(row, kind, target) {
  try {
    if (kind === 'start') await api.startTour(row.id)
    else if (kind === 'finish') await api.finishTour(row.id)
    else if (kind === 'transfer') await api.transferTour(row.id, target)
    ElMessage.success('已更新')
    // 无论成功失败都以服务端数据为准重新拉取，避免本地状态与服务端不一致
    await Promise.all([load(), loadGalleriesAndUsage()])
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
    await Promise.all([load(), loadGalleriesAndUsage()])
  }
}

onMounted(async () => {
  await Promise.all([load(), loadGalleriesAndUsage()])
})
</script>

<template>
  <div>
    <div class="v-head">
      <h3>导览管理</h3>
      <el-button type="primary" size="small" @click="openCreate">排期导览</el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.galleryId" placeholder="全部展厅" size="small" clearable @change="onFilterChange">
        <el-option v-for="g in galleries" :key="g.id" :label="g.name" :value="g.id" />
      </el-select>
      <el-select v-model="filter.status" placeholder="全部状态" size="small" clearable @change="onFilterChange">
        <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
      </el-select>
    </div>

    <el-table :data="rows" size="small" height="220">
      <el-table-column prop="tourNo" label="编号" width="100" />
      <el-table-column prop="galleryName" label="展厅" width="86" />
      <el-table-column prop="title" label="主题" min-width="100" show-overflow-tooltip />
      <el-table-column prop="guideName" label="讲解员" width="70" />
      <el-table-column label="状态" width="116">
        <template #default="{ row }">
          <el-select
            :model-value="row.status"
            size="small"
            @change="(v) => act(row, 'transfer', v)"
          >
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="96">
        <template #default="{ row }">
          <el-button v-if="row.status === '待开始'" size="small" text type="success" @click="act(row, 'start')">开始</el-button>
          <el-button v-if="row.status === '进行中'" size="small" text type="primary" @click="act(row, 'finish')">结束</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="排期导览" width="440px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="展厅">
          <el-select v-model="form.galleryCode" placeholder="选择展厅" filterable>
            <el-option
              v-for="g in galleryOptions"
              :key="g.id"
              :label="g.full ? `${g.label} · 已满` : g.label"
              :value="g.galleryCode"
              :disabled="g.full"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="主题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="讲解员"><el-input v-model="form.guideName" /></el-form-item>
        <el-form-item label="时间"><el-date-picker v-model="form.scheduledAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
.filter-bar { display: flex; gap: 8px; margin-bottom: 8px; }
.filter-bar .el-select { flex: 1; }
</style>
