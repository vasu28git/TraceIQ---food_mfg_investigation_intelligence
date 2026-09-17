import type { ReactNode } from 'react'
import type { Investigation, InvestigationEvidence } from '../../../types/investigation'

const CARD = '#111827'
const PANEL = '#0F172A'
const BORDER = '#1E293B'
const TEXT = '#F8FAFC'
const SECONDARY = '#94A3B8'
const MUTED = '#64748B'

function Badge({ children, tone = '#334155' }: { children: ReactNode; tone?: string }) {
  return (
    <span
      style={{
        background: tone,
        color: '#E2E8F0',
        borderRadius: 999,
        padding: '3px 8px',
        fontSize: 11,
        fontWeight: 700,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
      }}
    >
      {children}
    </span>
  )
}

function RelevanceBadge({ relevance }: { relevance?: string | null }) {
  const rel = (relevance || '').toUpperCase()
  if (rel === 'DIRECT') return <Badge tone="#1E3A8A">DIRECT</Badge>
  if (rel === 'RELATED') return <Badge tone="#78350F">RELATED</Badge>
  if (rel === 'SUPPORTING') return <Badge tone="#334155">SUPPORTING</Badge>
  if (rel === 'RELEVANT') return <Badge tone="#14532D">RELEVANT</Badge>
  if (rel === 'NOT_RELEVANT') return <Badge tone="#7F1D1D">NOT RELEVANT</Badge>
  return <Badge tone="#334155">{rel || 'UNCLASSIFIED'}</Badge>
}

function ReviewStatusBadge({ status }: { status?: string | null }) {
  const st = (status || 'PENDING_REVIEW').toUpperCase()
  if (st === 'REVIEWED') return <Badge tone="#14532D">Reviewed</Badge>
  if (st === 'REJECTED') return <Badge tone="#7F1D1D">Rejected</Badge>
  return <Badge tone="#854D0E">Pending review</Badge>
}

function dateText(value?: string | null) {
  if (!value) return '-'
  try {
    return new Date(value).toLocaleString()
  } catch {
    return value
  }
}

function cleanNodeName(node: string): string {
  if (!node) return ''
  return node.replace(/^SRC_[A-Z0-9]+_/, '')
}

function deriveRoute(item: InvestigationEvidence): string {
  if (item.semanticRoute) return item.semanticRoute
  if (item.distance === 1) return 'DIRECT_BATCH'
  if (item.discoveryPath && item.discoveryPath.length > 2) {
    const mid = item.discoveryPath[1].toUpperCase()
    if (mid.startsWith('M-') || mid.includes('MACHINE')) return 'MACHINE_ROUTE'
    if (mid.startsWith('SUP-') || mid.includes('SUPPLIER')) return 'SUPPLIER_ROUTE'
    if (mid.startsWith('WZ-') || mid.startsWith('WH-') || mid.startsWith('LOG-') || mid.includes('ZONE') || mid.includes('WAREHOUSE')) return 'WAREHOUSE_ROUTE'
    if (mid.startsWith('PRD-') || mid.includes('PRODUCT')) return 'PRODUCT_ROUTE'
    if (mid.startsWith('CUST-') || mid.includes('CUSTOMER')) return 'CUSTOMER_ROUTE'
  }
  if (item.discoveryReason) {
    const r = item.discoveryReason.toLowerCase()
    if (r.includes('machine')) return 'MACHINE_ROUTE'
    if (r.includes('supplier')) return 'SUPPLIER_ROUTE'
    if (r.includes('warehouse') || r.includes('storage')) return 'WAREHOUSE_ROUTE'
    if (r.includes('product') || r.includes('specification')) return 'PRODUCT_ROUTE'
    if (r.includes('customer')) return 'CUSTOMER_ROUTE'
  }
  if (item.machineReference) return 'MACHINE_ROUTE'
  if (item.supplierReference) return 'SUPPLIER_ROUTE'
  if (item.productReference) return 'PRODUCT_ROUTE'
  return 'GRAPH_ROUTE'
}

