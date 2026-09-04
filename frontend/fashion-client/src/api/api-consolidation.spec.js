import { readFileSync, readdirSync } from 'node:fs'
import { join } from 'node:path'
import { describe, expect, it } from 'vitest'


describe('client API modules', () => {
  it('all reuse the single request instance', () => {
    const apiDirectory = join(process.cwd(), 'src', 'api')
    const modules = readdirSync(apiDirectory).filter(name => name.endsWith('.js') && !name.endsWith('.spec.js'))
    expect(modules).toHaveLength(12)

    for (const moduleName of modules) {
      const source = readFileSync(join(apiDirectory, moduleName), 'utf8')
      expect(source, moduleName).toMatch(/from ['"]@\/utils\/request['"]/)
      expect(source, moduleName).not.toMatch(/from ['"]axios['"]|axios\.create|interceptors\./)
    }
  })
})
