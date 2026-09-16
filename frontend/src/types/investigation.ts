export type InvestigationStatus = 'DRAFT' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

export interface Investigation {
  id: number
  investigationKey: string
  complaintKey?: string | null
  title: string
  description?: string | null
  // Incident context — optional (Incident domain alias for Investigation persistence)
  batchReference?: string | null
  productReference?: string | null
  orderReference?: string | null
  incidentStart?: string | null
  incidentEnd?: string | null
  status: InvestigationStatus
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
}

// Incident is the domain terminology for Investigation persistence
export type Incident = Investigation
export type CreateIncidentRequest = CreateInvestigationRequest

export interface CreateInvestigationRequest {
  investigationKey: string
  title: string
  description?: string | null
  batchReference?: string | null
  productReference?: string | null
  orderReference?: string | null
  incidentStart?: string | null
  incidentEnd?: string | null
}

export interface Complaint {
  id: number
  complaintKey: string
  title: string
  description?: string | null
  batchReference?: string | null
  externalReference?: string | null
  sourceType: 'MANUAL' | 'INTEGRATION'
  raisedAt?: string | null
  receivedAt?: string | null
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
  investigation?: { id: number; investigationKey: string; title: string; status: InvestigationStatus } | null
}

export interface CreateComplaintRequest {
  complaintKey: string
  title: string
  description?: string | null
  batchReference?: string | null
  externalReference?: string | null
  raisedAt?: string | null
}

export interface CreateInvestigationFromComplaintRequest {
  investigationKey: string
  title: string
  description?: string | null
}

export interface InvestigationEvidence {
  stableId: string
  title?: string | null
  sourceType?: string | null
  status?: string | null
  linkedAt?: string | null
  correlationReason?: string | null
  batchReference?: string | null
  sourceRecordId?: string | null
  machineReference?: string | null
  supplierReference?: string | null
  productReference?: string | null
  orderReference?: string | null
  originalFileName?: string | null
  fileId?: number | null
  contentType?: string | null
  size?: number | null
  associationType?: string | null
  matchExplanations?: EvidenceMatchExplanation[]
  normalizedPayload?: string | null
  reviewStatus?: 'PENDING_REVIEW' | 'REVIEWED' | null
  relevance?: 'RELEVANT' | 'NOT_RELEVANT' | null
  importance?: 'HIGH' | 'MEDIUM' | 'LOW' | null
  assessment?: 'SUPPORTS_INVESTIGATION' | 'CONTRADICTS_INVESTIGATION' | 'CONTEXT_ONLY' | 'INCONCLUSIVE' | 'NOT_ASSESSED' | null
  investigatorNotes?: string | null
  reviewedByUserId?: number | null
  reviewedAt?: string | null
}

export interface InvestigationEvidenceAssessmentRequest {
  [key: string]: unknown
  reviewStatus: 'PENDING_REVIEW' | 'REVIEWED'
  relevance?: 'RELEVANT' | 'NOT_RELEVANT' | null
  importance?: 'HIGH' | 'MEDIUM' | 'LOW' | null
  assessment?: 'SUPPORTS_INVESTIGATION' | 'CONTRADICTS_INVESTIGATION' | 'CONTEXT_ONLY' | 'INCONCLUSIVE' | 'NOT_ASSESSED' | null
  investigatorNotes?: string | null
}

export interface InvestigationFindingEvidenceLink {
  stableId: string
  sourceType?: string | null
  title?: string | null
  relationshipType: 'SUPPORTING' | 'CONTRADICTING'
  reviewStatus?: string | null
  relevance?: string | null
  assessment?: string | null
}

export interface InvestigationFinding {
  id: number
  investigationId: number
  statement: string
  category?: string | null
  confidence?: string | null
  status: 'OPEN' | 'CONFIRMED' | 'DISMISSED'
  reasoning?: string | null
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
  evidence: InvestigationFindingEvidenceLink[]
}

export interface InvestigationFindingRequest {
  statement: string
  category?: string | null
  confidence?: string | null
  status?: 'OPEN' | 'CONFIRMED' | 'DISMISSED'
  reasoning?: string | null
  evidence: { stableId: string; relationshipType: 'SUPPORTING' | 'CONTRADICTING' }[]
}

export interface FindingEvidenceTraceability {
  investigationId: number
  findingId: number
  evidence: {
    evidenceId: string
    sourceSystem?: string | null
    sourceRecordId?: string | null
    title?: string | null
    findingRelationship?: string | null
    discoveryPaths: {
      classification: 'PRIMARY' | 'CROSS_BATCH_CONTEXT'
      reason?: string | null
      matchedField?: string | null
      matchedValue?: string | null
      intermediateEntityType?: string | null
      intermediateEntityValue?: string | null
      path: string[]
      discoveredAt?: string | null
    }[]
    sourceRecord?: {
      id: number
      sourceType?: string | null
      sourceRecordId?: string | null
      batchReference?: string | null
      machineReference?: string | null
      supplierReference?: string | null
      productReference?: string | null
      orderReference?: string | null
      externalReference?: string | null
      payload?: string | null
      ingestedAt?: string | null
      sourceFile?: { id: number; originalName?: string | null; contentType?: string | null; size?: number | null; receivedAt?: string | null } | null
    } | null
  }[]
}

