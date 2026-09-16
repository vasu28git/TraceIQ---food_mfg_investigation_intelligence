export interface FileRecord {
  id: number
  organisation?: { orgId: number; name?: string }
  integration?: { id: number; name?: string } | null
  sourceType: string
  originalName: string
  storageKey?: string | null
  contentType?: string | null
  size?: number | null
  status?: string | null
  receivedAt?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface FileCreateRequest {
  sourceType: string
  originalName: string
  storageKey?: string | null
  contentType?: string | null
  size?: number | null
  status?: string | null
  integration?: { id: number } | null
}

export interface FileUpdateRequest {
  originalName?: string
  storageKey?: string | null
  contentType?: string | null
  size?: number | null
  status?: string | null
  integration?: { id: number } | null
}
