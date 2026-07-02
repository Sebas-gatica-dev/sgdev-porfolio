import { spawn } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = dirname(fileURLToPath(import.meta.url))
const root = dirname(scriptDir)
const args = process.argv.slice(2)
const isWindows = process.platform === 'win32'

const command = isWindows ? 'powershell.exe' : 'bash'
const commandArgs = isWindows
  ? ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(scriptDir, 'portfolio.ps1'), ...args]
  : [join(scriptDir, 'portfolio.sh'), ...args]

const child = spawn(command, commandArgs, {
  cwd: root,
  stdio: 'inherit',
  windowsHide: true,
})

child.on('error', (error) => {
  console.error(`No pude ejecutar ${command}: ${error.message}`)
  process.exit(1)
})

child.on('exit', (code, signal) => {
  if (signal) {
    process.kill(process.pid, signal)
    return
  }
  process.exit(code ?? 0)
})
