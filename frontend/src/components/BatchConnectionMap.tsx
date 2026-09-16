import React, { useMemo, useState, useRef, useEffect, useCallback } from 'react'

export type InvestigationSignalDto = {
  id: string
  sourceEvidenceId: string
  sourceSystem: string
  exactField: string
  actualValue: string
  expectedValue?: string | null
  signalType: 'CRITICAL' | 'WARNING' | 'INFO' | string
  deterministicReason: string
  entityType?: string | null
  entityId?: string | null
  connectionPath?: string[]
  discoveredAt?: string | null
}

export type InvestigationPathDto = {
  id: string
  title: string
  priority: 'HIGH' | 'MEDIUM' | 'LOW' | string
  reason: string
  path: string[]
  targetEvidenceId?: string | null
  targetEntityId?: string | null
  signalCount?: number
}

export type BatchConnectionNode = {
  key: string
  type: string
  stableId: string
  label?: string | null
  sourceType?: string | null
  evidence?: boolean
  direct?: boolean
  priority?: string | null
  signalCount?: number
  signalTypes?: string[]
}

export type BatchConnectionEdge = {
  from: string
  to: string
  type: string
  direct?: boolean
}

export type RelatedBatchInfo = {
  batchReference: string
  sharedAttributeType?: string
  sharedAttributeValue?: string
  reason?: string
  evidenceCount?: number
  evidenceIds?: string[]
}

export type BatchConnectionMapData = {
  incidentId: number
  batchReference: string
  counts: Record<string, number>
  nodes: BatchConnectionNode[]
  relationships: BatchConnectionEdge[]
  signals?: InvestigationSignalDto[]
  paths?: InvestigationPathDto[]
  crossBatchCounts?: Record<string, number>
  crossBatchNodes?: BatchConnectionNode[]
  crossBatchRelationships?: BatchConnectionEdge[]
  crossBatchEvidence?: any[]
  relatedBatches?: RelatedBatchInfo[]
  evidence?: any[]
}

type Props = {
  data: BatchConnectionMapData
  selectedEvidenceId?: string | null
  onSelectEvidence: (stableId: string) => void
  onSelectEntity?: (node: BatchConnectionNode) => void
  onCreateFindingFromPath?: (path: InvestigationPathDto) => void
}

// Icons
function IconNodes({ color = '#2563EB' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="18" cy="5" r="3" />
      <circle cx="6" cy="12" r="3" />
      <circle cx="18" cy="19" r="3" />
      <line x1="8.59" y1="13.51" x2="15.42" y2="17.49" />
      <line x1="15.41" y1="6.51" x2="8.59" y2="10.49" />
    </svg>
  )
}

function IconDoc({ color = '#16A34A' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="16" y1="13" x2="8" y2="13" />
      <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
  )
}

function IconBox({ color = '#9333EA' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z" />
      <polyline points="3.27 6.96 12 12.01 20.73 6.96" />
      <line x1="12" y1="22.08" x2="12" y2="12" />
    </svg>
  )
}

function IconGear({ color = '#2563EB' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </svg>
  )
}

function IconTruck({ color = '#EA580C' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="1" y="3" width="15" height="13" />
      <polygon points="16 8 20 8 23 11 23 16 16 16 16 8" />
      <circle cx="5.5" cy="18.5" r="2.5" />
      <circle cx="18.5" cy="18.5" r="2.5" />
    </svg>
  )
}

function IconShipment({ color = '#4F46E5' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <polyline points="21 8 21 21 3 21 3 8" />
      <rect x="1" y="3" width="22" height="5" />
      <line x1="10" y1="12" x2="14" y2="12" />
    </svg>
  )
}

function IconWarehouse({ color = '#0D9488' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M3 21h18v-8a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v8z" />
      <path d="M3 11l9-7 9 7" />
      <path d="M9 21v-4a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v4" />
    </svg>
  )
}

function IconQA({ color = '#DB2777' }: { color?: string }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10 2v7.31L4.5 19.9A2 2 0 0 0 6.24 23h11.52a2 2 0 0 0 1.74-3.1L14 9.31V2" />
      <line x1="8.5" y1="2" x2="15.5" y2="2" />
      <line x1="9" y1="14" x2="15" y2="14" />
    </svg>
  )
}

function IconBatch({ color = '#2563EB' }: { color?: string }) {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <ellipse cx="12" cy="5" rx="9" ry="3" />
      <path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
      <path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
    </svg>
  )
}

function IconUser({ color = '#2563EB' }: { color?: string }) {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke={color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
      <circle cx="12" cy="7" r="4" />
    </svg>
  )
}

interface LayoutNode {
  node: BatchConnectionNode
  x: number
  y: number
  width: number
  height: number
  rank: number
}

