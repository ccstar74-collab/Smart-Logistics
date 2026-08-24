import http from 'node:http'
import { readFile } from 'node:fs/promises'

const PORT = Number(process.env.MOCK_API_PORT || 3001)
const source = JSON.parse(await readFile(new URL('../src/mock/data.json', import.meta.url), 'utf8'))
let vehicles = structuredClone(source.vehicles || [])
let cargos = structuredClone(source.cargos || [])

const send = (res, status, body) => {
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS'
  })
  res.end(JSON.stringify(body))
}

const bodyOf = req => new Promise((resolve, reject) => {
  let raw = ''
  req.on('data', chunk => { raw += chunk; if (raw.length > 1_000_000) reject(new Error('请求体过大')) })
  req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}) } catch { reject(new Error('JSON 格式错误')) } })
  req.on('error', reject)
})

const nextId = list => Math.max(0, ...list.map(item => Number(item.id ?? item.vehicleId) || 0)) + 1
const idFrom = (pathname, resource) => pathname.match(new RegExp(`^/api/v1/${resource}/([^/]+)$`))?.[1]

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return send(res, 204, {})
  const url = new URL(req.url, `http://${req.headers.host}`)
  const { pathname } = url
  try {
    if (req.method === 'GET' && pathname === '/health') return send(res, 200, { status: 'ok', mode: 'mock' })
    if (req.method === 'POST' && pathname === '/api/v1/auth/login') {
      const input = await bodyOf(req)
      if (!input.username || !input.password) return send(res, 422, { detail: '请输入用户名和密码' })
      return send(res, 200, { access_token: 'local-mock-token', token_type: 'bearer' })
    }
    if (req.method === 'GET' && pathname === '/api/v1/users/me') return send(res, 200, source.users?.[0] || { id: 1, name: '演示用户', role: 'ADMIN' })

    if (req.method === 'GET' && pathname === '/api/v1/vehicles') {
      const status = url.searchParams.get('status')
      return send(res, 200, { items: status ? vehicles.filter(v => v.status === status) : vehicles, total: vehicles.length })
    }
    if (req.method === 'POST' && pathname === '/api/v1/vehicles') {
      const input = await bodyOf(req); const item = { ...input, vehicleId: nextId(vehicles) }; vehicles.push(item); return send(res, 201, item)
    }
    const vehicleId = idFrom(pathname, 'vehicles')
    if (vehicleId) {
      const index = vehicles.findIndex(v => String(v.vehicleId ?? v.id) === vehicleId)
      if (index < 0) return send(res, 404, { detail: '车辆不存在' })
      if (req.method === 'GET') return send(res, 200, vehicles[index])
      if (req.method === 'PUT') { vehicles[index] = { ...vehicles[index], ...await bodyOf(req) }; return send(res, 200, vehicles[index]) }
      if (req.method === 'DELETE') { vehicles.splice(index, 1); return send(res, 200, { message: '删除成功' }) }
    }

    if (req.method === 'GET' && pathname === '/api/v1/cargos') return send(res, 200, { items: cargos, total: cargos.length })
    if (req.method === 'POST' && pathname === '/api/v1/cargos') {
      const input = await bodyOf(req); const item = { ...input, id: nextId(cargos) }; cargos.push(item); return send(res, 201, item)
    }
    const cargoId = idFrom(pathname, 'cargos')
    if (cargoId) {
      const item = cargos.find(c => String(c.id ?? c.cargoId) === cargoId)
      return item && req.method === 'GET' ? send(res, 200, item) : send(res, 404, { detail: '货物不存在' })
    }
    return send(res, 404, { detail: `Mock API 未实现：${req.method} ${pathname}` })
  } catch (error) { return send(res, 400, { detail: error.message }) }
})

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Local Mock API: http://127.0.0.1:${PORT}`)
  console.log('Demo login: any non-empty username and password')
})