export type InvestigationConclusionLifecycle = 'DRAFT' | 'FINAL'
export type InvestigationConclusionOutcome = 'CONFIRMED' | 'PARTIALLY_CONFIRMED' | 'NOT_CONFIRMED' | 'INCONCLUSIVE'
export type InvestigationConclusionRelationship = 'SUPPORTING' | 'CONTRADICTING'

export interface InvestigationConclusionFinding {
  id: number
  statement: string
  category?: string | null
  confidence?: string | null
  status: string
  reasoning?: string | null
  relationshipType: InvestigationConclusionRelationship
  evidence: { stableId: string; title?: string | null; sourceType?: string | null; relationshipType?: string | null }[]
}

export interface InvestigationConclusion {
  id: number
  investigationId: number
  lifecycle: InvestigationConclusionLifecycle
  outcome: InvestigationConclusionOutcome
  confidence?: 'LOW' | 'MEDIUM' | 'HIGH' | null
  summary: string
  investigatorReasoning?: string | null
  createdByUserId?: number | null
  updatedByUserId?: number | null
  createdAt?: string
  updatedAt?: string
  finalizedAt?: string | null
  supportingFindings: InvestigationConclusionFinding[]
  contradictingFindings: InvestigationConclusionFinding[]
}

export interface InvestigationConclusionRequest {
  lifecycle: InvestigationConclusionLifecycle
  outcome: InvestigationConclusionOutcome
  confidence?: 'LOW' | 'MEDIUM' | 'HIGH' | null
  summary: string
  investigatorReasoning?: string | null
  supportingFindingIds: number[]
  contradictingFindingIds: number[]
}

export type InvestigationActionStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED'
export type InvestigationActionPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
export interface InvestigationAction {
  id: number
  investigationId: number
  title: string
  description?: string | null
  actionType?: string | null
  ownerUserId?: number | null
  ownerUsername?: string | null
  priority: InvestigationActionPriority
  dueDate?: string | null
  status: InvestigationActionStatus
  notes?: string | null
  findingId?: number | null
  findingStatement?: string | null
  conclusionId?: number | null
  conclusionSummary?: string | null
  createdByUserId?: number | null
  updatedByUserId?: number | null
  createdAt?: string
  updatedAt?: string
}
export interface InvestigationActionRequest {
  title: string
  description?: string | null
  actionType?: string | null
  ownerUserId?: number | null
  priority: InvestigationActionPriority
  dueDate?: string | null
  status: InvestigationActionStatus
  notes?: string | null
  findingId?: number | null
  conclusionId?: number | null
}

export interface InvestigationEvidenceSummary {
  investigationId: number
  totalEvidence: number
  reviewed: number
  pendingReview: number
  sourceSystems: string[]
}

export interface EvidenceMatchExplanation {
  reason?: string | null
  matchedField?: string | null
  matchedValue?: string | null
  sourceRecordId?: string | null
  intermediateEntityType?: string | null
  intermediateEntityValue?: string | null
  connectionPath?: string[]
  discoveredAt?: string | null
}

export interface LinkEvidenceRequest {
  stableId: string
}

