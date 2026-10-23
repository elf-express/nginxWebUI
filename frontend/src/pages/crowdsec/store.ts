import { defineStore } from 'pinia'
import { get, post } from '../../shared/http'

export type SecretSource = 'SECRETS_FILE' | 'ENV' | 'NONE'

export interface SecretView {
  masked: string
  source: SecretSource
  configured: boolean
  file: string
  env: string
}

export interface CrowdSecState {
  lastOk: number
  checkedAt: number
  lapiOk: boolean
  appsecOk: boolean
}

export interface CrowdSecConfig {
  lapiUrl: string
  lapiUrlEnv: string
  bouncerKey: SecretView
  machineId: string
  machinePassword: SecretView
  graceSeconds: number
  state?: CrowdSecState | null
  configured: boolean
}

export type CheckResult = 'ok' | 'invalid' | 'unreachable' | 'notConfigured' | 'unchecked'

export interface Probe {
  lapi: CheckResult
  bouncerKey: CheckResult
  machine: CheckResult
}

const BASE = '/adminPage/crowdsec'

export const useCrowdSecStore = defineStore('crowdsec', {
  state: () => ({
    config: null as CrowdSecConfig | null,
    probe: null as Probe | null,
    testing: false,
  }),
  actions: {
    async load() {
      this.config = await get<CrowdSecConfig>(`${BASE}/getConfig`)
    },
    async test() {
      this.testing = true
      try {
        this.probe = await get<Probe>(`${BASE}/status`)
      } finally {
        this.testing = false
      }
    },
    async saveGrace(seconds: number) {
      await post(`${BASE}/saveConfig`, { graceSeconds: seconds })
      if (this.config) {
        this.config.graceSeconds = seconds
      }
    },
  },
})
