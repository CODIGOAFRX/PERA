#!/usr/bin/env node
/**
 * Compila la demo pública (frontend en modo «demo») y la copia a la página del proyecto en el
 * portfolio, donde se sirve en /proyecto/pera-erp/app/.
 *
 *   node scripts/build-portfolio-demo.mjs [carpeta destino]
 *
 * Destino por defecto: ../../portfolio/portfolio-react/proyecto/pera-erp/app respecto al
 * repositorio (o la variable PERA_PORTFOLIO_APP_DIR). La carpeta destino se vacía antes de copiar.
 */
import { execSync } from 'node:child_process'
import { cpSync, existsSync, rmSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const repo = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const target = resolve(process.argv[2] ?? process.env.PERA_PORTFOLIO_APP_DIR ?? resolve(repo, '../../portfolio/portfolio-react/proyecto/pera-erp/app'))

if (!existsSync(dirname(target))) {
  console.error(`No existe ${dirname(target)}: crea antes la página del proyecto en el portfolio.`)
  process.exit(1)
}

execSync('npm run build:demo', { cwd: resolve(repo, 'frontend'), stdio: 'inherit', shell: true })
rmSync(target, { recursive: true, force: true })
cpSync(resolve(repo, 'frontend/dist-demo'), target, { recursive: true })
console.log(`Demo copiada a ${target}`)
