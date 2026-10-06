<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const STATUS = ['草稿', '待接收', '已接收', '已拒收', '已收口', '已终止']
const TAG_TYPE = { 草稿: 'info', 待接收: 'warning', 已接收: 'primary', 已拒收: 'danger', 已收口: 'success', 已终止: 'info' }

const rows = ref([])
const artifacts = ref([])
const exhibitions = ref([])
const filterStatus = ref('')

const editDialog = ref(false)
const rejectDialog = ref(false)
const returnDialog = ref(false)
const detailDialog = ref(false)
const saving = ref(false)

const form = reactive({ id: null, artifactId: null, exhibitionId: null, deliverer: '', receiver: '', handoverTime: '', preCondition: '', note: '' })
const rejectForm = reactive({ id: null, no: '', reason: '' })
const returnForm = reactive({ id: null, no: '', handler: '', condition: '' })
const detail = ref({})

// 发起交接：只能从「库房」藏品中选，只能关联「未闭幕」展览
const warehouseArtifacts = computed(() => artifacts.value.filter((a) => a.status === '库房'))
const openExhibitions = computed(() => exhibitions.value.filter((e) => e.status !== '已闭幕'))

const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '—')

async function load() {
  const [hs, as, es] = await Promise.all([
    api.listHandovers(filterStatus.value ? { status: filterStatus.value } : {}),
    api.listArtifacts(),
    api.listExhibitions()
  ])
  rows.value = hs
  artifacts.value = as
  exhibitions.value = es
}

function openCreate() {
  Object.assign(form, { id: null, artifactId: null, exhibitionId: null, deliverer: '', receiver: '', handoverTime: '', preCondition: '', note: '' })
  editDialog.value = true
}
function openEdit(row) {
  Object.assign(form, {
    id: row.id, artifactId: row.artifactId, exhibitionId: row.exhibitionId,
    deliverer: row.deliverer || '', receiver: row.receiver || '',
    handoverTime: row.handoverTime || '', preCondition: row.preCondition || '', note: row.note || ''
  })
  editDialog.value = true
}

