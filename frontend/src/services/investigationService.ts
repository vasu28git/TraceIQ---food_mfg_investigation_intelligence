import { apiClient } from '../api/client'
import type {
  Investigation, CreateInvestigationRequest,
  Complaint, CreateComplaintRequest, CreateInvestigationFromComplaintRequest,
  InvestigationEvidence, LinkEvidenceRequest,
  InvestigationEvidenceAssessmentRequest,
  ReviewEvidenceRequest,
  InvestigationFinding, InvestigationFindingRequest,
  InvestigationConclusion, InvestigationConclusionRequest,
  InvestigationAction, InvestigationActionRequest,
  FindingEvidenceTraceability,
  InvestigationEvidenceSummary,
  InvestigationNote, CreateNoteRequest, UpdateNoteRequest,
  InvestigationTimelineResponse,
  InvestigationReport,
  PageResponse,
} from '../types/investigation'

// Investigation — Incident is domain alias for Investigation persistence
export async function createInvestigation(payload: CreateInvestigationRequest): Promise<Investigation> {
  const body: Record<string, unknown> = {
    investigationKey: payload.investigationKey.trim(),
    title: payload.title.trim(),
    description: payload.description?.trim() || null,
  }
  if (payload.batchReference !== undefined) body.batchReference = payload.batchReference?.trim() || null
  if (payload.productReference !== undefined) body.productReference = payload.productReference?.trim() || null
  if (payload.orderReference !== undefined) body.orderReference = payload.orderReference?.trim() || null
  if (payload.incidentStart !== undefined) body.incidentStart = payload.incidentStart || null
  if (payload.incidentEnd !== undefined) body.incidentEnd = payload.incidentEnd || null
  const res = await apiClient.post<Investigation>('/investigations', body)
  return res.data
}

// Alias for domain terminology
export const createIncident = createInvestigation
export async function getIncident(id: number): Promise<Investigation> {
  return getInvestigation(id)
}

export async function getInvestigations(page = 0, size = 20): Promise<PageResponse<Investigation>> {
  const res = await apiClient.get<PageResponse<Investigation>>('/investigations', { params: { page, size } })
  return res.data
}

export async function getInvestigation(id: number): Promise<Investigation> {
  const res = await apiClient.get<Investigation>(`/investigations/${id}`)
  return res.data
}

export async function activateInvestigation(id: number): Promise<Investigation> {
  const res = await apiClient.post<Investigation>(`/investigations/${id}/activate`)
  return res.data
}

export async function completeInvestigation(id: number): Promise<Investigation> {
  const res = await apiClient.post<Investigation>(`/investigations/${id}/complete`)
  return res.data
}

export async function archiveInvestigation(id: number): Promise<Investigation> {
  const res = await apiClient.post<Investigation>(`/investigations/${id}/archive`)
  return res.data
}

// Complaint
export async function createComplaint(payload: CreateComplaintRequest): Promise<Complaint> {
  const body: Record<string, unknown> = {
    complaintKey: payload.complaintKey.trim(),
    title: payload.title.trim(),
    description: payload.description?.trim() || null,
    batchReference: payload.batchReference?.trim() || null,
    externalReference: payload.externalReference?.trim() || null,
  }
  if (payload.raisedAt) body.raisedAt = payload.raisedAt
  const res = await apiClient.post<Complaint>('/complaints', body)
  return res.data
}

export async function getComplaint(id: number): Promise<Complaint> {
  const res = await apiClient.get<Complaint>(`/complaints/${id}`)
  return res.data
}

export async function createInvestigationFromComplaint(complaintId: number, payload: CreateInvestigationFromComplaintRequest): Promise<Investigation> {
  const res = await apiClient.post<Investigation>(`/complaints/${complaintId}/investigation`, {
    investigationKey: payload.investigationKey.trim(),
    title: payload.title.trim(),
    description: payload.description?.trim() || null,
  })
  return res.data
}

// Evidence
export async function linkEvidence(investigationId: number, payload: LinkEvidenceRequest): Promise<InvestigationEvidence> {
  const res = await apiClient.post<InvestigationEvidence>(`/investigations/${investigationId}/evidence`, { stableId: payload.stableId.trim() })
  return res.data
}

