import { defineStore } from 'pinia'
import { get, post } from '../../shared/http'
import type { BasicForm } from './form'
import { cascadeToggles } from './moduleDeps'

export interface Basic {
  id: string
  name: string
  value: string
}

export interface Module {
  id: string
  name: string
  descrKey: string
  enable: boolean | null
}

export interface PageData {
  basicList: Basic[]
  moduleList: Module[]
  modulesOnDisk: string[]
  isLinux: boolean
}

const BASE = '/adminPage/basic'

export const useBasicStore = defineStore('basic', {
  state: () => ({
    basicList: [] as Basic[],
    moduleList: [] as Module[],
    modulesOnDisk: [] as string[],
    isLinux: false,
    loaded: false,
  }),
  actions: {
    async load() {
      const data = await get<PageData>(`${BASE}/pageData`)
      this.$patch({ ...data, loaded: true })
    },
    detail(id: string) {
      return get<Basic>(`${BASE}/detail`, { id })
    },
    async save(form: BasicForm) {
      await post(`${BASE}/addOver`, { id: form.id, name: form.name, value: form.value })
      await this.load()
    },
    async remove(ids: string[]) {
      await post(`${BASE}/del`, { id: ids.join(',') })
      await this.load()
    },
    async move(id: string, count: -1 | 1) {
      await post(`${BASE}/setOrder`, { id, count })
      await this.load()
    },
    // 回傳連帶切換的模組名稱，供畫面提示
    async toggleModule(name: string, enabling: boolean): Promise<string[]> {
      const byName = new Map(this.moduleList.map((m) => [m.name, m]))
      const enabled = new Set(this.moduleList.filter((m) => m.enable).map((m) => m.name))
      const cascaded = cascadeToggles(name, enabling, enabled, new Set(this.modulesOnDisk))
      for (const target of [name, ...cascaded]) {
        const module = byName.get(target)
        if (!module) {
          continue
        }
        await post(`${BASE}/setModuleEnable`, { id: module.id, enable: enabling ? 1 : 0 })
        module.enable = enabling
      }
      return cascaded
    },
  },
})
