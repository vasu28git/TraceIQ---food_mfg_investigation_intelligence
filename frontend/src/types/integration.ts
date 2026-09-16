export interface Integration {
  id: number
  name: string
  type?: string | null
  status?: string | null
  configuration?: string | null
  organisation?: { orgId: number; name?: string }
  createdAt?: string
  updatedAt?: string
}

export interface IntegrationCreateRequest {
  name: string
  type?: string | null
  status?: string | null
  configuration?: string | null
}

export interface IntegrationUpdateRequest {
  name?: string
  type?: string | null
  status?: string | null
  configuration?: string | null
}