export async function listInvestigationEvidence(
  investigationId: number,
  page = 0,
  size = 20,
  params?: {
    search?: string;
    sourceType?: string;
    status?: string;
    sort?: string;
    relevance?: string;
    reviewStatus?: string;
  }
): Promise<PageResponse<InvestigationEvidence>> {
  const query: Record<string, unknown> = { page, size }
  if (params?.search) query.search = params.search
  if (params?.sourceType) query.sourceType = params.sourceType
  if (params?.status) query.status = params.status
  if (params?.sort) query.sort = params.sort
  if (params?.relevance) query.relevance = params.relevance
  if (params?.reviewStatus) query.reviewStatus = params.reviewStatus
  const res = await apiClient.get<PageResponse<InvestigationEvidence>>(`/investigations/${investigationId}/evidence`, { params: query })
  return res.data
}

export async function reviewInvestigationEvidence(
  investigationId: number,
  stableId: string,
  payload: ReviewEvidenceRequest
): Promise<InvestigationEvidence> {
  const res = await apiClient.patch<InvestigationEvidence>(
    `/investigations/${investigationId}/evidence/${encodeURIComponent(stableId)}/review`,
    payload
  )
  return res.data
}

export async function getInvestigationEvidenceDetail(investigationId: number, stableId: string): Promise<InvestigationEvidence> {
  const res = await apiClient.get<InvestigationEvidence>(`/investigations/${investigationId}/evidence/${encodeURIComponent(stableId)}`)
  return res.data
}

export async function getInvestigationEvidenceSummary(investigationId: number): Promise<InvestigationEvidenceSummary> {
  const res = await apiClient.get<InvestigationEvidenceSummary>(`/investigations/${investigationId}/evidence/summary`)
  return res.data
}

export async function saveInvestigationEvidenceAssessment(investigationId: number, stableId: string, payload: InvestigationEvidenceAssessmentRequest): Promise<InvestigationEvidence> {
  const res = await apiClient.put<InvestigationEvidence>(`/investigations/${investigationId}/evidence/${encodeURIComponent(stableId)}/assessment`, payload)
  return res.data
}

export async function listInvestigationFindings(investigationId: number): Promise<InvestigationFinding[]> {
  const res = await apiClient.get<InvestigationFinding[]>(`/investigations/${investigationId}/findings`)
  return res.data
}

export async function createInvestigationFinding(investigationId: number, payload: InvestigationFindingRequest): Promise<InvestigationFinding> {
  const res = await apiClient.post<InvestigationFinding>(`/investigations/${investigationId}/findings`, payload)
  return res.data
}

export async function updateInvestigationFinding(investigationId: number, findingId: number, payload: InvestigationFindingRequest): Promise<InvestigationFinding> {
  const res = await apiClient.put<InvestigationFinding>(`/investigations/${investigationId}/findings/${findingId}`, payload)
  return res.data
}

export async function getFindingEvidenceTraceability(investigationId: number, findingId: number): Promise<FindingEvidenceTraceability> {
  const res = await apiClient.get<FindingEvidenceTraceability>(`/investigations/${investigationId}/findings/${findingId}/traceability`)
  return res.data
}

export async function getInvestigationConclusion(investigationId: number): Promise<InvestigationConclusion | null> {
  const res = await apiClient.get<InvestigationConclusion | null>(`/investigations/${investigationId}/conclusion`, { validateStatus: status => status === 200 || status === 204 })
  return res.status === 204 ? null : res.data
}

export async function createInvestigationConclusion(investigationId: number, payload: InvestigationConclusionRequest): Promise<InvestigationConclusion> {
  const res = await apiClient.post<InvestigationConclusion>(`/investigations/${investigationId}/conclusion`, payload)
  return res.data
}

export async function updateInvestigationConclusion(investigationId: number, payload: InvestigationConclusionRequest): Promise<InvestigationConclusion> {
  const res = await apiClient.put<InvestigationConclusion>(`/investigations/${investigationId}/conclusion`, payload)
  return res.data
}