function extractSourceBatch(item: InvestigationEvidence): string | null {
  if (item.batchReference && item.sourceType !== 'INVESTIGATION') return item.batchReference
  if (!item.normalizedPayload) return null
  try {
    const root = JSON.parse(item.normalizedPayload) as Record<string, unknown>
    const p = typeof root.originalPayload === 'string'
      ? (JSON.parse(root.originalPayload) as Record<string, unknown>)
      : root
    const val = p.batch_id || p.batch_reference || p.batchReference || p.batch || root.batchReference
    return val ? String(val) : null
  } catch {
    return null
  }
}

function payloadFields(payload?: string | null): [string, unknown][] {
  if (!payload) return []
  try {
    const root = JSON.parse(payload) as Record<string, unknown>
    const source = typeof root.originalPayload === 'string'
      ? (JSON.parse(root.originalPayload) as Record<string, unknown>)
      : root
    return Object.entries(source)
  } catch {
    return []
  }
}

function formattedJson(payload?: string | null): string {
  if (!payload) return 'No payload available.'
  try {
    const obj = JSON.parse(payload)
    if (typeof obj.originalPayload === 'string') {
      try {
        obj.originalPayload = JSON.parse(obj.originalPayload)
      } catch {}
    }
    return JSON.stringify(obj, null, 2)
  } catch {
    return payload
  }
}

export interface FindingRelationshipProps {
  isLinked: boolean
  relationshipType: 'SUPPORTING' | 'CONTRADICTING'
  onToggleLink: () => void
  onSetRelationship: (type: 'SUPPORTING' | 'CONTRADICTING') => void
}

export interface EvidenceDetailDrawerProps {
  investigation: Investigation
  evidence: InvestigationEvidence | null
  loading?: boolean
  error?: string | null
  onClose: () => void
  findingRelationship?: FindingRelationshipProps
}