export interface InvestigationNote {
  id: number
  content: string
  authorUserId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateNoteRequest {
  content: string
}

export interface UpdateNoteRequest {
  content: string
}

export interface InvestigationCheck {
  id: number
  title: string
  description?: string | null
  status: 'OPEN' | 'COMPLETED' | 'SKIPPED'
  result?: string | null
  notes?: string | null
  createdByUserId?: number | null
  completedByUserId?: number | null
  createdAt?: string
  updatedAt?: string
  completedAt?: string | null
}

export interface CreateCheckRequest {
  title: string
  description?: string | null
}

export interface UpdateCheckRequest {
  title?: string
  description?: string | null
}

export interface CompleteCheckRequest {
  result?: string | null
  notes?: string | null
}

export interface SkipCheckRequest {
  notes?: string | null
}

export interface InvestigationDecision {
  id: number
  investigationId: number
  investigationKey?: string | null
  decisionKey: string
  title: string
  conclusion: string
  rationale?: string | null
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateDecisionRequest {
  decisionKey: string
  title: string
  conclusion: string
  rationale?: string | null
}

export interface InvestigationFinalResult {
  id: number
  investigationId: number
  investigationKey?: string | null
  outcome: string
  conclusion: string
  rationale?: string | null
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface CreateFinalResultRequest {
  outcome: string
  conclusion: string
  rationale?: string | null
}

export interface InvestigationTimelineEvent {
  eventType: string
  eventTime: string
  title: string
  description?: string | null
  sourceType?: string | null
  sourceId?: string | null
  stableId?: string | null
  metadata?: unknown
  caseId?: string | null
  actorId?: string | null
  parentId?: string | null
  status?: string | null
  size?: number | null
  contentType?: string | null
  tags?: string[] | null
}

export interface InvestigationTimelineResponse {
  investigationId: number
  investigationKey: string
  events: InvestigationTimelineEvent[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface ReportMetadata {
  investigationId: number
  investigationKey: string
  title: string
  generatedAt: string
  format: 'PDF' | 'CSV' | 'JSON'
  status: InvestigationStatus
}

export interface InvestigationReport {
  reportMetadata: ReportMetadata
  complaint: Complaint | null
  investigation: Investigation
  timeline: InvestigationTimelineResponse
  evidence: InvestigationEvidence[]
  findings: InvestigationFinding[]
  checks: InvestigationCheck[]
  decisions: InvestigationDecision[]
  finalResult: InvestigationFinalResult | null
}

export interface InvestigationRecommendationItem {
  recommendationKey: string
  category: 'VERIFY' | 'INVESTIGATE' | 'REVIEW' | 'FOLLOW_UP'
  title: string
  rationale: string
  suggestedAction: string
  priority: 'LOW' | 'MEDIUM' | 'HIGH'
}

export interface InvestigationRecommendationResponse {
  investigationKey: string
  generatedAt: string
  model: string
  recommendations: InvestigationRecommendationItem[]
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  pageable?: unknown
  numberOfElements?: number
  first?: boolean
  last?: boolean
  empty?: boolean
}

export interface InvestigationFinding {
  id: number
  investigationId: number
  investigationKey?: string | null
  title: string
  description?: string | null
  conclusion?: string | null
  status: 'OPEN' | 'CONFIRMED' | 'DISMISSED'
  createdByUserId?: number | null
  createdAt?: string
  updatedAt?: string
  supportingEvidence?: InvestigationEvidence[]
  supportingChecks?: InvestigationCheck[]
  supportingEvidenceCount?: number | null
  supportingChecksCount?: number | null
  // Provenance fields
  sourceSignalKey?: string | null
  sourcePathId?: string | null
  sourceEntity?: string | null
  sourceEvidenceId?: string | null
  sourceSystem?: string | null
  connectionPath?: string | null
}

export interface ReviewState {
  id: number
  investigationId: number
  itemType: 'SIGNAL' | 'PATH' | 'EVIDENCE'
  itemKey: string
  status: 'OPEN' | 'IN_REVIEW' | 'REVIEWED' | 'RELEVANT' | 'NOT_RELEVANT' | 'NEEDS_MORE_EVIDENCE'
  notes?: string | null
  reviewedByUserId?: number | null
  createdAt?: string
  updatedAt?: string
}

export interface UpdateReviewStateRequest {
  itemType: 'SIGNAL' | 'PATH' | 'EVIDENCE'
  itemKey: string
  status: 'OPEN' | 'IN_REVIEW' | 'REVIEWED' | 'RELEVANT' | 'NOT_RELEVANT' | 'NEEDS_MORE_EVIDENCE'
  notes?: string | null
}

export interface NextAction {
  priority: 'CRITICAL' | 'HIGH' | 'WARNING' | 'MEDIUM' | 'INFO' | 'DONE'
  itemType: 'SIGNAL' | 'PATH' | 'EVIDENCE' | 'FINDING' | 'CHECK' | 'DECISION' | 'FINAL_RESULT' | 'COMPLETED'
  title: string
  description: string
  itemKey?: string | null
  entity?: string | null
  evidenceId?: string | null
  actionLabel: string
}

export interface ReadinessSummary {
  complaintLinked: boolean
  batchIdentified: boolean
  evidenceDiscovered: boolean
  criticalSignalsReviewed: boolean
  findingsRecorded: boolean
  checksCompleted: boolean
  decisionRecorded: boolean
  finalResultRecorded: boolean
  canComplete: boolean
  missingItems: string[]
}

export interface InvestigationProgress {
  investigationId: number
  investigationKey: string
  overallStatus: string
  signalsTotal: number
  signalsReviewed: number
  signalsOpen: number
  evidenceConnected: number
  evidenceReviewed: number
  evidenceNotReviewed: number
  pathsDiscovered: number
  pathsInvestigated: number
  pathsOpen: number
  findingsCount: number
  checksCount: number
  decisionsCount: number
  nextAction?: NextAction | null
  readiness?: ReadinessSummary | null
}

export interface InvestigationReadiness {
  investigationId: number
  investigationKey: string
  status: string
  evidenceCount: number
  checksTotal: number
  checksOpen: number
  checksCompleted: number
  checksSkipped: number
  decisionsCount: number
  hasFinalResult: boolean
  graphReady: boolean
  finalResultOutcome?: string | null
}

export interface CreateFindingRequest {
  title: string
  description?: string | null
  conclusion?: string | null
  status?: 'OPEN' | 'CONFIRMED' | 'DISMISSED'
  sourceSignalKey?: string | null
  sourcePathId?: string | null
  sourceEntity?: string | null
  sourceEvidenceId?: string | null
  sourceSystem?: string | null
  connectionPath?: string | null
}

export interface UpdateFindingRequest {
  title?: string
  description?: string | null
  conclusion?: string | null
  status?: 'OPEN' | 'CONFIRMED' | 'DISMISSED'
}