export async function listInvestigationActions(investigationId: number): Promise<InvestigationAction[]> { return (await apiClient.get<InvestigationAction[]>(`/investigations/${investigationId}/actions`)).data }
export async function createInvestigationAction(investigationId: number, payload: InvestigationActionRequest): Promise<InvestigationAction> { return (await apiClient.post<InvestigationAction>(`/investigations/${investigationId}/actions`, payload)).data }
export async function updateInvestigationAction(investigationId: number, actionId: number, payload: InvestigationActionRequest): Promise<InvestigationAction> { return (await apiClient.put<InvestigationAction>(`/investigations/${investigationId}/actions/${actionId}`, payload)).data }

export async function unlinkEvidence(investigationId: number, stableId: string): Promise<void> {
  await apiClient.delete(`/investigations/${investigationId}/evidence/${encodeURIComponent(stableId)}`)
}


// Notes
export async function createNote(investigationId: number, payload: CreateNoteRequest): Promise<InvestigationNote> {
  const res = await apiClient.post<InvestigationNote>(`/investigations/${investigationId}/notes`, { content: payload.content })
  return res.data
}

export async function listNotes(investigationId: number, page = 0, size = 20): Promise<PageResponse<InvestigationNote>> {
  const res = await apiClient.get<PageResponse<InvestigationNote>>(`/investigations/${investigationId}/notes`, { params: { page, size } })
  return res.data
}

export async function updateNote(investigationId: number, noteId: number, payload: UpdateNoteRequest): Promise<InvestigationNote> {
  const res = await apiClient.patch<InvestigationNote>(`/investigations/${investigationId}/notes/${noteId}`, { content: payload.content })
  return res.data
}

export async function deleteNote(investigationId: number, noteId: number): Promise<void> {
  await apiClient.delete(`/investigations/${investigationId}/notes/${noteId}`)
}

// Timeline
export async function getTimeline(investigationId: number, page = 0, size = 50): Promise<InvestigationTimelineResponse> {
  const res = await apiClient.get<InvestigationTimelineResponse>(`/investigations/${investigationId}/timeline`, { params: { page, size } })
  return res.data
}

// Reports
export async function getReport(investigationId: number, format?: 'JSON' | 'CSV' | 'PDF'): Promise<InvestigationReport> {
  const params: Record<string, string> = {}
  if (format) params.format = format
  const res = await apiClient.get<InvestigationReport>(`/investigations/${investigationId}/report`, { params })
  return res.data
}

export async function getReportBlob(investigationId: number, format: 'CSV' | 'PDF'): Promise<Blob> {
  const res = await apiClient.get(`/investigations/${investigationId}/report`, {
    params: { format },
    responseType: 'blob',
  })
  return res.data as Blob
}

// Graph / workspace read APIs
export async function graphEvidence(params: { caseId?: string; actorId?: string; page?: number; size?: number; excludeLinkedIncidentId?: number }) {
  const res = await apiClient.get('/graph/evidence', { params })
  return res.data
}

export async function traceCase(caseId: string) {
  const res = await apiClient.get(`/graph/cases/${encodeURIComponent(caseId)}/traceability`)
  return res.data
}

export async function traceIncident(incidentId: number) {
  const res = await apiClient.get(`/graph/incidents/${incidentId}/traceability`)
  return res.data
}

export async function discoverInvestigationEvidence(investigationId: number) {
  const res = await apiClient.post(`/investigations/${investigationId}/evidence/discover`)
  return res.data
}

export async function getGraphEvidenceDetail(stableId: string) {
  const res = await apiClient.get(`/graph/evidence/${encodeURIComponent(stableId)}`)
  return res.data
}

export async function getGraphCase(caseId: string) {
  const res = await apiClient.get(`/graph/cases/${encodeURIComponent(caseId)}`)
  return res.data
}

export async function checkGraphReady(integrationId: number) {
  const res = await apiClient.post(`/integrations/${integrationId}/graph/ready`)
  return res.data
}

export async function checkOrgGraphReady() {
  const res = await apiClient.get('/graph/ready')
  return res.data
}