export function EvidenceDetailDrawer({
  investigation,
  evidence,
  loading = false,
  error = null,
  onClose,
  findingRelationship,
}: EvidenceDetailDrawerProps) {
  if (!evidence) return null

  const sourceBatch = extractSourceBatch(evidence)
  const route = deriveRoute(evidence)

  return (
    <div
      role="dialog"
      aria-label="Evidence details"
      style={{
        position: 'fixed',
        inset: 0,
        background: 'rgba(2, 6, 23, 0.75)',
        zIndex: 60,
        display: 'flex',
        justifyContent: 'flex-end',
      }}
      onClick={onClose}
    >
      <aside
        onClick={event => event.stopPropagation()}
        style={{
          width: 'min(660px, 100vw)',
          height: '100%',
          overflowY: 'auto',
          background: CARD,
          padding: 24,
          boxSizing: 'border-box',
          color: TEXT,
          borderLeft: `1px solid ${BORDER}`,
          display: 'flex',
          flexDirection: 'column',
          gap: 20,
        }}
      >
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', borderBottom: `1px solid ${BORDER}`, paddingBottom: 16 }}>
          <div>
            <div style={{ display: 'flex', gap: 6, alignItems: 'center', marginBottom: 6, flexWrap: 'wrap' }}>
              <RelevanceBadge relevance={evidence.relevance} />
              <ReviewStatusBadge status={evidence.reviewStatus} />
              {evidence.distance != null && (
                <Badge tone="#0F766E">
                  {evidence.distance === 1 ? '1 Hop' : `${evidence.distance} Hops`}
                </Badge>
              )}
              {loading && <span style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700 }}>Refreshing details…</span>}
            </div>
            <h2 style={{ margin: '4px 0 2px', fontSize: 20 }}>
              {evidence.sourceRecordId || evidence.stableId}
            </h2>
            <div style={{ color: MUTED, fontSize: 12, fontFamily: 'monospace' }}>
              {evidence.stableId}
            </div>
            <div style={{ color: SECONDARY, fontSize: 13, marginTop: 4 }}>
              {evidence.sourceType || 'Unknown source'} · {evidence.title || 'Untitled record'}
            </div>
          </div>
          <button
            onClick={onClose}
            aria-label="Close evidence details"
            style={{
              width: 32,
              height: 32,
              borderRadius: 7,
              border: `1px solid ${BORDER}`,
              background: PANEL,
              color: SECONDARY,
              fontSize: 20,
              cursor: 'pointer',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            ×
          </button>
        </div>

        {error && (
          <div role="alert" style={{ background: '#451A1A', color: '#FECACA', borderRadius: 8, padding: 12, fontSize: 12 }}>
            {error}
          </div>
        )}

        {/* Finding Relationship Section (when opened in finding context) */}
        {findingRelationship && (
          <section style={{ background: PANEL, border: `1px solid ${findingRelationship.isLinked ? (findingRelationship.relationshipType === 'CONTRADICTING' ? '#991B1B' : '#166534') : BORDER}`, borderRadius: 8, padding: 14 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <div style={{ color: '#93C5FD', fontSize: 11, fontWeight: 800, textTransform: 'uppercase' }}>
                Finding Relationship
              </div>
              {findingRelationship.isLinked ? (
                <Badge tone={findingRelationship.relationshipType === 'CONTRADICTING' ? '#7F1D1D' : '#14532D'}>
                  Linked as {findingRelationship.relationshipType === 'CONTRADICTING' ? 'Contradicts' : 'Supports'}
                </Badge>
              ) : (
                <Badge tone="#334155">Not linked to finding</Badge>
              )}
            </div>

            <p style={{ margin: '4px 0 10px', fontSize: 12, color: SECONDARY }}>
              {findingRelationship.isLinked
                ? `This evidence record is currently linked to the finding as a ${findingRelationship.relationshipType === 'CONTRADICTING' ? 'contradicting' : 'supporting'} factor.`
                : 'This reviewed evidence can be linked to support or contradict the current finding statement.'}
            </p>

            <div style={{ display: 'flex', gap: 8 }}>
              <button
                type="button"
                onClick={() => {
                  if (!findingRelationship.isLinked) findingRelationship.onToggleLink()
                  findingRelationship.onSetRelationship('SUPPORTING')
                }}
                style={{
                  flex: 1,
                  padding: '7px 12px',
                  borderRadius: 6,
                  fontSize: 12,
                  fontWeight: 700,
                  cursor: 'pointer',
                  border: `1px solid ${findingRelationship.isLinked && findingRelationship.relationshipType === 'SUPPORTING' ? '#22C55E' : BORDER}`,
                  background: findingRelationship.isLinked && findingRelationship.relationshipType === 'SUPPORTING' ? '#14532D' : CARD,
                  color: findingRelationship.isLinked && findingRelationship.relationshipType === 'SUPPORTING' ? '#DCFCE7' : SECONDARY,
                }}
              >
                ✓ Supports Finding
              </button>
              <button
                type="button"
                onClick={() => {
                  if (!findingRelationship.isLinked) findingRelationship.onToggleLink()
                  findingRelationship.onSetRelationship('CONTRADICTING')
                }}
                style={{
                  flex: 1,
                  padding: '7px 12px',
                  borderRadius: 6,
                  fontSize: 12,
                  fontWeight: 700,
                  cursor: 'pointer',
                  border: `1px solid ${findingRelationship.isLinked && findingRelationship.relationshipType === 'CONTRADICTING' ? '#EF4444' : BORDER}`,
                  background: findingRelationship.isLinked && findingRelationship.relationshipType === 'CONTRADICTING' ? '#7F1D1D' : CARD,
                  color: findingRelationship.isLinked && findingRelationship.relationshipType === 'CONTRADICTING' ? '#FEE2E2' : SECONDARY,
                }}
              >
                ✕ Contradicts Finding
              </button>
              {findingRelationship.isLinked && (
                <button
                  type="button"
                  onClick={findingRelationship.onToggleLink}
                  style={{
                    padding: '7px 12px',
                    borderRadius: 6,
                    fontSize: 12,
                    fontWeight: 600,
                    cursor: 'pointer',
                    border: `1px solid ${BORDER}`,
                    background: CARD,
                    color: '#F87171',
                  }}
                >
                  Unlink
                </button>
              )}
            </div>
          </section>
        )}

        {/* Traceability Discovery Path */}
        <section>
          <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT }}>
            Traceability Discovery Path
          </h3>
          {evidence.discoveryPath && evidence.discoveryPath.length > 0 ? (
            <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 12 }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
                <div style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700, textTransform: 'uppercase' }}>
                  Discovery Route ({evidence.distance != null ? `${evidence.distance} Hop${evidence.distance === 1 ? '' : 's'}` : 'Direct'})
                </div>
                <span style={{ background: '#1E293B', border: '1px solid #334155', borderRadius: 4, padding: '2px 8px', color: '#93C5FD', fontSize: 11, fontWeight: 700, fontFamily: 'monospace' }}>
                  route = {route}
                </span>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6, margin: '8px 0' }}>
                {evidence.discoveryPath.map((node, idx) => (
                  <span key={`${node}-${idx}`} style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                    <span
                      style={{
                        background: '#1E293B',
                        border: '1px solid #334155',
                        borderRadius: 6,
                        padding: '4px 8px',
                        fontFamily: 'monospace',
                        fontSize: 12,
                        color: '#F1F5F9',
                      }}
                    >
                      {cleanNodeName(node)}
                    </span>
                    {idx < evidence.discoveryPath!.length - 1 && (
                      <span style={{ color: '#60A5FA', fontWeight: 800 }}>→</span>
                    )}
                  </span>
                ))}
              </div>
              <div style={{ display: 'flex', gap: 14, marginTop: 8, borderTop: `1px solid ${BORDER}`, paddingTop: 6, fontSize: 11, color: MUTED }}>
                <span><strong>distance:</strong> {evidence.distance ?? 1}</span>
                <span><strong>route:</strong> {route}</span>
                <span><strong>relevance:</strong> {evidence.relevance || 'RELATED'}</span>
              </div>
              {evidence.discoveryReason && (
                <div style={{ color: SECONDARY, fontSize: 12, marginTop: 8, borderTop: `1px solid ${BORDER}`, paddingTop: 6 }}>
                  <strong style={{ color: TEXT }}>Route Explanation:</strong> {evidence.discoveryReason}
                </div>
              )}
              {evidence.discoveryMethod && (
                <div style={{ color: MUTED, fontSize: 11, marginTop: 4 }}>
                  Discovery Method: {evidence.discoveryMethod}
                </div>
              )}
            </div>
          ) : evidence.matchExplanations && evidence.matchExplanations.length > 0 ? (
            <div>
              {evidence.matchExplanations.map((match, index) => (
                <div key={`${match.reason}-${index}`} style={{ background: PANEL, padding: 10, marginBottom: 7, color: SECONDARY, borderRadius: 6, border: `1px solid ${BORDER}` }}>
                  <div style={{ color: '#93C5FD', fontWeight: 700, fontSize: 11, marginBottom: 4 }}>{match.reason}</div>
                  <div style={{ color: TEXT, fontSize: 12 }}>{match.connectionPath?.join(' → ') || 'Deterministic discovery match'}</div>
                </div>
              ))}
            </div>
          ) : (
            <div style={{ background: PANEL, padding: 12, color: MUTED, fontSize: 12, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              Direct batch reference or legacy incident association.
            </div>
          )}
        </section>

        {/* Source Context (Keeping Investigation Batch and Source Record Batch separate) */}
        <section>
          <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT }}>
            Source Context
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, color: SECONDARY, fontSize: 12 }}>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Investigation Batch</div>
              <div style={{ color: '#60A5FA', fontWeight: 700, marginTop: 2 }}>{investigation.batchReference || '-'}</div>
              <div style={{ color: MUTED, fontSize: 10, marginTop: 2 }}>Anchor of current investigation</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Source Record Batch</div>
              <div style={{ color: TEXT, fontWeight: 700, marginTop: 2 }}>{sourceBatch || 'None (Record not batch-scoped)'}</div>
              <div style={{ color: MUTED, fontSize: 10, marginTop: 2 }}>Batch from source record payload</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Source System</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{evidence.sourceType || '-'}</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Record Status</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{evidence.status || '-'}</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Machine Reference</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{evidence.machineReference || '-'}</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Supplier Reference</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{evidence.supplierReference || '-'}</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Product Reference</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{evidence.productReference || '-'}</div>
            </div>
            <div style={{ background: PANEL, padding: 10, borderRadius: 6, border: `1px solid ${BORDER}` }}>
              <div style={{ color: MUTED, fontSize: 10, textTransform: 'uppercase', fontWeight: 700 }}>Linked At</div>
              <div style={{ color: TEXT, marginTop: 2 }}>{dateText(evidence.linkedAt)}</div>
            </div>
          </div>
        </section>

        {/* Complete Original Source Record Payload */}
        <section>
          <h3 style={{ margin: '0 0 8px', fontSize: 13, textTransform: 'uppercase', color: TEXT }}>
            Complete Source Record Payload
          </h3>
          <div style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 6, padding: 10 }}>
            <div style={{ maxHeight: 200, overflowY: 'auto', marginBottom: 10 }}>
              {payloadFields(evidence.normalizedPayload).map(([key, value]) => (
                <div
                  key={key}
                  style={{
                    display: 'grid',
                    gridTemplateColumns: '1fr 2fr',
                    gap: 8,
                    padding: '5px 4px',
                    borderBottom: `1px solid ${BORDER}`,
                    fontSize: 12,
                  }}
                >
                  <span style={{ color: MUTED, fontFamily: 'monospace' }}>{key}</span>
                  <span style={{ color: TEXT, wordBreak: 'break-word' }}>
                    {typeof value === 'object' ? JSON.stringify(value) : String(value)}
                  </span>
                </div>
              ))}
            </div>

            <details>
              <summary style={{ color: '#60A5FA', fontSize: 11, fontWeight: 700, cursor: 'pointer' }}>
                View Raw JSON Payload
              </summary>
              <pre
                style={{
                  margin: '8px 0 0',
                  padding: 10,
                  background: '#020617',
                  borderRadius: 6,
                  color: '#CBD5E1',
                  fontSize: 11,
                  fontFamily: 'monospace',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word',
                  maxHeight: 220,
                  overflowY: 'auto',
                }}
              >
                {formattedJson(evidence.normalizedPayload)}
              </pre>
            </details>
          </div>
        </section>

        {/* Investigator Assessment Notes */}
        {evidence.investigatorNotes && (
          <section style={{ background: PANEL, border: `1px solid ${BORDER}`, borderRadius: 8, padding: 12 }}>
            <div style={{ color: MUTED, fontSize: 11, textTransform: 'uppercase', fontWeight: 700, marginBottom: 4 }}>
              Investigator Assessment Notes
            </div>
            <div style={{ color: TEXT, fontSize: 12, whiteSpace: 'pre-wrap' }}>
              {evidence.investigatorNotes}
            </div>
          </section>
        )}

        {/* Bottom Close Bar */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10, borderTop: `1px solid ${BORDER}`, paddingTop: 14 }}>
          <button
            type="button"
            onClick={onClose}
            style={{
              background: PANEL,
              color: TEXT,
              border: `1px solid ${BORDER}`,
              borderRadius: 6,
              padding: '8px 18px',
              fontSize: 12,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Close Details
          </button>
        </div>
      </aside>
    </div>
  )
}
