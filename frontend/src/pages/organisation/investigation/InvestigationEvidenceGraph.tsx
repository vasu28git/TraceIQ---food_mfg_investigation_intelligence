import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { traceIncident } from '../../../services/investigationService'
import { discoverInvestigationEvidence } from '../../../services/investigationService'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#64748B'

export type TraceNode = {
  stableId: string
  label: string
  title?: string | null
  sourceType?: string | null
  status?: string | null
}

export type TraceRel = {
  fromStableId: string
  fromLabel: string
  type: string
  toStableId: string
  toLabel: string
}

export type TraceResponse = {
  incidentNode: TraceNode
  nodes: TraceNode[]
  relationships: TraceRel[]
  depth: number
}

interface LayoutNode {
  key: string
  node: TraceNode
  x: number
  y: number
  width: number
  height: number
  category: 'incident' | 'batch' | 'entity' | 'evidence'
}

interface LayoutEdge {
  from: string
  to: string
  type: string
  fromKey: string
  toKey: string
}

function getNodeKey(label: string, stableId: string): string {
  return `${label}:${stableId}`
}

function getNodeCategory(node: TraceNode): 'incident' | 'batch' | 'entity' | 'evidence' {
  if (node.label === 'Incident') return 'incident'
  if (node.label === 'Batch') return 'batch'
  if (node.label === 'Evidence') return 'evidence'
  return 'entity'
}

function getCategoryColor(category: 'incident' | 'batch' | 'entity' | 'evidence', label?: string) {
  switch (category) {
    case 'incident':
      return { bg: '#312E81', border: '#6366F1', text: '#E0E7FF', badgeBg: '#4338CA', badgeFg: '#EEF2FF' }
    case 'batch':
      return { bg: '#451A03', border: '#F59E0B', text: '#FEF3C7', badgeBg: '#78350F', badgeFg: '#FDE68A' }
    case 'entity': {
      const l = (label || '').toLowerCase()
      if (l === 'machine') return { bg: '#083344', border: '#06B6D4', text: '#CFFAFE', badgeBg: '#155E75', badgeFg: '#A5F3FC' }
      if (l === 'supplier') return { bg: '#431407', border: '#EA580C', text: '#FFEDD5', badgeBg: '#7C2D12', badgeFg: '#FED7AA' }
      if (l === 'product') return { bg: '#2E1065', border: '#8B5CF6', text: '#EDE9FE', badgeBg: '#5B21B6', badgeFg: '#DDD6FE' }
      if (l === 'customer') return { bg: '#172554', border: '#3B82F6', text: '#DBEAFE', badgeBg: '#1E40AF', badgeFg: '#BFDBFE' }
      if (l === 'warehouse') return { bg: '#042F2E', border: '#14B8A6', text: '#CCFBF1', badgeBg: '#115E59', badgeFg: '#99F6E4' }
      return { bg: '#1E1B4B', border: '#6366F1', text: '#E0E7FF', badgeBg: '#3730A3', badgeFg: '#C7D2FE' }
    }
    case 'evidence':
      return { bg: '#052E16', border: '#22C55E', text: '#DCFCE7', badgeBg: '#14532D', badgeFg: '#86EFAC' }
  }
}

function getRelColor(type: string) {
  switch (type) {
    case 'TARGETS':
      return { stroke: '#F59E0B', badgeBg: '#78350F', badgeFg: '#FDE68A' }
    case 'HAS_EVIDENCE':
      return { stroke: '#22C55E', badgeBg: '#14532D', badgeFg: '#86EFAC' }
    case 'REFERENCES':
      return { stroke: '#38BDF8', badgeBg: '#075985', badgeFg: '#BAE6FD' }
    case 'ASSOCIATED_WITH':
      return { stroke: '#A855F7', badgeBg: '#581C87', badgeFg: '#F3E8FF' }
    default:
      return { stroke: '#94A3B8', badgeBg: '#334155', badgeFg: '#E2E8F0' }
  }
}