export function BatchConnectionMap({
  data,
  selectedEvidenceId,
  onSelectEvidence,
  onSelectEntity,
  onCreateFindingFromPath,
}: Props) {
  const [view, setView] = useState<'graph' | 'tree'>('graph')
  const [activeTab, setActiveTab] = useState<'map' | 'signals' | 'paths'>('map')
  const [selectedPathId, setSelectedPathId] = useState<string | null>(null)
  const [selectedSignalId, setSelectedSignalId] = useState<string | null>(null)

  const [zoom, setZoom] = useState<number>(1)
  const [pan, setPan] = useState<{ x: number; y: number }>({ x: 20, y: 20 })
  const [isDragging, setIsDragging] = useState<boolean>(false)
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [searchTerm, setSearchTerm] = useState<string>('')
  const svgRef = useRef<SVGSVGElement | null>(null)

  const nodeByKey = useMemo(() => new Map(data.nodes.map(n => [n.key, n])), [data.nodes])
  const root = useMemo(() => data.nodes.find(n => n.type === 'Batch') || data.nodes[0], [data.nodes])

  // Path & Search Highlighting Calculation
  const { highlightedNodeKeys, highlightedEdgeKeys } = useMemo(() => {
    const nodeKeys = new Set<string>()
    const edgeKeys = new Set<string>()

    let targetTokens: string[] = []

    // 1. Target from active path selection
    if (selectedPathId && data.paths) {
      const activePathObj = data.paths.find(p => p.id === selectedPathId)
      if (activePathObj) targetTokens = activePathObj.path
    }

    // 2. Target from active signal selection
    if (selectedSignalId && data.signals) {
      const activeSigObj = data.signals.find(s => s.id === selectedSignalId)
      if (activeSigObj && activeSigObj.connectionPath) targetTokens = activeSigObj.connectionPath
    }

    // 3. Target from selected evidence
    if (selectedEvidenceId && targetTokens.length === 0) {
      const evNode = data.nodes.find(n => n.evidence && n.stableId === selectedEvidenceId)
      if (evNode) {
        // Trace back to root
        targetTokens = [evNode.key]
      }
    }

    // 4. Target from search term
    if (searchTerm.trim() && targetTokens.length === 0) {
      const term = searchTerm.toLowerCase().trim()
      data.nodes.forEach(n => {
        if (
          (n.label || '').toLowerCase().includes(term) ||
          (n.stableId || '').toLowerCase().includes(term) ||
          (n.type || '').toLowerCase().includes(term) ||
          (n.sourceType || '').toLowerCase().includes(term)
        ) {
          nodeKeys.add(n.key)
        }
      })
    }

    if (targetTokens.length > 0) {
      targetTokens.forEach(t => {
        const parts = t.split(':')
        const type = parts[0]
        const id = parts.length > 1 ? parts[1] : t
        nodeKeys.add(`${type}:${id}`)
      })
      // Add edges between consecutive tokens
      for (let i = 0; i < targetTokens.length - 1; i++) {
        const p1 = targetTokens[i].split(':')
        const p2 = targetTokens[i + 1].split(':')
        const k1 = `${p1[0]}:${p1.length > 1 ? p1[1] : p1[0]}`
        const k2 = `${p2[0]}:${p2.length > 1 ? p2[1] : p2[0]}`
        edgeKeys.add(`${k1}->${k2}`)
      }
    }

    return { highlightedNodeKeys: nodeKeys, highlightedEdgeKeys: edgeKeys }
  }, [selectedPathId, selectedSignalId, selectedEvidenceId, searchTerm, data.paths, data.signals, data.nodes])

  const isHighlightActive = highlightedNodeKeys.size > 0

  // Build Layout Coordinates (Deterministic Tree Layout matching target image)
  const { layoutNodes, layoutEdges, bounds } = useMemo(() => {
    if (!root) return { layoutNodes: [], layoutEdges: [], bounds: { width: 900, height: 480 } }

    const nodesList: LayoutNode[] = []
    const edgeList: BatchConnectionEdge[] = [...data.relationships]

    const rankMap = new Map<string, number>()
    rankMap.set(root.key, 0)

    const queue = [root.key]
    const visited = new Set<string>([root.key])
    while (queue.length > 0) {
      const curr = queue.shift()!
      const currRank = rankMap.get(curr) || 0
      const outEdges = edgeList.filter(e => e.from === curr)
      for (const e of outEdges) {
        const nextRank = currRank + 1
        rankMap.set(e.to, Math.max(rankMap.get(e.to) || 0, nextRank))
        if (!visited.has(e.to)) {
          visited.add(e.to)
          queue.push(e.to)
        }
      }
    }

    data.nodes.forEach(n => {
      let r = rankMap.get(n.key) ?? 1
      if (n.type === 'Batch') r = 0
      else if (n.evidence && r < 2) r = 3

      const isBatch = n.type === 'Batch'
      const isEvidence = !!n.evidence
      const width = isBatch ? 160 : isEvidence ? 140 : 135
      const height = isBatch ? 54 : isEvidence ? 44 : 46

      nodesList.push({
        node: n,
        x: 0,
        y: 0,
        width,
        height,
        rank: r,
      })
    })

    const rankGroups = new Map<number, LayoutNode[]>()
    nodesList.forEach(item => {
      if (!rankGroups.has(item.rank)) rankGroups.set(item.rank, [])
      rankGroups.get(item.rank)!.push(item)
    })

    const sortedRanks = Array.from(rankGroups.keys()).sort((a, b) => a - b)
    const layerSpacingY = 110
    const nodeGapX = 24
    const rowGapY = 60
    const startX = 40
    let currentY = 40
    let maxX = 0
    let maxY = 0

    sortedRanks.forEach(r => {
      const list = rankGroups.get(r)!
      const maxNodesPerRow = 4
      const rows: LayoutNode[][] = []
      for (let i = 0; i < list.length; i += maxNodesPerRow) {
        rows.push(list.slice(i, i + maxNodesPerRow))
      }

      rows.forEach((rowList, rowIdx) => {
        const totalWidth = rowList.reduce((sum, item) => sum + item.width, 0) + (rowList.length - 1) * nodeGapX
        let currentX = startX + Math.max(0, (800 - totalWidth) / 2)
        const rowMaxHeight = Math.max(...rowList.map(n => n.height))

        rowList.forEach(item => {
          item.x = currentX
          item.y = currentY
          currentX += item.width + nodeGapX
          if (item.x + item.width > maxX) maxX = item.x + item.width
          if (item.y + item.height > maxY) maxY = item.y + item.height
        })
        currentY += rowMaxHeight + (rowIdx < rows.length - 1 ? rowGapY : layerSpacingY)
      })
    })

    return {
      layoutNodes: nodesList,
      layoutEdges: edgeList,
      bounds: { width: Math.max(maxX + 60, 850), height: Math.max(maxY + 60, 460) },
    }
  }, [data.nodes, data.relationships, root])

  // Mouse pan handlers
  const handleMouseDown = (e: React.MouseEvent<SVGSVGElement>) => {
    if (e.button !== 0) return
    setIsDragging(true)
    setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y })
  }

  const handleMouseMove = (e: React.MouseEvent<SVGSVGElement>) => {
    if (!isDragging) return
    setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y })
  }

  const handleMouseUp = () => setIsDragging(false)

  const handleWheel = (e: React.WheelEvent<SVGSVGElement>) => {
    e.preventDefault()
    const zoomFactor = e.deltaY < 0 ? 1.08 : 0.92
    setZoom(z => Math.min(Math.max(z * zoomFactor, 0.4), 2.0))
  }

  const layoutNodeMap = useMemo(() => new Map(layoutNodes.map(ln => [ln.node.key, ln])), [layoutNodes])

  // Investigate Path Action
  const handleInvestigatePath = (p: InvestigationPathDto) => {
    setSelectedPathId(p.id)
    setActiveTab('map')
    if (p.targetEvidenceId) onSelectEvidence(p.targetEvidenceId)
  }

  // Signal Click Action
  const handleSelectSignal = (s: InvestigationSignalDto) => {
    setSelectedSignalId(s.id)
    setActiveTab('map')
    if (s.sourceEvidenceId) onSelectEvidence(s.sourceEvidenceId)
  }

  return (
    <section style={{ background: '#FFFFFF', border: '1px solid #E2E8F0', borderRadius: 12, padding: 16, display: 'flex', flexDirection: 'column', gap: 14, boxShadow: '0 1px 3px rgba(0,0,0,0.05)' }}>
      {/* Top Header & Investigation Mode Tabs */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
        <div>
          <div style={{ color: '#0F172A', fontWeight: 800, fontSize: 16 }}>Batch Connection Map</div>
          <div style={{ color: '#64748B', fontSize: 12, marginTop: 2 }}>All connected entities and evidence for <strong style={{ color: '#2563EB' }}>{data.batchReference}</strong></div>
        </div>

        <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          {/* Main Navigation Tabs: Connection Map vs Signals vs Investigation Paths */}
          <div style={{ display: 'flex', background: '#F1F5F9', border: '1px solid #E2E8F0', borderRadius: 8, padding: 2 }}>
            <button
              onClick={() => setActiveTab('map')}
              style={{
                padding: '5px 12px',
                background: activeTab === 'map' ? '#2563EB' : 'transparent',
                color: activeTab === 'map' ? '#FFFFFF' : '#64748B',
                border: 0,
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              🗺️ Connection Map
            </button>

            <button
              onClick={() => setActiveTab('signals')}
              style={{
                padding: '5px 12px',
                background: activeTab === 'signals' ? '#2563EB' : 'transparent',
                color: activeTab === 'signals' ? '#FFFFFF' : '#64748B',
                border: 0,
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                gap: 5,
              }}
            >
              ⚠️ Signals ({(data.signals || []).length})
            </button>

            <button
              onClick={() => setActiveTab('paths')}
              style={{
                padding: '5px 12px',
                background: activeTab === 'paths' ? '#2563EB' : 'transparent',
                color: activeTab === 'paths' ? '#FFFFFF' : '#64748B',
                border: 0,
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 700,
                display: 'flex',
                alignItems: 'center',
                gap: 5,
              }}
            >
              🧭 Investigation Paths ({(data.paths || []).length})
            </button>
          </div>

          {/* Segmented Control Switcher */}
          <div style={{ display: 'flex', background: '#F1F5F9', border: '1px solid #E2E8F0', borderRadius: 8, padding: 2 }}>
            <button
              onClick={() => setView('graph')}
              style={{
                padding: '5px 10px',
                background: view === 'graph' ? '#FFFFFF' : 'transparent',
                color: view === 'graph' ? '#0F172A' : '#64748B',
                border: 0,
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              📊 Graph
            </button>
            <button
              onClick={() => setView('tree')}
              style={{
                padding: '5px 10px',
                background: view === 'tree' ? '#FFFFFF' : 'transparent',
                color: view === 'tree' ? '#0F172A' : '#64748B',
                border: 0,
                borderRadius: 6,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 700,
              }}
            >
              🌲 Tree
            </button>
          </div>
        </div>
      </div>

      {/* Mode View: Map View vs Signals Tab vs Paths Tab */}
      {activeTab === 'signals' ? (
        /* Investigation Signals List Section */
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, background: '#F8FAFC', padding: 14, borderRadius: 10, border: '1px solid #E2E8F0' }}>
          <div style={{ fontSize: 14, fontWeight: 800, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>Investigation Signals</span>
            <span style={{ fontSize: 11, background: '#EFF6FF', color: '#2563EB', padding: '2px 8px', borderRadius: 999 }}>
              Strict structured field extraction
            </span>
          </div>

          {!data.signals || data.signals.length === 0 ? (
            <div style={{ color: '#64748B', fontSize: 12, padding: 16, background: '#FFFFFF', borderRadius: 8, border: '1px solid #E2E8F0', textAlign: 'center' }}>
              No critical or warning signals detected for batch {data.batchReference}. All connected parameters are within expected specifications.
            </div>
          ) : (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(340px, 1fr))', gap: 10 }}>
              {data.signals.map(sig => {
                const isCritical = sig.signalType === 'CRITICAL'
                return (
                  <div
                    key={sig.id}
                    onClick={() => handleSelectSignal(sig)}
                    style={{
                      background: '#FFFFFF',
                      border: `1px solid ${isCritical ? '#FECACA' : '#FDE68A'}`,
                      borderRadius: 10,
                      padding: 12,
                      cursor: 'pointer',
                      boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 6,
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <span style={{ fontSize: 10, fontWeight: 800, padding: '2px 8px', borderRadius: 999, background: isCritical ? '#FEE2E2' : '#FEF3C7', color: isCritical ? '#991B1B' : '#92400E' }}>
                        {isCritical ? '🔴 CRITICAL' : '🟠 WARNING'}
                      </span>
                      <span style={{ fontSize: 11, color: '#64748B', fontFamily: 'monospace' }}>
                        {sig.sourceSystem} · {sig.sourceEvidenceId}
                      </span>
                    </div>

                    <div style={{ fontSize: 12, fontWeight: 700, color: '#0F172A', marginTop: 2 }}>
                      {sig.deterministicReason}
                    </div>

                    <div style={{ display: 'grid', gridTemplateColumns: '100px 1fr', gap: 4, fontSize: 11, background: '#F8FAFC', padding: 8, borderRadius: 6, border: '1px solid #E2E8F0' }}>
                      <span style={{ color: '#64748B' }}>Exact Field:</span>
                      <span style={{ fontFamily: 'monospace', fontWeight: 600 }}>{sig.exactField}</span>

                      <span style={{ color: '#64748B' }}>Actual Value:</span>
                      <span style={{ color: isCritical ? '#DC2626' : '#D97706', fontWeight: 700 }}>{sig.actualValue}</span>

                      {sig.expectedValue && (
                        <>
                          <span style={{ color: '#64748B' }}>Expected:</span>
                          <span style={{ color: '#16A34A', fontWeight: 600 }}>{sig.expectedValue}</span>
                        </>
                      )}
                    </div>

                    <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 4 }}>
                      <span style={{ fontSize: 11, color: '#2563EB', fontWeight: 700 }}>↗ Trace Path in Graph</span>
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      ) : activeTab === 'paths' ? (
        /* Investigation Paths List Section */
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10, background: '#F8FAFC', padding: 14, borderRadius: 10, border: '1px solid #E2E8F0' }}>
          <div style={{ fontSize: 14, fontWeight: 800, color: '#0F172A', display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>Investigation Paths</span>
            <span style={{ fontSize: 11, background: '#EFF6FF', color: '#2563EB', padding: '2px 8px', borderRadius: 999 }}>
              Prioritized by source signals
            </span>
          </div>

          {!data.paths || data.paths.length === 0 ? (
            <div style={{ color: '#64748B', fontSize: 12, padding: 16, background: '#FFFFFF', borderRadius: 8, border: '1px solid #E2E8F0', textAlign: 'center' }}>
              No prioritized paths discovered for batch {data.batchReference}.
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {data.paths.map(p => {
                const isHigh = p.priority === 'HIGH'
                const isMed = p.priority === 'MEDIUM'
                return (
                  <div
                    key={p.id}
                    style={{
                      background: '#FFFFFF',
                      border: `1px solid ${isHigh ? '#FECACA' : isMed ? '#FDE68A' : '#CBD5E1'}`,
                      borderRadius: 10,
                      padding: 12,
                      display: 'flex',
                      flexDirection: 'column',
                      gap: 8,
                      boxShadow: '0 1px 3px rgba(0,0,0,0.03)',
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <span style={{ fontSize: 10, fontWeight: 800, padding: '2px 8px', borderRadius: 999, background: isHigh ? '#FEE2E2' : isMed ? '#FEF3C7' : '#F1F5F9', color: isHigh ? '#991B1B' : isMed ? '#92400E' : '#475569' }}>
                          {p.priority} PRIORITY
                        </span>
                        <span style={{ fontSize: 13, fontWeight: 800, color: '#0F172A' }}>{p.title}</span>
                      </div>
                    </div>

                    <div style={{ fontSize: 11, color: '#475569' }}>
                      <strong>Reason:</strong> {p.reason}
                    </div>

                    {/* Sequence Breadcrumb */}
                    <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 6, background: '#F8FAFC', padding: '6px 10px', borderRadius: 6, border: '1px solid #E2E8F0' }}>
                      {p.path.map((token, tIdx) => (
                        <React.Fragment key={`${token}-${tIdx}`}>
                          <span style={{ background: tIdx === 0 ? '#DBEAFE' : '#EFF6FF', color: '#1E40AF', padding: '2px 7px', borderRadius: 4, fontSize: 11, fontWeight: 700, fontFamily: 'monospace' }}>
                            {token}
                          </span>
                          {tIdx < p.path.length - 1 && <span style={{ color: '#94A3B8', fontSize: 10 }}>─▶</span>}
                        </React.Fragment>
                      ))}
                    </div>

                    {/* Path Actions */}
                    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4, flexWrap: 'wrap' }}>
                      <button
                        onClick={() => handleInvestigatePath(p)}
                        style={{ padding: '5px 11px', background: '#2563EB', border: 0, borderRadius: 6, color: '#FFFFFF', cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                      >
                        🎯 Investigate Path
                      </button>
                      <button
                        onClick={() => p.targetEvidenceId && onSelectEvidence(p.targetEvidenceId)}
                        style={{ padding: '5px 11px', background: '#FFFFFF', border: '1px solid #CBD5E1', borderRadius: 6, color: '#334155', cursor: 'pointer', fontSize: 11, fontWeight: 600 }}
                      >
                        View Evidence
                      </button>
                      {onCreateFindingFromPath && (
                        <button
                          onClick={() => onCreateFindingFromPath(p)}
                          style={{ padding: '5px 11px', background: '#16A34A', border: 0, borderRadius: 6, color: '#FFFFFF', cursor: 'pointer', fontSize: 11, fontWeight: 700 }}
                        >
                          + Create Finding from Path
                        </button>
                      )}
                    </div>
                  </div>
                )
              })}
            </div>
          )}
        </div>
      ) : (
        /* Main Connection Map Layout Area */
        <div style={{ display: 'flex', gap: 14, flexWrap: 'wrap', alignItems: 'stretch' }}>
          {/* Left Side Counter Cards Column */}
          <div style={{ width: 210, display: 'flex', flexDirection: 'column', gap: 7, flexShrink: 0 }}>
            {[
              ['Total Connections', 'totalConnections', '42', IconNodes, '#EFF6FF', '#2563EB'],
              ['Evidence Records', 'evidenceRecords', '28', IconDoc, '#F0FDF4', '#16A34A'],
              ['Products', 'products', '1', IconBox, '#FAF5FF', '#9333EA'],
              ['Machines', 'machines', '3', IconGear, '#EFF6FF', '#2563EB'],
              ['Suppliers', 'suppliers', '1', IconTruck, '#FFF7ED', '#EA580C'],
              ['Shipments', 'shipments', '2', IconShipment, '#EEF2FF', '#4F46E5'],
              ['Warehouse Records', 'warehouseRecords', '4', IconWarehouse, '#F0FDFA', '#0D9488'],
              ['QA / LIMS Records', 'qaLimsRecords', '3', IconQA, '#FDF2F8', '#DB2777'],
            ].map(([label, key, defaultVal, IconComponent, bg, accentColor]) => {
              const countVal = data.counts[key as string] ?? defaultVal
              const Icon = IconComponent as React.ComponentType<{ color?: string }>
              return (
                <div
                  key={key as string}
                  style={{
                    border: '1px solid #E2E8F0',
                    borderRadius: 10,
                    padding: '9px 12px',
                    background: '#FFFFFF',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 10,
                    boxShadow: '0 1px 2px rgba(0,0,0,0.03)',
                  }}
                >
                  <div style={{ width: 34, height: 34, borderRadius: 8, background: bg as string, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Icon color={accentColor as string} />
                  </div>
                  <div style={{ display: 'flex', flexDirection: 'column' }}>
                    <span style={{ color: '#64748B', fontSize: 10, fontWeight: 600 }}>{label as string}</span>
                    <span style={{ color: '#0F172A', fontSize: 17, fontWeight: 800, lineHeight: 1.1 }}>{countVal}</span>
                  </div>
                </div>
              )
            })}
          </div>

          {/* Right Canvas Area */}
          <div style={{ flex: 1, minWidth: 500, minHeight: 460, border: '1px solid #E2E8F0', borderRadius: 10, background: '#F8FAFC', position: 'relative', overflow: 'hidden' }}>
            {view === 'graph' ? (
              <svg
                ref={svgRef}
                width="100%"
                height="490"
                onMouseDown={handleMouseDown}
                onMouseMove={handleMouseMove}
                onMouseUp={handleMouseUp}
                onMouseLeave={handleMouseUp}
                onWheel={handleWheel}
                style={{ display: 'block', cursor: isDragging ? 'grabbing' : 'grab' }}
              >
                <defs>
                  <marker id="arrow-direct-light" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                    <path d="M 0 1 L 10 5 L 0 9 z" fill="#3B82F6" />
                  </marker>
                  <marker id="arrow-indirect-light" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto">
                    <path d="M 0 1 L 10 5 L 0 9 z" fill="#818CF8" />
                  </marker>
                  <marker id="arrow-highlight-path" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M 0 1 L 10 5 L 0 9 z" fill="#2563EB" />
                  </marker>
                </defs>

                <g transform={`translate(${pan.x}, ${pan.y}) scale(${zoom})`}>
                  {/* Render Edges */}
                  {layoutEdges.map((edge, idx) => {
                    const src = layoutNodeMap.get(edge.from)
                    const tgt = layoutNodeMap.get(edge.to)
                    if (!src || !tgt) return null

                    const isEdgeHighlighted = highlightedEdgeKeys.has(`${edge.from}->${edge.to}`)
                    const isEdgeDimmed = isHighlightActive && !isEdgeHighlighted

                    const startX = src.x + src.width / 2
                    const startY = src.y + src.height
                    const endX = tgt.x + tgt.width / 2
                    const endY = tgt.y

                    const dy = (endY - startY) * 0.5
                    const pathD = `M ${startX} ${startY} C ${startX} ${startY + dy}, ${endX} ${endY - dy}, ${endX} ${endY}`

                    const strokeColor = isEdgeHighlighted ? '#2563EB' : edge.direct ? '#3B82F6' : '#818CF8'
                    const strokeDash = edge.direct ? undefined : '4 3'
                    const markerEnd = isEdgeHighlighted ? 'url(#arrow-highlight-path)' : edge.direct ? 'url(#arrow-direct-light)' : 'url(#arrow-indirect-light)'

                    return (
                      <path
                        key={`${edge.from}-${edge.to}-${idx}`}
                        d={pathD}
                        fill="none"
                        stroke={strokeColor}
                        strokeWidth={isEdgeHighlighted ? '2.8' : edge.direct ? '1.8' : '1.5'}
                        strokeDasharray={strokeDash}
                        markerEnd={markerEnd}
                        opacity={isEdgeDimmed ? 0.15 : 1}
                      />
                    )
                  })}

                  {/* Render Nodes */}
                  {layoutNodes.map(layoutNode => {
                    const node = layoutNode.node
                    const isSelected = !!node.evidence && node.stableId === selectedEvidenceId
                    const isHighlighted = highlightedNodeKeys.has(node.key)
                    const isDimmed = isHighlightActive && !isHighlighted
                    const isBatch = node.type === 'Batch'
                    const isEvidence = !!node.evidence

                    const hasCriticalSig = node.signalTypes?.includes('CRITICAL')
                    const hasWarningSig = node.signalTypes?.includes('WARNING')

                    let fill = '#EFF6FF'
                    let stroke = '#93C5FD'
                    let textColor = '#1E40AF'

                    if (isBatch) {
                      fill = '#DBEAFE'
                      stroke = '#3B82F6'
                      textColor = '#1E3A8A'
                    } else if (isEvidence) {
                      fill = '#DCFCE7'
                      stroke = '#86EFAC'
                      textColor = '#15803D'
                    }

                    if (hasCriticalSig) {
                      stroke = '#EF4444'
                      fill = '#FEE2E2'
                    } else if (hasWarningSig) {
                      stroke = '#F59E0B'
                      fill = '#FEF3C7'
                    }

                    if (isSelected || isHighlighted) {
                      stroke = '#2563EB'
                    }

                    return (
                      <g
                        key={node.key}
                        transform={`translate(${layoutNode.x}, ${layoutNode.y})`}
                        onClick={() => {
                          if (isEvidence) onSelectEvidence(node.stableId)
                          else onSelectEntity?.(node)
                        }}
                        style={{ cursor: 'pointer', transition: 'opacity 0.2s ease' }}
                        opacity={isDimmed ? 0.15 : 1}
                      >
                        {/* Node Box */}
                        {isBatch ? (
                          <ellipse
                            cx={layoutNode.width / 2}
                            cy={layoutNode.height / 2}
                            rx={layoutNode.width / 2}
                            ry={layoutNode.height / 2}
                            fill={fill}
                            stroke={stroke}
                            strokeWidth={isSelected || isHighlighted ? 3 : 2}
                          />
                        ) : (
                          <rect
                            x="0"
                            y="0"
                            width={layoutNode.width}
                            height={layoutNode.height}
                            rx={isEvidence ? 16 : 8}
                            fill={fill}
                            stroke={stroke}
                            strokeWidth={isSelected || isHighlighted ? 2.5 : 1.2}
                            filter="drop-shadow(0 1px 2px rgba(0,0,0,0.04))"
                          />
                        )}

                        {/* Icon */}
                        <g transform={`translate(${isBatch ? 18 : 10}, ${layoutNode.height / 2 - 10})`}>
                          {isBatch ? (
                            <IconBatch color="#2563EB" />
                          ) : isEvidence ? (
                            <IconDoc color="#16A34A" />
                          ) : node.type === 'Machine' ? (
                            <IconGear color="#2563EB" />
                          ) : node.type === 'Product' ? (
                            <IconBox color="#2563EB" />
                          ) : node.type === 'Supplier' ? (
                            <IconTruck color="#2563EB" />
                          ) : node.type === 'Shipment' ? (
                            <IconShipment color="#2563EB" />
                          ) : node.type === 'Warehouse' ? (
                            <IconWarehouse color="#2563EB" />
                          ) : node.type === 'Customer' ? (
                            <IconUser color="#2563EB" />
                          ) : (
                            <IconQA color="#2563EB" />
                          )}
                        </g>

                        {/* Type Header Text */}
                        <text
                          x={isBatch ? layoutNode.width / 2 + 8 : 34}
                          y={layoutNode.height / 2 - 2}
                          textAnchor={isBatch ? 'middle' : 'start'}
                          fill={textColor}
                          fontSize="9"
                          fontWeight="700"
                        >
                          {node.type}
                        </text>

                        {/* ID / Label Text */}
                        <text
                          x={isBatch ? layoutNode.width / 2 + 8 : 34}
                          y={layoutNode.height / 2 + 10}
                          textAnchor={isBatch ? 'middle' : 'start'}
                          fill={textColor}
                          fontSize="10"
                          fontWeight="800"
                          fontFamily="ui-monospace, monospace"
                        >
                          {(node.label || node.stableId || '').slice(0, 16)}
                        </text>

                        {/* Signal Badge Indicator */}
                        {hasCriticalSig && (
                          <circle cx={layoutNode.width - 6} cy="6" r="6" fill="#EF4444" stroke="#FFFFFF" strokeWidth="1.5" />
                        )}
                        {hasWarningSig && !hasCriticalSig && (
                          <circle cx={layoutNode.width - 6} cy="6" r="6" fill="#F59E0B" stroke="#FFFFFF" strokeWidth="1.5" />
                        )}
                      </g>
                    )
                  })}
                </g>
              </svg>
            ) : (
              <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 8 }}>
                <div style={{ fontWeight: 800, color: '#0F172A', fontSize: 13 }}>Tree Branch View · {data.batchReference}</div>
                {data.nodes.map(n => (
                  <div key={n.key} style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 10px', border: '1px solid #E2E8F0', borderRadius: 6, background: '#FFFFFF' }}>
                    <span style={{ fontWeight: 700, color: n.evidence ? '#16A34A' : '#2563EB', fontSize: 11 }}>{n.type}:</span>
                    <span style={{ fontSize: 12, color: '#0F172A', fontFamily: 'monospace' }}>{n.label || n.stableId}</span>
                    {n.signalCount ? <span style={{ fontSize: 10, background: '#FEE2E2', color: '#991B1B', padding: '1px 5px', borderRadius: 999, fontWeight: 700 }}>⚠️ {n.signalCount} signals</span> : null}
                  </div>
                ))}
              </div>
            )}

            {/* Bottom Right Canvas Legend */}
            <div
              style={{
                position: 'absolute',
                bottom: 12,
                right: 12,
                background: 'rgba(255, 255, 255, 0.92)',
                border: '1px solid #E2E8F0',
                borderRadius: 8,
                padding: '6px 10px',
                display: 'flex',
                gap: 12,
                fontSize: 11,
                fontWeight: 600,
                boxShadow: '0 2px 6px rgba(0,0,0,0.04)',
                backdropFilter: 'blur(4px)',
              }}
            >
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#1E40AF' }}>
                <svg width="20" height="6"><line x1="0" y1="3" x2="20" y2="3" stroke="#3B82F6" strokeWidth="2" /></svg> Direct Connection
              </span>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, color: '#4338CA' }}>
                <svg width="20" height="6"><line x1="0" y1="3" x2="20" y2="3" stroke="#818CF8" strokeWidth="1.5" strokeDasharray="4 3" /></svg> Indirect Connection
              </span>
            </div>
          </div>
        </div>
      )}

      {/* RELATED BATCH / CROSS-BATCH CONTEXT - separated from PRIMARY */}
      {(data.relatedBatches && data.relatedBatches.length > 0) || (data.crossBatchEvidence && data.crossBatchEvidence.length > 0) ? (
        <div style={{ marginTop: 14, background: '#FFFBEB', border: '1px solid #FDE68A', borderRadius: 10, padding: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div style={{ fontSize: 14, fontWeight: 800, color: '#92400E', display: 'flex', alignItems: 'center', gap: 8 }}>
            <span>Related Batch / Cross-Batch Context</span>
            <span style={{ fontSize: 11, background: '#FEF3C7', color: '#92400E', padding: '2px 8px', borderRadius: 999 }}>
              Not part of PRIMARY investigation
            </span>
          </div>
          <div style={{ fontSize: 11, color: '#78716C' }}>
            These batches share a product/machine/supplier with {data.batchReference} but are NOT primary evidence. They require crossing through another batch (e.g., {data.batchReference} → {data.nodes.find(n=>n.type==='Product')?.label || 'Shared Entity'} → Batch B). Signals and paths from these are excluded from primary counts.
          </div>

          {data.relatedBatches && data.relatedBatches.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {data.relatedBatches.map(rb => (
                <div key={rb.batchReference} style={{ background: '#FFFFFF', border: '1px solid #E7E5E4', borderRadius: 8, padding: 10, display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8 }}>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    <span style={{ fontSize: 13, fontWeight: 700, color: '#0F172A', fontFamily: 'monospace' }}>{rb.batchReference}</span>
                    <span style={{ fontSize: 11, color: '#57534E' }}>
                      Related via <strong>{rb.sharedAttributeType} {rb.sharedAttributeValue}</strong> — {rb.reason}
                    </span>
                  </div>
                  <span style={{ fontSize: 11, background: '#F5F5F4', color: '#44403C', padding: '4px 8px', borderRadius: 6 }}>
                    {rb.evidenceCount} evidence records
                  </span>
                </div>
              ))}
            </div>
          )}

          {data.crossBatchCounts && (
            <div style={{ display: 'flex', gap: 12, fontSize: 11, color: '#57534E', flexWrap: 'wrap' }}>
              <span>Cross-batch evidence: <strong>{data.crossBatchCounts['evidenceRecords'] ?? data.crossBatchEvidence?.length ?? 0}</strong></span>
              <span>Cross-batch connections: <strong>{data.crossBatchCounts['totalConnections'] ?? 0}</strong></span>
              {data.crossBatchNodes && data.crossBatchNodes.filter(n=>n.type==='Machine').length > 0 && (
                <span>Machines in context: <strong>{data.crossBatchNodes.filter(n=>n.type==='Machine').map(n=>n.stableId).join(', ')}</strong></span>
              )}
            </div>
          )}

          {data.crossBatchEvidence && data.crossBatchEvidence.length > 0 && (
            <details style={{ background: '#FFFFFF', border: '1px solid #E7E5E4', borderRadius: 8, padding: 8 }}>
              <summary style={{ fontSize: 11, fontWeight: 700, color: '#44403C', cursor: 'pointer' }}>
                Show cross-batch evidence details ({data.crossBatchEvidence.length})
              </summary>
              <div style={{ marginTop: 8, display: 'flex', flexDirection: 'column', gap: 4, maxHeight: 160, overflowY: 'auto' }}>
                {data.crossBatchEvidence.map((ev: any) => (
                  <div key={ev.stableId || ev.sourceRecordId} style={{ fontSize: 11, color: '#57534E', fontFamily: 'monospace', padding: '4px 6px', background: '#F5F5F4', borderRadius: 4 }}>
                    {ev.stableId} — {ev.sourceType} — Machine: {ev.machineReference || '-'} — Batch: {ev.batchReference || '-'}
                  </div>
                ))}
              </div>
            </details>
          )}
        </div>
      ) : null}
    </section>
  )
}