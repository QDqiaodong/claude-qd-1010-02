<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const rows = ref([])
const dialog = ref(false)
const saving = ref(false)
const STATUS = ['筹展', '展出中', '已闭幕']
const TAG_TYPE = { 草稿: 'info', 待接收: 'warning', 已接收: 'primary', 已拒收: 'danger', 已收口: 'success', 已终止: 'info' }
const newHl = ref('')

const form = reactive({
  id: null, exCode: '', title: '', startDate: '', endDate: '', status: '筹展', highlights: []
})

// 从展览端定位当前交接：该展览的全部交接单（新的在前）
const handoverDialog = ref(false)
const handoverRows = ref([])
const handoverTitle = ref('')
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function openHandovers(row) {
  try {
    handoverRows.value = await api.listHandovers({ exhibitionId: row.id })
    handoverTitle.value = `${row.exCode} · ${row.title}`
    handoverDialog.value = true
  } catch (e) { ElMessage.error(e.message) }
}

async function load() { rows.value = await api.listExhibitions() }
function openCreate() {
  Object.assign(form, { id: null, exCode: '', title: '', startDate: '', endDate: '', status: '筹展', highlights: [] })
  newHl.value = ''
  dialog.value = true
}
function addHl() {
  const v = newHl.value.trim()
  if (v) { form.highlights.push(v); newHl.value = '' }
}
function removeHl(i) { form.highlights.splice(i, 1) }

async function submit() {
  saving.value = true
  try {
    const payload = { ...form }
    if (form.id) { await api.updateExhibition(form.id, payload); ElMessage.success('已更新') }
    else { await api.createExhibition(payload); ElMessage.success('展览已创建') }
    dialog.value = false
    await load(); emit('changed')
  } catch (e) { ElMessage.error(e.message) }
  finally { saving.value = false }
}
async function transfer(row, target) {
  try { await api.transferExhibition(row.id, target); ElMessage.success('状态已更新'); await load(); emit('changed') }
  catch (e) { ElMessage.error(e.message) }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>展览管理</h3>
      <el-button type="primary" size="small" @click="openCreate">新建展览</el-button>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="exCode" label="编号" width="92" />
      <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
      <el-table-column prop="startDate" label="开始" width="92" />
      <el-table-column prop="endDate" label="结束" width="92" />
      <el-table-column label="亮点" min-width="110">
        <template #default="{ row }">
          <span v-for="(h, i) in (row.highlights || [])" :key="i" class="tag">{{ h }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-select :model-value="row.status" size="small" @change="(v) => transfer(row, v)">
            <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="交接" width="60">
        <template #default="{ row }">
          <el-button size="small" text type="primary" @click="openHandovers(row)">交接</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" title="新建展览" width="460px">
      <el-form :model="form" label-width="72px">
        <el-form-item label="编号"><el-input v-model="form.exCode" placeholder="如 EX-2026-08" /></el-form-item>
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="开始"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="结束"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status"><el-option v-for="s in STATUS" :key="s" :label="s" :value="s" /></el-select>
        </el-form-item>
        <el-form-item label="亮点">
          <div class="hl">
            <span v-for="(h, i) in form.highlights" :key="i" class="tag">{{ h }} <i @click="removeHl(i)">×</i></span>
            <el-input v-model="newHl" size="small" style="width:160px" placeholder="输入后回车" @keyup.enter="addHl" />
            <el-button size="small" @click="addHl">添加</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="dialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 该展览的交接记录（与藏品端看到的是同一份当前状态） -->
    <el-dialog v-model="handoverDialog" :title="`交接记录 · ${handoverTitle}`" width="520px">
      <el-table :data="handoverRows" size="small" max-height="300" empty-text="暂无交接记录">
        <el-table-column prop="handoverNo" label="单号" width="100" />
        <el-table-column prop="artifactName" label="藏品" min-width="110" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="TAG_TYPE[row.status] || 'info'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="交接时间" width="140">
          <template #default="{ row }">{{ fmt(row.handoverTime) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
.tag { display: inline-block; margin: 1px 3px; padding: 1px 6px; background: var(--primary-wash); border-radius: 8px; font-size: 11px; }
.tag i { cursor: pointer; margin-left: 3px; color: var(--primary-deep); }
.hl { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
</style>
