<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const galleries = ref([])
const dialog = ref(false)
const saving = ref(false)
const STATUS = ['待开始', '进行中', '已完成']

const form = reactive({ id: null, galleryCode: '', title: '', guideName: '', scheduledAt: '', status: '待开始' })

async function load() {
  rows.value = await api.listTours()
  galleries.value = await api.listGalleries()
}
function openCreate() {
  Object.assign(form, { id: null, galleryCode: '', title: '', guideName: '', scheduledAt: '', status: '待开始' })
  dialog.value = true
}
async function submit() {
  saving.value = true
  try {
    await api.createTour({ ...form })
    ElMessage.success('导览已排期')
    dialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function act(row, kind) {
  try {
    if (kind === 'start') await api.startTour(row.id)
    else if (kind === 'finish') await api.finishTour(row.id)
    else if (kind === 'transfer') await api.transferTour(row.id, row.status)
    ElMessage.success('已更新'); await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>导览管理</h3>
      <el-button type="primary" size="small" @click="openCreate">排期导览</el-button>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="tourNo" label="编号" width="100" />
      <el-table-column prop="galleryName" label="展厅" width="96" />
      <el-table-column prop="title" label="主题" min-width="110" show-overflow-tooltip />
      <el-table-column prop="guideName" label="讲解员" width="80" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-select :model-value="row.status" size="small" @change="(v) => { row.status = v; act(row, 'transfer') }">
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
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
            <el-option v-for="g in galleries" :key="g.id" :label="`${g.name}（容量${g.capacity}）`" :value="g.galleryCode" />
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
</style>
