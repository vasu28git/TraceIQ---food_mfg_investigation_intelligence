export interface ConfigurationDefinition {
  id: number
  key: string
  description?: string
  type?: string // INTEGER, ENUM, BOOLEAN, STRING
  allowedValues?: string[]
  defaultValue?: string
}

export interface Configuration {
  id: number
  organisation?: { orgId: number; name?: string }
  definition: ConfigurationDefinition
  value?: string | null
  createdAt?: string
  updatedAt?: string
}

export interface ConfigurationCreateRequest {
  definition: { key: string }
  value?: string | null
}

export interface ConfigurationUpdateRequest {
  value?: string | null
}