export function InvestigationEvidenceGraph({
  investigationId,
  batchReference,
}: {
  investigationId: number
  batchReference?: string
}) {
  const [data, setData] = useState<TraceResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isEmpty, setIsEmpty] = useState(false)
  const [recovering, setRecovering] = useState(false)

  // Canvas View State
  const [zoom, setZoom] = useState<number>(1)
  const [pan, setPan] = useState<{ x: number; y: number }>({ x: 30, y: 30 })
  const [isDragging, setIsDragging] = useState(false)
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [selectedNodeKey, setSelectedNodeKey] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [copiedId, setCopiedId] = useState(false)

  const svgRef = useRef<SVGSVGElement | null>(null)

  // Fetch Traceability API
  const loadGraph = useCallback(async () => {
    setLoading(true)
    setError(null)
    setIsEmpty(false)
    setSelectedNodeKey(null)
    try {
      const res = (await traceIncident(investigationId)) as TraceResponse
      if (!res || ((!res.nodes || res.nodes.length === 0) && (!res.relationships || res.relationships.length === 0))) {
        setIsEmpty(true)
        setData(null)
      } else {
        setData(res)
      }
    } catch (err: unknown) {
      const status = (err as { response?: { status?: number } })?.response?.status
      if (status === 404) {
        setIsEmpty(true)
        setData(null)
      } else {
        const msg = getApiErrorMessage(err, 'Failed to load investigation evidence graph')
        setError(msg)
      }
    } finally {
      setLoading(false)
    }
  }, [investigationId])

  useEffect(() => {
    loadGraph()
  }, [loadGraph])

  const recoverGraph = async () => {
    setRecovering(true)
    setError(null)
    try {
      await discoverInvestigationEvidence(investigationId)
    } catch (err: unknown) {
      setError(getApiErrorMessage(err, 'Failed to discover investigation evidence'))
    } finally {
      setRecovering(false)
      await loadGraph()
    }
  }

  // All Nodes Map
  const allNodes = useMemo<TraceNode[]>(() => {
    if (!data) return []
    return [data.incidentNode, ...data.nodes]
  }, [data])

  const nodeMap = useMemo(() => {
    const map = new Map<string, TraceNode>()
    for (const n of allNodes) {
      map.set(getNodeKey(n.label, n.stableId), n)
    }
    return map
  }, [allNodes])

  // Calculate Layout Nodes and Edges
  const { layoutNodes, layoutEdges, bounds } = useMemo(() => {
    if (!data || allNodes.length === 0) {
      return { layoutNodes: [], layoutEdges: [], bounds: { width: 900, height: 600 } }
    }

    const NODE_WIDTH = 200
    const NODE_HEIGHT = 70
    const HORIZONTAL_GAP = 50
    const VERTICAL_GAP = 140

    // Assign rank levels
    // Rank 0: Incident
    // Rank 1: Batch / Primary Target
    // Rank 2: Domain Entities (Product, Machine, Supplier, Customer, Warehouse)
    // Rank 3: Evidence Records
    const rankBuckets: Map<number, TraceNode[]> = new Map([
      [0, []],
      [1, []],
      [2, []],
      [3, []],
    ])

    for (const n of allNodes) {
      const cat = getNodeCategory(n)
      if (cat === 'incident') rankBuckets.get(0)!.push(n)
      else if (cat === 'batch') rankBuckets.get(1)!.push(n)
      else if (cat === 'entity') rankBuckets.get(2)!.push(n)
      else rankBuckets.get(3)!.push(n)
    }

    // If no batch in rank 1, push entities up or balance
    if (rankBuckets.get(1)!.length === 0 && rankBuckets.get(2)!.length > 0) {
      // Keep ranks as is
    }

    // Sort domain entities logically
    const entitySortOrder: Record<string, number> = {
      product: 1,
      machine: 2,
      supplier: 3,
      customer: 4,
      warehouse: 5,
    }
    rankBuckets.get(2)!.sort((a, b) => {
      const ordA = entitySortOrder[a.label.toLowerCase()] || 99
      const ordB = entitySortOrder[b.label.toLowerCase()] || 99
      return ordA - ordB
    })

    // Sort evidence records by source type
    const sourceSortOrder: Record<string, number> = {
      mes: 1,
      packaging: 2,
      cmms: 3,
      sop: 4,
      lims: 5,
      erp: 6,
      shipment: 7,
      warehouse: 8,
    }
    rankBuckets.get(3)!.sort((a, b) => {
      const sA = sourceSortOrder[(a.sourceType || '').toLowerCase()] || 99
      const sB = sourceSortOrder[(b.sourceType || '').toLowerCase()] || 99
      return sA - sB || a.stableId.localeCompare(b.stableId)
    })

    // Determine max width across ranks
    let maxRankWidth = 0
    rankBuckets.forEach((bucket) => {
      const w = bucket.length * NODE_WIDTH + Math.max(0, bucket.length - 1) * HORIZONTAL_GAP
      if (w > maxRankWidth) maxRankWidth = w
    })
    const canvasWidth = Math.max(900, maxRankWidth + 120)

    // Calculate positions
    const nodesList: LayoutNode[] = []
    const layoutNodeMap = new Map<string, LayoutNode>()

    rankBuckets.forEach((bucket, rank) => {
      const bucketCount = bucket.length
      if (bucketCount === 0) return

      const totalBucketWidth = bucketCount * NODE_WIDTH + (bucketCount - 1) * HORIZONTAL_GAP
      const startX = (canvasWidth - totalBucketWidth) / 2
      const y = 40 + rank * (NODE_HEIGHT + VERTICAL_GAP)

      bucket.forEach((node, idx) => {
        const x = startX + idx * (NODE_WIDTH + HORIZONTAL_GAP)
        const key = getNodeKey(node.label, node.stableId)
        const layoutNode: LayoutNode = {
          key,
          node,
          x,
          y,
          width: NODE_WIDTH,
          height: NODE_HEIGHT,
          category: getNodeCategory(node),
        }
        nodesList.push(layoutNode)
        layoutNodeMap.set(key, layoutNode)
      })
    })

    // Edges with keys
    const edgesList: LayoutEdge[] = []
    for (const r of data.relationships) {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)
      if (layoutNodeMap.has(fromKey) && layoutNodeMap.has(toKey)) {
        edgesList.push({
          from: r.fromStableId,
          to: r.toStableId,
          type: r.type,
          fromKey,
          toKey,
        })
      }
    }

    const maxY = 40 + 3 * (NODE_HEIGHT + VERTICAL_GAP) + NODE_HEIGHT + 60
    return {
      layoutNodes: nodesList,
      layoutEdges: edgesList,
      bounds: { width: canvasWidth, height: maxY },
    }
  }, [data, allNodes])

  const layoutNodeMap = useMemo(() => {
    const map = new Map<string, LayoutNode>()
    for (const n of layoutNodes) {
      map.set(n.key, n)
    }
    return map
  }, [layoutNodes])

  // Connected nodes / edges for selected node highlighting
  const { connectedNodeKeys, connectedEdgeIndices } = useMemo(() => {
    const nodeKeys = new Set<string>()
    const edgeIndices = new Set<number>()

    if (selectedNodeKey) {
      nodeKeys.add(selectedNodeKey)
      layoutEdges.forEach((edge, idx) => {
        if (edge.fromKey === selectedNodeKey) {
          nodeKeys.add(edge.toKey)
          edgeIndices.add(idx)
        } else if (edge.toKey === selectedNodeKey) {
          nodeKeys.add(edge.fromKey)
          edgeIndices.add(idx)
        }
      })
    }

    if (searchTerm.trim()) {
      const term = searchTerm.toLowerCase().trim()
      layoutNodes.forEach((ln) => {
        const n = ln.node
        if (
          n.stableId.toLowerCase().includes(term) ||
          n.label.toLowerCase().includes(term) ||
          (n.title && n.title.toLowerCase().includes(term)) ||
          (n.sourceType && n.sourceType.toLowerCase().includes(term))
        ) {
          nodeKeys.add(ln.key)
        }
      })
    }

    return { connectedNodeKeys: nodeKeys, connectedEdgeIndices: edgeIndices }
  }, [selectedNodeKey, searchTerm, layoutEdges, layoutNodes])

  // Selected Node Details
  const selectedNode = useMemo(() => {
    if (!selectedNodeKey) return null
    return nodeMap.get(selectedNodeKey) || null
  }, [selectedNodeKey, nodeMap])

  const selectedNodeRelationships = useMemo(() => {
    if (!selectedNodeKey || !data) return { incoming: [], outgoing: [] }
    const incoming: { edge: TraceRel; neighbor: TraceNode | undefined }[] = []
    const outgoing: { edge: TraceRel; neighbor: TraceNode | undefined }[] = []

    data.relationships.forEach((r) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)
      if (fromKey === selectedNodeKey) {
        outgoing.push({ edge: r, neighbor: nodeMap.get(toKey) })
      }
      if (toKey === selectedNodeKey) {
        incoming.push({ edge: r, neighbor: nodeMap.get(fromKey) })
      }
    })

    return { incoming, outgoing }
  }, [selectedNodeKey, data, nodeMap])

  // Zoom & Pan Handlers
  const handleZoom = (delta: number) => {
    setZoom((z) => Math.min(Math.max(0.3, z + delta), 2.5))
  }

  const handleResetView = () => {
    setZoom(1)
    setPan({ x: 30, y: 30 })
    setSelectedNodeKey(null)
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (e.button !== 0) return
    setIsDragging(true)
    setDragStart({ x: e.clientX - pan.x, y: e.clientY - pan.y })
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return
    setPan({ x: e.clientX - dragStart.x, y: e.clientY - dragStart.y })
  }

  const handleMouseUp = () => {
    setIsDragging(false)
  }

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault()
    const zoomFactor = e.deltaY < 0 ? 1.1 : 0.9
    setZoom((z) => Math.min(Math.max(0.3, z * zoomFactor), 2.5))
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(true)
    setTimeout(() => setCopiedId(false), 1800)
  }

  // Count summaries
  const stats = useMemo(() => {
    if (!data) return { incident: 0, entities: 0, evidence: 0, relationships: 0 }
    let entities = 0
    let evidence = 0
    data.nodes.forEach((n) => {
      if (n.label === 'Evidence') evidence++
      else entities++
    })
    return {
      incident: 1,
      entities,
      evidence,
      relationships: data.relationships.length,
    }
  }, [data])

  // RENDER: Loading State
  if (loading) {
    return (
      <div
        role="status"
        aria-live="polite"
        style={{
          background: CARD_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 12,
          padding: 48,
          textAlign: 'center',
          color: TEXT_SEC,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 12,
        }}
      >
        <div
          style={{
            width: 36,
            height: 36,
            border: '3px solid #3B82F6',
            borderTopColor: 'transparent',
            borderRadius: '50%',
            animation: 'spin 0.8s linear infinite',
          }}
        />
        <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
        <div style={{ fontSize: 14, fontWeight: 700, color: TEXT_MAIN }}>Loading Evidence Graph…</div>
        <div style={{ fontSize: 12, color: MUTED }}>Querying incident traceability relationships</div>
      </div>
    )
  }

  // RENDER: Empty State
  if (isEmpty || !data) {
    return (
      <div
        style={{
          background: CARD_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 12,
          padding: 40,
          textAlign: 'center',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
        }}
      >
        <div
          style={{
            width: 52,
            height: 52,
            borderRadius: 14,
            background: '#1E293B',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: 24,
            color: '#60A5FA',
          }}
        >
          🕸️
        </div>
        <div style={{ maxWidth: 440 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: TEXT_MAIN, margin: '0 0 6px 0' }}>
            No evidence has been discovered for this investigation yet.
          </h3>
          <p style={{ fontSize: 13, color: TEXT_SEC, margin: 0, lineHeight: 1.5 }}>
            Evidence discovery runs deterministically when a complaint is created with an affected batch reference.
            Once discovered, relevant source records and domain relationships will appear here.
          </p>
        </div>
        <button
          onClick={recoverGraph}
          disabled={recovering}
          style={{
            marginTop: 4,
            padding: '7px 16px',
            background: '#1E293B',
            border: `1px solid ${BORDER}`,
            borderRadius: 8,
            color: '#93C5FD',
            cursor: 'pointer',
            fontSize: 12,
            fontWeight: 600,
          }}
        >
          {recovering ? 'Discovering Evidence…' : '↻ Refresh Graph'}
        </button>
      </div>
    )
  }

  // RENDER: Error State
  if (error) {
    return (
      <div
        role="alert"
        style={{
          background: '#451A1A',
          border: '1px solid #7F1D1D',
          borderRadius: 12,
          padding: 24,
          color: '#FECACA',
          display: 'flex',
          flexDirection: 'column',
          gap: 12,
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <span style={{ fontSize: 18 }}>⚠️</span>
          <div style={{ fontWeight: 700, fontSize: 15 }}>Failed to load evidence graph</div>
        </div>
        <div style={{ fontSize: 13, color: '#FCA5A5' }}>{error}</div>
        <div>
          <button
            onClick={loadGraph}
            style={{
              padding: '6px 14px',
              background: '#991B1B',
              color: '#FFFFFF',
              border: 0,
              borderRadius: 8,
              cursor: 'pointer',
              fontWeight: 600,
              fontSize: 13,
            }}
          >
            Retry
          </button>
        </div>
      </div>
    )
  }

  const isAnyHighlight = connectedNodeKeys.size > 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {/* Top Controls & Metrics Bar */}
      <div
        style={{
          background: CARD_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 12,
          padding: '12px 16px',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        {/* Left: Investigation Graph Metrics */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 14, fontWeight: 800, color: TEXT_MAIN }}>Evidence Graph</span>
            <span
              style={{
                fontSize: 11,
                background: '#1E293B',
                color: '#60A5FA',
                padding: '2px 8px',
                borderRadius: 999,
                fontWeight: 700,
              }}
            >
              Incident #{investigationId}
            </span>
          </div>

          <div style={{ display: 'flex', gap: 12, fontSize: 12, color: TEXT_SEC, alignItems: 'center' }}>
            <span>
              <strong style={{ color: '#E0E7FF' }}>{stats.incident}</strong> Incident
            </span>
            <span>·</span>
            <span>
              <strong style={{ color: '#FDE68A' }}>{stats.entities}</strong> Entities
            </span>
            <span>·</span>
            <span>
              <strong style={{ color: '#86EFAC' }}>{stats.evidence}</strong> Evidence Records
            </span>
            <span>·</span>
            <span>
              <strong style={{ color: '#38BDF8' }}>{stats.relationships}</strong> Directed Relationships
            </span>
          </div>
        </div>

        {/* Right: Search, Zoom, Fit Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <input
            type="text"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            placeholder="Search nodes (ID, type)..."
            style={{
              padding: '6px 10px',
              background: '#0B1120',
              border: `1px solid ${BORDER}`,
              borderRadius: 8,
              color: TEXT_MAIN,
              fontSize: 12,
              width: 180,
            }}
          />

          <div style={{ display: 'flex', background: '#0B1120', border: `1px solid ${BORDER}`, borderRadius: 8, overflow: 'hidden' }}>
            <button
              onClick={() => handleZoom(0.15)}
              title="Zoom In"
              style={{
                padding: '6px 10px',
                background: 'transparent',
                border: 0,
                color: TEXT_SEC,
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: 700,
              }}
            >
              +
            </button>
            <div style={{ width: 1, background: BORDER }} />
            <button
              onClick={() => handleZoom(-0.15)}
              title="Zoom Out"
              style={{
                padding: '6px 10px',
                background: 'transparent',
                border: 0,
                color: TEXT_SEC,
                cursor: 'pointer',
                fontSize: 14,
                fontWeight: 700,
              }}
            >
              −
            </button>
            <div style={{ width: 1, background: BORDER }} />
            <button
              onClick={handleResetView}
              title="Reset View"
              style={{
                padding: '6px 12px',
                background: 'transparent',
                border: 0,
                color: TEXT_SEC,
                cursor: 'pointer',
                fontSize: 11,
                fontWeight: 600,
              }}
            >
              Fit / Reset
            </button>
          </div>

          <button
            onClick={loadGraph}
            title="Refresh"
            style={{
              padding: '6px 10px',
              background: 'transparent',
              border: `1px solid ${BORDER}`,
              borderRadius: 8,
              color: TEXT_SEC,
              cursor: 'pointer',
              fontSize: 12,
            }}
          >
            ↻
          </button>
        </div>
      </div>

      {/* Main Canvas & Details Area */}
      <div style={{ display: 'flex', gap: 14, alignItems: 'stretch', position: 'relative' }}>
        {/* SVG Graph Viewport */}
        <div
          style={{
            flex: 1,
            minHeight: 620,
            height: 640,
            background: '#0B1120',
            border: `1px solid ${BORDER}`,
            borderRadius: 12,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Legend Overlay */}
          <div
            style={{
              position: 'absolute',
              bottom: 12,
              left: 12,
              background: 'rgba(15, 23, 42, 0.85)',
              backdropFilter: 'blur(8px)',
              border: `1px solid ${BORDER}`,
              borderRadius: 8,
              padding: '6px 12px',
              display: 'flex',
              gap: 14,
              fontSize: 11,
              color: TEXT_SEC,
              alignItems: 'center',
              zIndex: 10,
              flexWrap: 'wrap',
            }}
          >
            <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#6366F1' }} />
              Incident
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#F59E0B' }} />
              Target Batch
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#06B6D4' }} />
              Domain Entity
            </span>
            <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
              <span style={{ width: 10, height: 10, borderRadius: '50%', background: '#22C55E' }} />
              Canonical Evidence
            </span>
          </div>

          <svg
            ref={svgRef}
            width="100%"
            height="100%"
            onMouseDown={handleMouseDown}
            onMouseMove={handleMouseMove}
            onMouseUp={handleMouseUp}
            onMouseLeave={handleMouseUp}
            onWheel={handleWheel}
            style={{ display: 'block', cursor: isDragging ? 'grabbing' : 'grab' }}
          >
            <defs>
              {/* Arrowheads for different relationship types */}
              {['TARGETS', 'HAS_EVIDENCE', 'REFERENCES', 'ASSOCIATED_WITH', 'DEFAULT'].map((rel) => {
                const color = getRelColor(rel).stroke
                return (
                  <marker
                    key={rel}
                    id={`arrow-${rel}`}
                    viewBox="0 0 10 10"
                    refX="9"
                    refY="5"
                    markerWidth="6"
                    markerHeight="6"
                    orient="auto"
                  >
                    <path d="M 0 1 L 10 5 L 0 9 z" fill={color} />
                  </marker>
                )
              })}
              <marker
                id="arrow-highlight"
                viewBox="0 0 10 10"
                refX="9"
                refY="5"
                markerWidth="7"
                markerHeight="7"
                orient="auto"
              >
                <path d="M 0 1 L 10 5 L 0 9 z" fill="#60A5FA" />
              </marker>

              {/* Grid Background Pattern */}
              <pattern id="grid-dots" x="0" y="0" width="24" height="24" patternUnits="userSpaceOnUse">
                <circle cx="2" cy="2" r="1" fill="#1E293B" />
              </pattern>
            </defs>

            {/* Grid Background */}
            <rect width="100%" height="100%" fill="url(#grid-dots)" />

            <g transform={`translate(${pan.x}, ${pan.y}) scale(${zoom})`}>
              {/* Directed Edges */}
              {layoutEdges.map((edge, idx) => {
                const src = layoutNodeMap.get(edge.fromKey)
                const tgt = layoutNodeMap.get(edge.toKey)
                if (!src || !tgt) return null

                const isConnected = connectedEdgeIndices.has(idx)
                const isDimmed = isAnyHighlight && !isConnected

                // Route from bottom center of source to top center of target
                // If source is lower or same rank, route from side or appropriate boundary
                let startX = src.x + src.width / 2
                let startY = src.y + src.height
                let endX = tgt.x + tgt.width / 2
                let endY = tgt.y

                if (src.y >= tgt.y) {
                  // Horizontal or upward connection (e.g. between entities on same level)
                  if (src.x < tgt.x) {
                    startX = src.x + src.width
                    startY = src.y + src.height / 2
                    endX = tgt.x
                    endY = tgt.y + tgt.height / 2
                  } else {
                    startX = src.x
                    startY = src.y + src.height / 2
                    endX = tgt.x + tgt.width
                    endY = tgt.y + tgt.height / 2
                  }
                }

                const deltaY = endY - startY
                const deltaX = endX - startX
                let pathD = ''

                if (Math.abs(deltaY) > 20) {
                  const cpY = deltaY * 0.5
                  pathD = `M ${startX} ${startY} C ${startX} ${startY + cpY}, ${endX} ${endY - cpY}, ${endX} ${endY}`
                } else {
                  // Same row curve
                  const cpX = deltaX * 0.5
                  const arcY = startY - 35
                  pathD = `M ${startX} ${startY} C ${startX + cpX * 0.2} ${arcY}, ${endX - cpX * 0.2} ${arcY}, ${endX} ${endY}`
                }

                const relColor = getRelColor(edge.type)
                const strokeColor = isConnected ? '#60A5FA' : relColor.stroke
                const markerEnd = isConnected ? 'url(#arrow-highlight)' : `url(#arrow-${edge.type in { TARGETS: 1, HAS_EVIDENCE: 1, REFERENCES: 1, ASSOCIATED_WITH: 1 } ? edge.type : 'DEFAULT'})`

                const midX = (startX + endX) / 2
                const midY = deltaY < 20 ? startY - 26 : (startY + endY) / 2

                return (
                  <g key={`${edge.fromKey}->${edge.toKey}-${idx}`} opacity={isDimmed ? 0.15 : 0.85}>
                    <path
                      d={pathD}
                      fill="none"
                      stroke={strokeColor}
                      strokeWidth={isConnected ? 2.5 : 1.4}
                      markerEnd={markerEnd}
                      strokeDasharray={edge.type === 'ASSOCIATED_WITH' ? '4 3' : undefined}
                      style={{ transition: 'all 0.2s ease' }}
                    />
                    {/* Edge Label Pill */}
                    <g transform={`translate(${midX}, ${midY})`}>
                      <rect
                        x="-38"
                        y="-8"
                        width="76"
                        height="16"
                        rx="8"
                        fill="#0F172A"
                        stroke={strokeColor}
                        strokeWidth="1"
                        opacity="0.9"
                      />
                      <text
                        x="0"
                        y="3"
                        fontSize="8"
                        fontWeight="700"
                        fill={strokeColor}
                        textAnchor="middle"
                        fontFamily="ui-monospace, monospace"
                      >
                        {edge.type}
                      </text>
                    </g>
                  </g>
                )
              })}

              {/* Nodes */}
              {layoutNodes.map((ln) => {
                const node = ln.node
                const isSelected = selectedNodeKey === ln.key
                const isConnected = connectedNodeKeys.has(ln.key)
                const isDimmed = isAnyHighlight && !isConnected

                const colors = getCategoryColor(ln.category, node.label)

                return (
                  <g
                    key={ln.key}
                    transform={`translate(${ln.x}, ${ln.y})`}
                    onClick={(e) => {
                      e.stopPropagation()
                      setSelectedNodeKey(isSelected ? null : ln.key)
                    }}
                    style={{ cursor: 'pointer', transition: 'opacity 0.2s ease' }}
                    opacity={isDimmed ? 0.25 : 1}
                  >
                    {/* Outer Glow on Selection */}
                    {isSelected && (
                      <rect
                        x="-4"
                        y="-4"
                        width={ln.width + 8}
                        height={ln.height + 8}
                        rx="12"
                        fill="none"
                        stroke="#60A5FA"
                        strokeWidth="2.5"
                        strokeDasharray="4 2"
                      />
                    )}

                    {/* Node Card Background */}
                    <rect
                      x="0"
                      y="0"
                      width={ln.width}
                      height={ln.height}
                      rx="8"
                      fill={colors.bg}
                      stroke={isSelected ? '#60A5FA' : colors.border}
                      strokeWidth={isSelected ? 2 : 1.2}
                    />

                    {/* Category / Type Header Badge */}
                    <rect
                      x="8"
                      y="8"
                      width={ln.width - 16}
                      height="18"
                      rx="4"
                      fill={colors.badgeBg}
                    />
                    <text
                      x="14"
                      y="20"
                      fontSize="9"
                      fontWeight="800"
                      fill={colors.badgeFg}
                      letterSpacing="0.4"
                    >
                      {node.label.toUpperCase()}
                    </text>

                    {/* Source Type Indicator (for evidence) */}
                    {node.sourceType && (
                      <text
                        x={ln.width - 14}
                        y="20"
                        fontSize="9"
                        fontWeight="700"
                        fill="#A7F3D0"
                        textAnchor="end"
                      >
                        {node.sourceType}
                      </text>
                    )}

                    {/* Stable ID */}
                    <text
                      x="12"
                      y="42"
                      fontSize="11"
                      fontWeight="700"
                      fill={colors.text}
                      fontFamily="ui-monospace, monospace"
                    >
                      {node.stableId.length > 22 ? node.stableId.slice(0, 20) + '…' : node.stableId}
                    </text>

                    {/* Title or Subtext */}
                    <text
                      x="12"
                      y="58"
                      fontSize="9"
                      fill={MUTED}
                      fontFamily="sans-serif"
                    >
                      {(node.title && node.title.length > 26 ? node.title.slice(0, 24) + '…' : node.title) ||
                        (node.status ? `Status: ${node.status}` : node.label)}
                    </text>
                  </g>
                )
              })}
            </g>
          </svg>
        </div>

        {/* Selected Node Details Side Panel */}
        {selectedNode && (
          <div
            style={{
              width: 320,
              background: CARD_BG,
              border: `1px solid ${BORDER}`,
              borderRadius: 12,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
              flexShrink: 0,
              maxHeight: 640,
              overflowY: 'auto',
            }}
          >
            {/* Panel Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <span
                  style={{
                    fontSize: 10,
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    padding: '2px 8px',
                    borderRadius: 999,
                    background: getCategoryColor(getNodeCategory(selectedNode), selectedNode.label).badgeBg,
                    color: getCategoryColor(getNodeCategory(selectedNode), selectedNode.label).badgeFg,
                  }}
                >
                  {selectedNode.label}
                </span>
                <h4 style={{ fontSize: 14, fontWeight: 800, color: TEXT_MAIN, margin: '6px 0 0 0' }}>
                  Node Details
                </h4>
              </div>
              <button
                onClick={() => setSelectedNodeKey(null)}
                style={{
                  background: 'transparent',
                  border: 0,
                  color: TEXT_SEC,
                  fontSize: 18,
                  cursor: 'pointer',
                  lineHeight: 1,
                  padding: 4,
                }}
              >
                ×
              </button>
            </div>

            {/* Stable ID Card with Copy */}
            <div
              style={{
                background: '#0B1120',
                border: `1px solid ${BORDER}`,
                borderRadius: 8,
                padding: '8px 10px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 8,
              }}
            >
              <div style={{ overflow: 'hidden' }}>
                <span style={{ fontSize: 10, color: MUTED, display: 'block', textTransform: 'uppercase', fontWeight: 700 }}>
                  Stable Identifier
                </span>
                <span
                  style={{
                    fontSize: 12,
                    fontFamily: 'ui-monospace, monospace',
                    fontWeight: 700,
                    color: '#60A5FA',
                    wordBreak: 'break-all',
                  }}
                >
                  {selectedNode.stableId}
                </span>
              </div>
              <button
                onClick={() => copyToClipboard(selectedNode.stableId)}
                style={{
                  background: copiedId ? '#15803D' : '#1E293B',
                  border: `1px solid ${BORDER}`,
                  borderRadius: 6,
                  color: '#FFFFFF',
                  padding: '4px 8px',
                  fontSize: 10,
                  fontWeight: 600,
                  cursor: 'pointer',
                  flexShrink: 0,
                }}
              >
                {copiedId ? 'Copied!' : 'Copy'}
              </button>
            </div>

            {/* Attributes Grid (Only fields from TraceNode) */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8, fontSize: 12 }}>
              {selectedNode.title && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ color: MUTED, fontSize: 11 }}>Title / Reference</span>
                  <span style={{ color: TEXT_MAIN, fontWeight: 600 }}>{selectedNode.title}</span>
                </div>
              )}

              {selectedNode.sourceType && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ color: MUTED, fontSize: 11 }}>Source System</span>
                  <span style={{ color: '#86EFAC', fontWeight: 700, fontFamily: 'monospace' }}>
                    {selectedNode.sourceType}
                  </span>
                </div>
              )}

              {selectedNode.status && (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                  <span style={{ color: MUTED, fontSize: 11 }}>Record Status</span>
                  <span style={{ color: TEXT_SEC, fontWeight: 600 }}>{selectedNode.status}</span>
                </div>
              )}
            </div>

            {/* Connected Relationships Sections */}
            <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 10, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {/* Incoming */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: TEXT_SEC, textTransform: 'uppercase' }}>
                  Incoming ({selectedNodeRelationships.incoming.length})
                </span>
                {selectedNodeRelationships.incoming.length === 0 ? (
                  <div style={{ fontSize: 11, color: MUTED, marginTop: 4 }}>No incoming edges</div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 6 }}>
                    {selectedNodeRelationships.incoming.map((item, i) => (
                      <div
                        key={i}
                        onClick={() => item.neighbor && setSelectedNodeKey(getNodeKey(item.neighbor.label, item.neighbor.stableId))}
                        style={{
                          background: '#0B1120',
                          border: `1px solid ${BORDER}`,
                          borderRadius: 6,
                          padding: '6px 8px',
                          fontSize: 11,
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                        }}
                      >
                        <span style={{ color: '#94A3B8', fontFamily: 'monospace' }}>
                          {item.edge.fromLabel}:{item.edge.fromStableId}
                        </span>
                        <span style={{ color: getRelColor(item.edge.type).stroke, fontWeight: 700, fontSize: 10 }}>
                          ─[{item.edge.type}]▶
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Outgoing */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: TEXT_SEC, textTransform: 'uppercase' }}>
                  Outgoing ({selectedNodeRelationships.outgoing.length})
                </span>
                {selectedNodeRelationships.outgoing.length === 0 ? (
                  <div style={{ fontSize: 11, color: MUTED, marginTop: 4 }}>No outgoing edges</div>
                ) : (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4, marginTop: 6 }}>
                    {selectedNodeRelationships.outgoing.map((item, i) => (
                      <div
                        key={i}
                        onClick={() => item.neighbor && setSelectedNodeKey(getNodeKey(item.neighbor.label, item.neighbor.stableId))}
                        style={{
                          background: '#0B1120',
                          border: `1px solid ${BORDER}`,
                          borderRadius: 6,
                          padding: '6px 8px',
                          fontSize: 11,
                          cursor: 'pointer',
                          display: 'flex',
                          alignItems: 'center',
                          gap: 6,
                        }}
                      >
                        <span style={{ color: getRelColor(item.edge.type).stroke, fontWeight: 700, fontSize: 10 }}>
                          ─[{item.edge.type}]▶
                        </span>
                        <span style={{ color: '#94A3B8', fontFamily: 'monospace' }}>
                          {item.edge.toLabel}:{item.edge.toStableId}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>

            {/* Footnote */}
            <div style={{ fontSize: 10, color: MUTED, borderTop: `1px solid ${BORDER}`, paddingTop: 8 }}>
              Investigation-scoped traceability from Neo4j. Authoritative edge directions preserved.
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
