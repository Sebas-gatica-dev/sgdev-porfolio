import { spawn } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const root = dirname(scriptDir)
const backendDir = join(root, 'backend')
const wrapper = process.platform === 'win32' ? 'mvnw.cmd' : './mvnw'
const args = process.argv.slice(2)

const child = spawn(wrapper, args, {
  cwd: backendDir,
  stdio: 'inherit',
  shell: process.platform === 'win32',
  windowsHide: true,
})

child.on('error', (error) => {
  console.error(`No pude ejecutar ${wrapper}: ${error.message}`)
  process.exit(1)
})

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }
  process.exit(code ?? 0)
})
