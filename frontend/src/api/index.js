import axios from 'axios'

const http = axios.create({ baseURL: '/api', timeout: 20000 })

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const message = error?.response?.data?.message || error?.message || '请求失败'
    return Promise.reject(new Error(message))
  }
)

async function call(promise) {
  const response = await promise
  const body = response.data
  if (!body || body.ok === false) {
    throw new Error((body && body.message) || '请求失败')
  }
  return body.data
}

const params = (o) => ({ params: o })

export default {
  // ---------------- 藏品 ----------------
  listArtifacts: (status) => call(http.get('/artifacts', params(status ? { status } : {}))),
  createArtifact: (d) => call(http.post('/artifacts', d)),
  updateArtifact: (id, d) => call(http.put(`/artifacts/${id}`, d)),
  transferArtifact: (id, target) => call(http.post(`/artifacts/${id}/transfer`, null, params({ target }))),

  // ---------------- 展览 ----------------
  listExhibitions: (status) => call(http.get('/exhibitions', params(status ? { status } : {}))),
  createExhibition: (d) => call(http.post('/exhibitions', d)),
  updateExhibition: (id, d) => call(http.put(`/exhibitions/${id}`, d)),
  transferExhibition: (id, target) => call(http.post(`/exhibitions/${id}/transfer`, null, params({ target }))),

  // ---------------- 出展交接 ----------------
  listHandovers: (filter) => call(http.get('/handovers', params(filter || {}))),
  currentHandover: (artifactId) => call(http.get('/handovers/current', params({ artifactId }))),
  createHandover: (d) => call(http.post('/handovers', d)),
  updateHandover: (id, d) => call(http.put(`/handovers/${id}`, d)),
  confirmHandover: (id) => call(http.post(`/handovers/${id}/confirm`)),
  receiveHandover: (id) => call(http.post(`/handovers/${id}/receive`)),
  rejectHandover: (id, reason) => call(http.post(`/handovers/${id}/reject`, null, params({ reason }))),
  returnHandover: (id, handler, condition) => call(http.post(`/handovers/${id}/return`, null, params({ handler, condition }))),

  // ---------------- 展厅 ----------------
  listGalleries: (status) => call(http.get('/galleries', params(status ? { status } : {}))),
  createGallery: (d) => call(http.post('/galleries', d)),
  updateGallery: (id, d) => call(http.put(`/galleries/${id}`, d)),
  transferGallery: (id, target) => call(http.post(`/galleries/${id}/transfer`, null, params({ target }))),

  // ---------------- 导览 ----------------
  listTours: (galleryId, status) => call(http.get('/tours', params(
    galleryId ? { galleryId, ...(status ? { status } : {}) } : (status ? { status } : {})))),
  createTour: (d) => call(http.post('/tours', d)),
  startTour: (id) => call(http.post(`/tours/${id}/start`)),
  finishTour: (id) => call(http.post(`/tours/${id}/finish`)),
  transferTour: (id, target) => call(http.post(`/tours/${id}/transfer`, null, params({ target }))),

  // ---------------- 会员 ----------------
  listMembers: (level) => call(http.get('/members', params(level ? { level } : {}))),
  createMember: (d) => call(http.post('/members', d)),
  updateMember: (id, d) => call(http.put(`/members/${id}`, d)),
  addPoints: (id, delta) => call(http.post(`/members/${id}/points`, null, params({ delta }))),

  // ---------------- 积分调整申请 ----------------
  listPointAdjustments: (filter) => call(http.get('/point-adjustments', params(filter || {}))),
  createPointAdjustment: (d) => call(http.post('/point-adjustments', d)),
  updatePointAdjustment: (id, d) => call(http.put(`/point-adjustments/${id}`, d)),
  withdrawPointAdjustment: (id, operator) => call(http.post(`/point-adjustments/${id}/withdraw`, null, params({ operator }))),
  approvePointAdjustment: (id, reviewer, comment) => call(http.post(`/point-adjustments/${id}/approve`, null, params({ reviewer, comment }))),
  rejectPointAdjustment: (id, reviewer, comment) => call(http.post(`/point-adjustments/${id}/reject`, null, params({ reviewer, comment })))
}