async function submit() {
  saving.value = true
  try {
    const payload = { ...form, handoverTime: form.handoverTime || null }
    if (form.id) {
      await api.updateHandover(form.id, payload)
      ElMessage.success('草稿已保存')
    } else {
      await api.createHandover(payload)
      ElMessage.success('交接单已创建（草稿）')
    }
    editDialog.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function act(row, kind) {
  try {
    if (kind === 'confirm') {
      await api.confirmHandover(row.id)
      ElMessage.success('已确认交出，藏品进入出展中')
    } else if (kind === 'receive') {
      await api.receiveHandover(row.id)
      ElMessage.success('已确认接收')
    }
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function openReject(row) {
  Object.assign(rejectForm, { id: row.id, no: row.handoverNo, reason: '' })
  rejectDialog.value = true
}
async function submitReject() {
  saving.value = true
  try {
    await api.rejectHandover(rejectForm.id, rejectForm.reason)
    ElMessage.success('已拒收，藏品归还库房管理')
    rejectDialog.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openReturn(row) {
  Object.assign(returnForm, { id: row.id, no: row.handoverNo, handler: '', condition: '' })
  returnDialog.value = true
}
async function submitReturn() {
  saving.value = true
  try {
    await api.returnHandover(returnForm.id, returnForm.handler, returnForm.condition)
    ElMessage.success('返库完成，交接单已收口')
    returnDialog.value = false
    await load()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

function openDetail(row) {
  detail.value = row
  detailDialog.value = true
}

onMounted(load)
</script>

<template>
  <div>
    <div class="v-head">
      <h3>藏品出展交接</h3>
      <div class="v-head-ops">
        <el-select v-model="filterStatus" size="small" style="width: 104px" placeholder="全部状态" clearable @change="load">
          <el-option v-for="s in STATUS" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" size="small" @click="openCreate">发起交接</el-button>
      </div>
    </div>

    <el-table :data="rows" size="small" height="270">
      <el-table-column prop="handoverNo" label="单号" width="98" />
      <el-table-column prop="artifactName" label="藏品" min-width="96" show-overflow-tooltip />
      <el-table-column prop="exTitle" label="展览" min-width="90" show-overflow-tooltip />
      <el-table-column prop="deliverer" label="交出人" width="66" />
      <el-table-column prop="receiver" label="接收人" width="66" />
      <el-table-column label="交接时间" width="100">
        <template #default="{ row }">{{ fmt(row.handoverTime) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="74">
        <template #default="{ row }">
          <el-tag size="small" :type="TAG_TYPE[row.status] || 'info'">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === '草稿'" size="small" text type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status === '草稿'" size="small" text type="success" @click="act(row, 'confirm')">交出</el-button>
          <el-button v-if="row.status === '待接收'" size="small" text type="success" @click="act(row, 'receive')">接收</el-button>
          <el-button v-if="row.status === '待接收'" size="small" text type="danger" @click="openReject(row)">拒收</el-button>
          <el-button v-if="row.status === '已接收'" size="small" text type="warning" @click="openReturn(row)">返库</el-button>
          <el-button size="small" text @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 发起 / 编辑草稿 -->
    <el-dialog v-model="editDialog" :title="form.id ? '编辑交接草稿' : '发起出展交接'" width="480px">
      <el-form :model="form" label-width="86px">
        <el-form-item label="藏品">
          <el-select v-model="form.artifactId" placeholder="仅可选库房中的藏品" filterable :disabled="!!form.id" style="width: 100%">
            <el-option v-for="a in warehouseArtifacts" :key="a.id" :label="`${a.artifactNo} ${a.name}`" :value="a.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="展览">
          <el-select v-model="form.exhibitionId" placeholder="仅可选未闭幕的展览" filterable style="width: 100%">
            <el-option v-for="e in openExhibitions" :key="e.id" :label="`${e.exCode} ${e.title}（${e.status}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="交出人"><el-input v-model="form.deliverer" placeholder="库房交出人" /></el-form-item>
        <el-form-item label="接收人"><el-input v-model="form.receiver" placeholder="展览方接收人" /></el-form-item>
        <el-form-item label="交接时间">
          <el-date-picker v-model="form.handoverTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="留空则确认交出时取当前时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="出展前状况"><el-input v-model="form.preCondition" type="textarea" :rows="2" placeholder="如：釉面完好，底座无裂痕" /></el-form-item>
        <el-form-item label="随附说明"><el-input v-model="form.note" type="textarea" :rows="2" placeholder="包装、附件、运输要求等（可空）" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="editDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submit">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 拒收 -->
    <el-dialog v-model="rejectDialog" :title="`拒收交接单 ${rejectForm.no}`" width="440px">
      <el-alert type="warning" :closable="false" show-icon title="拒收后藏品仍归库房管理，单据保留拒收轨迹，不可再恢复流转" style="margin-bottom: 10px" />
      <el-form label-width="86px">
        <el-form-item label="差异说明" required>
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="实物状况与记录不符之处（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="rejectDialog = false">取消</el-button>
        <el-button size="small" type="danger" :loading="saving" @click="submitReject">确认拒收</el-button>
      </template>
    </el-dialog>

    <!-- 返库确认 -->
    <el-dialog v-model="returnDialog" :title="`返库确认 ${returnForm.no}`" width="440px">
      <el-form label-width="86px">
        <el-form-item label="经办人" required><el-input v-model="returnForm.handler" placeholder="返库经办人" /></el-form-item>
        <el-form-item label="返库状况" required>
          <el-input v-model="returnForm.condition" type="textarea" :rows="3" placeholder="返库时实物状况（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="returnDialog = false">取消</el-button>
        <el-button size="small" type="primary" :loading="saving" @click="submitReturn">确认返库</el-button>
      </template>
    </el-dialog>

    <!-- 详情：两端看到的是同一份当前状态 -->
    <el-dialog v-model="detailDialog" :title="`交接单 ${detail.handoverNo || ''}`" width="480px">
      <el-descriptions :column="1" size="small" border>
        <el-descriptions-item label="状态"><el-tag size="small" :type="TAG_TYPE[detail.status] || 'info'">{{ detail.status }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="藏品">{{ detail.artifactNo }} {{ detail.artifactName }}</el-descriptions-item>
        <el-descriptions-item label="展览">{{ detail.exCode }} {{ detail.exTitle }}</el-descriptions-item>
        <el-descriptions-item label="交出人">{{ detail.deliverer || '—' }}</el-descriptions-item>
        <el-descriptions-item label="接收人">{{ detail.receiver || '—' }}</el-descriptions-item>
        <el-descriptions-item label="交接时间">{{ fmt(detail.handoverTime) }}</el-descriptions-item>
        <el-descriptions-item label="出展前状况">{{ detail.preCondition || '—' }}</el-descriptions-item>
        <el-descriptions-item label="随附说明">{{ detail.note || '—' }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.receivedAt" label="接收时间">{{ fmt(detail.receivedAt) }}</el-descriptions-item>
        <template v-if="detail.status === '已拒收'">
          <el-descriptions-item label="拒收时间">{{ fmt(detail.rejectedAt) }}</el-descriptions-item>
          <el-descriptions-item label="差异说明">{{ detail.rejectReason || '—' }}</el-descriptions-item>
        </template>
        <template v-if="detail.status === '已收口'">
          <el-descriptions-item label="返库经办人">{{ detail.returnHandler || '—' }}</el-descriptions-item>
          <el-descriptions-item label="返库状况">{{ detail.returnCondition || '—' }}</el-descriptions-item>
          <el-descriptions-item label="返库时间">{{ fmt(detail.returnedAt) }}</el-descriptions-item>
        </template>
        <el-descriptions-item v-if="detail.status === '已终止'" label="终止原因">{{ detail.terminateReason || '—' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<style scoped>
.v-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.v-head h3 { margin: 0; font-size: 15px; color: var(--primary-deep); }
.v-head-ops { display: flex; gap: 8px; align-items: center; }
</style>
