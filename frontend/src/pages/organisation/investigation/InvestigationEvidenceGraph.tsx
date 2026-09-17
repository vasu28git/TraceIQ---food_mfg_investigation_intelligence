import React, { useEffect, useState, useCallback, useMemo, useRef } from 'react'
import { getApiErrorMessage } from '../../../api/client'
import { traceIncident, discoverInvestigationEvidence } from '../../../services/investigationService'

// Theme constants matching TraceIQ dark design system
const CARD_BG = '#111827'
const BORDER = '#1E293B'
const BORDER_LIGHT = '#334155'
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

export type NodeCategory =
  | 'incident'
  | 'target_batch'
  | 'machine'
  | 'warehouse'
  | 'shipment'
  | 'supplier'
  | 'product'
  | 'evidence'
  | 'co_batch'

interface LayoutNode {
  key: string
  node: TraceNode
  x: number
  y: number
  width: number
  height: number
  category: NodeCategory
  isTargetBatch: boolean
}

interface LayoutEdge {
  key: string
  fromKey: string
  toKey: string
  type: string
  semanticType: string
  fromNode: TraceNode
  toNode: TraceNode
  pathD: string
  midX: number
  midY: number
}

function getNodeKey(label: string, stableId: string): string {
  return `${label}:${stableId}`
}

function getSemanticRelLabel(rel: TraceRel, fromNode?: TraceNode, toNode?: TraceNode): string {
  if (!rel) return 'RELATED_TO'
  const type = rel.type || ''

  if (type === 'TARGETS') return 'TARGETS'
  if (type === 'HAS_EVIDENCE') return 'HAS_EVIDENCE'
  if (type === 'PRODUCED_ON') return 'PRODUCED_ON'
  if (type === 'STORED_IN') return 'STORED_IN'
  if (type === 'SHIPPED_TO') return 'SHIPPED_TO'
  if (type === 'SUPPLIED_BY') return 'SUPPLIED_BY'
  if (type === 'PRODUCT_OF') return 'PRODUCT_OF'
  if (type === 'REFERENCES') return 'REFERENCES'

  // Map ASSOCIATED_WITH / RELATED_TO by connected entity types
  const toLabel = (toNode?.label || rel.toLabel || '').toLowerCase()
  const fromLabel = (fromNode?.label || rel.fromLabel || '').toLowerCase()

  if (fromLabel === 'batch' || toLabel === 'batch') {
    if (toLabel === 'machine' || fromLabel === 'machine') return 'PRODUCED_ON'
    if (toLabel === 'warehouse' || fromLabel === 'warehouse') return 'STORED_IN'
    if (toLabel === 'supplier' || fromLabel === 'supplier') return 'SUPPLIED_BY'
    if (toLabel === 'customer' || fromLabel === 'customer' || toLabel === 'shipment' || fromLabel === 'shipment') return 'SHIPPED_TO'
    if (toLabel === 'product' || fromLabel === 'product') return 'PRODUCT_OF'
  }

  if (fromLabel === 'evidence' || toLabel === 'evidence') {
    return 'REFERENCES'
  }

  return type || 'RELATED_TO'
}

function getNodeCategory(node: TraceNode, isTarget: boolean): NodeCategory {
  if (node.label === 'Incident') return 'incident'
  if (node.label === 'Batch') return isTarget ? 'target_batch' : 'co_batch'
  if (node.label === 'Evidence') return 'evidence'

  const l = node.label.toLowerCase()
  if (l === 'machine') return 'machine'
  if (l === 'warehouse') return 'warehouse'
  if (l === 'supplier') return 'supplier'
  if (l === 'customer' || l === 'shipment') return 'shipment'
  if (l === 'product') return 'product'

  return 'evidence'
}

function getCategoryStyle(category: NodeCategory) {
  switch (category) {
    case 'incident':
      return {
        bg: '#1E1B4B',
        border: '#818CF8',
        text: '#EEF2FF',
        badgeBg: '#4338CA',
        badgeFg: '#EEF2FF',
        icon: '🚨',
        title: 'Incident',
      }
    case 'target_batch':
      return {
        bg: '#451A03',
        border: '#F59E0B',
        text: '#FEF3C7',
        badgeBg: '#B45309',
        badgeFg: '#FFFBEB',
        icon: '🎯',
        title: 'Target Batch',
      }
    case 'machine':
      return {
        bg: '#083344',
        border: '#06B6D4',
        text: '#CFFAFE',
        badgeBg: '#0E7490',
        badgeFg: '#E0F2FE',
        icon: '⚙️',
        title: 'Machine',
      }
    case 'warehouse':
      return {
        bg: '#042F2E',
        border: '#14B8A6',
        text: '#CCFBF1',
        badgeBg: '#0F766E',
        badgeFg: '#F0FDFA',
        icon: '🏢',
        title: 'Warehouse / Zone',
      }
    case 'shipment':
      return {
        bg: '#172554',
        border: '#3B82F6',
        text: '#DBEAFE',
        badgeBg: '#1D4ED8',
        badgeFg: '#EFF6FF',
        icon: '🚚',
        title: 'Shipment / Customer',
      }
    case 'supplier':
      return {
        bg: '#431407',
        border: '#F97316',
        text: '#FFEDD5',
        badgeBg: '#C2410C',
        badgeFg: '#FFF7ED',
        icon: '🏭',
        title: 'Supplier',
      }
    case 'product':
      return {
        bg: '#2E1065',
        border: '#A855F7',
        text: '#EDE9FE',
        badgeBg: '#7C3AED',
        badgeFg: '#FAF5FF',
        icon: '🏷️',
        title: 'Product',
      }
    case 'evidence':
      return {
        bg: '#052E16',
        border: '#22C55E',
        text: '#DCFCE7',
        badgeBg: '#15803D',
        badgeFg: '#F0FDF4',
        icon: '📄',
        title: 'Evidence',
      }
    case 'co_batch':
      return {
        bg: '#1E293B',
        border: '#64748B',
        text: '#CBD5E1',
        badgeBg: '#334155',
        badgeFg: '#E2E8F0',
        icon: '📦',
        title: 'Co-Batch',
      }
  }
}

function getRelStyle(semanticType: string) {
  switch (semanticType) {
    case 'TARGETS':
      return { stroke: '#F59E0B', badgeBg: '#78350F', badgeFg: '#FDE68A', label: 'TARGETS' }
    case 'PRODUCED_ON':
      return { stroke: '#06B6D4', badgeBg: '#0E7490', badgeFg: '#CFFAFE', label: 'PRODUCED_ON' }
    case 'STORED_IN':
      return { stroke: '#14B8A6', badgeBg: '#0F766E', badgeFg: '#CCFBF1', label: 'STORED_IN' }
    case 'SHIPPED_TO':
      return { stroke: '#3B82F6', badgeBg: '#1D4ED8', badgeFg: '#DBEAFE', label: 'SHIPPED_TO' }
    case 'SUPPLIED_BY':
      return { stroke: '#F97316', badgeBg: '#C2410C', badgeFg: '#FFEDD5', label: 'SUPPLIED_BY' }
    case 'PRODUCT_OF':
      return { stroke: '#A855F7', badgeBg: '#7C3AED', badgeFg: '#EDE9FE', label: 'PRODUCT_OF' }
    case 'HAS_EVIDENCE':
      return { stroke: '#22C55E', badgeBg: '#15803D', badgeFg: '#DCFCE7', label: 'HAS_EVIDENCE' }
    case 'REFERENCES':
      return { stroke: '#38BDF8', badgeBg: '#0369A1', badgeFg: '#E0F2FE', label: 'REFERENCES' }
    case 'RELATED_TO':
    case 'ASSOCIATED_WITH':
      return { stroke: '#94A3B8', badgeBg: '#334155', badgeFg: '#F1F5F9', label: 'RELATED_TO' }
    default:
      return { stroke: '#94A3B8', badgeBg: '#334155', badgeFg: '#F1F5F9', label: semanticType }
  }
}

function getSourceTypeBadge(sourceType?: string | null) {
  if (!sourceType) return null
  const s = sourceType.toUpperCase()
  let bg = '#1E293B'
  let color = '#94A3B8'
  if (s === 'CMMS') {
    bg = '#082F49'
    color = '#38BDF8'
  } else if (s === 'MES') {
    bg = '#451A03'
    color = '#FBBF24'
  } else if (s === 'LIMS') {
    bg = '#3B0764'
    color = '#C084FC'
  } else if (s.includes('WAREHOUSE')) {
    bg = '#042F2E'
    color = '#2DD4BF'
  } else if (s.includes('SHIP')) {
    bg = '#172554'
    color = '#60A5FA'
  } else if (s.includes('SOP')) {
    bg = '#4A044E'
    color = '#F472B6'
  }
  return { bg, color, text: s }
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

  // View & Interactive State
  const [zoom, setZoom] = useState<number>(0.85)
  const [pan, setPan] = useState<{ x: number; y: number }>({ x: 40, y: 30 })
  const [isDragging, setIsDragging] = useState(false)
  const [dragStart, setDragStart] = useState<{ x: number; y: number }>({ x: 0, y: 0 })
  const [selectedNodeKey, setSelectedNodeKey] = useState<string | null>(null)
  const [selectedEdgeKey, setSelectedEdgeKey] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')
  const [copiedId, setCopiedId] = useState(false)

  // Filter Toggles
  const [viewMode, setViewMode] = useState<'path' | 'all'>('path')
  const [visibleCategories, setVisibleCategories] = useState<Record<NodeCategory, boolean>>({
    incident: true,
    target_batch: true,
    machine: true,
    warehouse: true,
    shipment: true,
    supplier: true,
    product: true,
    evidence: true,
    co_batch: false,
  })
  const [evidenceSourceFilters, setEvidenceSourceFilters] = useState<Record<string, boolean>>({
    CMMS: true,
    MES: true,
    LIMS: true,
    WAREHOUSE: true,
    SHIPMENT: true,
  })

  const svgRef = useRef<SVGSVGElement | null>(null)

  // Fetch Traceability API
  const loadGraph = useCallback(async () => {
    setLoading(true)
    setError(null)
    setIsEmpty(false)
    setSelectedNodeKey(null)
    setSelectedEdgeKey(null)
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

  // Combine Incident & All Nodes
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

  // Identify Target Batch
  const targetBatchNode = useMemo<TraceNode | null>(() => {
    if (!data) return null
    // 1. Look for TARGETS relationship from Incident
    const targetRel = data.relationships.find(
      (r) => r.type === 'TARGETS' && r.fromLabel === 'Incident' && r.toLabel === 'Batch'
    )
    if (targetRel) {
      const target = nodeMap.get(getNodeKey('Batch', targetRel.toStableId))
      if (target) return target
    }
    // 2. Look for batch matching batchReference prop
    if (batchReference) {
      const match = allNodes.find((n) => n.label === 'Batch' && n.stableId === batchReference)
      if (match) return match
    }
    // 3. Fallback to first Batch node
    return allNodes.find((n) => n.label === 'Batch') || null
  }, [data, nodeMap, batchReference, allNodes])

  const targetBatchStableId = targetBatchNode?.stableId || batchReference || ''

  // Node Category Map
  const nodeCategoryMap = useMemo(() => {
    const map = new Map<string, NodeCategory>()
    allNodes.forEach((n) => {
      const isTarget = n.label === 'Batch' && (n.stableId === targetBatchStableId || n === targetBatchNode)
      map.set(getNodeKey(n.label, n.stableId), getNodeCategory(n, isTarget))
    })
    return map
  }, [allNodes, targetBatchStableId, targetBatchNode])

  // Category counts
  const categoryCounts = useMemo(() => {
    const counts: Record<NodeCategory, number> = {
      incident: 0,
      target_batch: 0,
      machine: 0,
      warehouse: 0,
      shipment: 0,
      supplier: 0,
      product: 0,
      evidence: 0,
      co_batch: 0,
    }
    allNodes.forEach((n) => {
      const cat = nodeCategoryMap.get(getNodeKey(n.label, n.stableId)) || 'evidence'
      counts[cat] = (counts[cat] || 0) + 1
    })
    return counts
  }, [allNodes, nodeCategoryMap])

  // Compute Layout: Hierarchical Arrangement around Target Batch
  const { layoutNodes, layoutEdges, bounds } = useMemo(() => {
    if (!data || allNodes.length === 0) {
      return { layoutNodes: [], layoutEdges: [], bounds: { minX: 0, maxX: 900, minY: 0, maxY: 600, width: 900, height: 600 } }
    }

    const NODE_WIDTH = 220
    const NODE_HEIGHT = 74
    const TARGET_BATCH_WIDTH = 250
    const TARGET_BATCH_HEIGHT = 82
    const INCIDENT_WIDTH = 230
    const INCIDENT_HEIGHT = 78

    // Direct connections to Target Batch
    const directEntities = new Map<string, TraceNode>()
    const directBatchEvidence = new Map<string, TraceNode>()
    const entityEvidenceMap = new Map<string, TraceNode[]>() // EntityKey -> Evidence[]

    // Identify direct connections
    data.relationships.forEach((r) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)
      const isFromTarget = r.fromLabel === 'Batch' && r.fromStableId === targetBatchStableId
      const isToTarget = r.toLabel === 'Batch' && r.toStableId === targetBatchStableId

      if (isFromTarget) {
        const neighbor = nodeMap.get(toKey)
        if (neighbor && neighbor.label !== 'Incident') {
          if (neighbor.label === 'Evidence') directBatchEvidence.set(toKey, neighbor)
          else directEntities.set(toKey, neighbor)
        }
      } else if (isToTarget) {
        const neighbor = nodeMap.get(fromKey)
        if (neighbor && neighbor.label !== 'Incident') {
          if (neighbor.label === 'Evidence') directBatchEvidence.set(fromKey, neighbor)
          else directEntities.set(fromKey, neighbor)
        }
      }
    })

    // Map evidence connected to domain entities
    data.relationships.forEach((r) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)

      if (r.fromLabel === 'Evidence' && directEntities.has(toKey)) {
        const ev = nodeMap.get(fromKey)
        if (ev) {
          const list = entityEvidenceMap.get(toKey) || []
          if (!list.some((item) => item.stableId === ev.stableId)) {
            list.push(ev)
            entityEvidenceMap.set(toKey, list)
          }
        }
      } else if (r.toLabel === 'Evidence' && directEntities.has(fromKey)) {
        const ev = nodeMap.get(toKey)
        if (ev) {
          const list = entityEvidenceMap.get(fromKey) || []
          if (!list.some((item) => item.stableId === ev.stableId)) {
            list.push(ev)
            entityEvidenceMap.set(fromKey, list)
          }
        }
      }
    })

    // Categorize direct entities into domain columns:
    // Supply Chain Flow: Supplier -> Machine -> Target Batch Evidence -> Warehouse -> Shipment/Customer -> Product
    const supplierEntities: TraceNode[] = []
    const machineEntities: TraceNode[] = []
    const warehouseEntities: TraceNode[] = []
    const shipmentEntities: TraceNode[] = []
    const productEntities: TraceNode[] = []

    directEntities.forEach((entity) => {
      const l = entity.label.toLowerCase()
      if (l === 'supplier') supplierEntities.push(entity)
      else if (l === 'machine') machineEntities.push(entity)
      else if (l === 'warehouse') warehouseEntities.push(entity)
      else if (l === 'customer' || l === 'shipment') shipmentEntities.push(entity)
      else if (l === 'product') productEntities.push(entity)
    })

    // Filter check function
    const isNodeVisible = (node: TraceNode): boolean => {
      const key = getNodeKey(node.label, node.stableId)
      const cat = nodeCategoryMap.get(key) || 'evidence'

      // Check category toggle
      if (!visibleCategories[cat]) return false

      // Check evidence source filter
      if (cat === 'evidence' && node.sourceType) {
        const s = node.sourceType.toUpperCase()
        let matched = false
        for (const [prefix, isEnabled] of Object.entries(evidenceSourceFilters)) {
          if (s.includes(prefix) && isEnabled) {
            matched = true
            break
          }
        }
        if (!matched && !evidenceSourceFilters.OTHER) return false
      }

      // Check Investigation Path vs All
      if (viewMode === 'path') {
        if (cat === 'co_batch') return false
        if (cat === 'incident' || cat === 'target_batch') return true
        if (directEntities.has(key)) return true
        if (directBatchEvidence.has(key)) return true
        // Evidence connected to direct entities
        for (const [, evList] of entityEvidenceMap.entries()) {
          if (evList.some((e) => e.stableId === node.stableId)) return true
        }
        return false
      }

      return true
    }

    // Build Columns for Investigation Flow
    type ColumnDef = {
      title: string
      headerEntities: TraceNode[]
      evidenceNodes: TraceNode[]
    }

    const columns: ColumnDef[] = []

    if (supplierEntities.some(isNodeVisible)) {
      const valid = supplierEntities.filter(isNodeVisible)
      const evs: TraceNode[] = []
      valid.forEach((e) => {
        const list = entityEvidenceMap.get(getNodeKey(e.label, e.stableId)) || []
        list.filter(isNodeVisible).forEach((ev) => evs.push(ev))
      })
      columns.push({ title: 'Raw Material Supplier', headerEntities: valid, evidenceNodes: evs })
    }

    if (machineEntities.some(isNodeVisible)) {
      const valid = machineEntities.filter(isNodeVisible)
      const evs: TraceNode[] = []
      valid.forEach((e) => {
        const list = entityEvidenceMap.get(getNodeKey(e.label, e.stableId)) || []
        list.filter(isNodeVisible).forEach((ev) => evs.push(ev))
      })
      columns.push({ title: 'Production Equipment', headerEntities: valid, evidenceNodes: evs })
    }

    // Direct Batch Evidence Column (Quality Tests / MES Production Run)
    const directBatchEvList = Array.from(directBatchEvidence.values()).filter(isNodeVisible)
    if (directBatchEvList.length > 0) {
      columns.push({ title: 'Batch Quality & Records', headerEntities: [], evidenceNodes: directBatchEvList })
    }

    if (warehouseEntities.some(isNodeVisible)) {
      const valid = warehouseEntities.filter(isNodeVisible)
      const evs: TraceNode[] = []
      valid.forEach((e) => {
        const list = entityEvidenceMap.get(getNodeKey(e.label, e.stableId)) || []
        list.filter(isNodeVisible).forEach((ev) => evs.push(ev))
      })
      columns.push({ title: 'Storage & Warehouse', headerEntities: valid, evidenceNodes: evs })
    }

    if (shipmentEntities.some(isNodeVisible)) {
      const valid = shipmentEntities.filter(isNodeVisible)
      const evs: TraceNode[] = []
      valid.forEach((e) => {
        const list = entityEvidenceMap.get(getNodeKey(e.label, e.stableId)) || []
        list.filter(isNodeVisible).forEach((ev) => evs.push(ev))
      })
      columns.push({ title: 'Distribution & Customers', headerEntities: valid, evidenceNodes: evs })
    }

    if (productEntities.some(isNodeVisible)) {
      const valid = productEntities.filter(isNodeVisible)
      const evs: TraceNode[] = []
      valid.forEach((e) => {
        const list = entityEvidenceMap.get(getNodeKey(e.label, e.stableId)) || []
        list.filter(isNodeVisible).forEach((ev) => evs.push(ev))
      })
      columns.push({ title: 'Finished Product', headerEntities: valid, evidenceNodes: evs })
    }

    // Ensure at least 1 column space if columns is empty
    const numColumns = Math.max(1, columns.length)
    const COLUMN_GAP = 70
    const COLUMN_WIDTH = 260
    const totalColumnsWidth = numColumns * COLUMN_WIDTH + (numColumns - 1) * COLUMN_GAP
    const startX = 60
    const centerX = startX + totalColumnsWidth / 2

    const positionedNodes: LayoutNode[] = []
    const layoutNodeMap = new Map<string, LayoutNode>()

    // Place 1: Incident Node (Top Center)
    const incidentNode = data.incidentNode
    if (isNodeVisible(incidentNode)) {
      const incKey = getNodeKey(incidentNode.label, incidentNode.stableId)
      const incLayout: LayoutNode = {
        key: incKey,
        node: incidentNode,
        x: centerX - INCIDENT_WIDTH / 2,
        y: 40,
        width: INCIDENT_WIDTH,
        height: INCIDENT_HEIGHT,
        category: 'incident',
        isTargetBatch: false,
      }
      positionedNodes.push(incLayout)
      layoutNodeMap.set(incKey, incLayout)
    }

    // Place 2: Target Batch Node (Center, below Incident)
    if (targetBatchNode && isNodeVisible(targetBatchNode)) {
      const batchKey = getNodeKey(targetBatchNode.label, targetBatchNode.stableId)
      const batchLayout: LayoutNode = {
        key: batchKey,
        node: targetBatchNode,
        x: centerX - TARGET_BATCH_WIDTH / 2,
        y: 165,
        width: TARGET_BATCH_WIDTH,
        height: TARGET_BATCH_HEIGHT,
        category: 'target_batch',
        isTargetBatch: true,
      }
      positionedNodes.push(batchLayout)
      layoutNodeMap.set(batchKey, batchLayout)
    }

    // Place 3: Domain Columns & Evidence Clusters below Target Batch
    let maxEvidenceY = 290
    columns.forEach((col, colIdx) => {
      const colX = startX + colIdx * (COLUMN_WIDTH + COLUMN_GAP)
      let currentY = 300

      // A. Header Entities in this column
      col.headerEntities.forEach((entity) => {
        const entKey = getNodeKey(entity.label, entity.stableId)
        if (!layoutNodeMap.has(entKey)) {
          const entLayout: LayoutNode = {
            key: entKey,
            node: entity,
            x: colX + (COLUMN_WIDTH - NODE_WIDTH) / 2,
            y: currentY,
            width: NODE_WIDTH,
            height: NODE_HEIGHT,
            category: nodeCategoryMap.get(entKey) || 'evidence',
            isTargetBatch: false,
          }
          positionedNodes.push(entLayout)
          layoutNodeMap.set(entKey, entLayout)
          currentY += NODE_HEIGHT + 16
        }
      })

      // Add a gap before evidence
      if (col.headerEntities.length > 0 && col.evidenceNodes.length > 0) {
        currentY += 14
      }

      // B. Evidence records under this column
      // If <= 5 items, single vertical column. If > 5, 2-column compact grid!
      const evCount = col.evidenceNodes.length
      if (evCount <= 5) {
        col.evidenceNodes.forEach((ev) => {
          const evKey = getNodeKey(ev.label, ev.stableId)
          if (!layoutNodeMap.has(evKey)) {
            const evLayout: LayoutNode = {
              key: evKey,
              node: ev,
              x: colX + (COLUMN_WIDTH - NODE_WIDTH) / 2,
              y: currentY,
              width: NODE_WIDTH,
              height: NODE_HEIGHT,
              category: 'evidence',
              isTargetBatch: false,
            }
            positionedNodes.push(evLayout)
            layoutNodeMap.set(evKey, evLayout)
            currentY += NODE_HEIGHT + 14
          }
        })
      } else {
        // 2-column compact grid
        const GRID_WIDTH = 210
        col.evidenceNodes.forEach((ev, evIdx) => {
          const evKey = getNodeKey(ev.label, ev.stableId)
          if (!layoutNodeMap.has(evKey)) {
            const colSub = evIdx % 2
            const rowSub = Math.floor(evIdx / 2)
            const gx = colX - 50 + colSub * (GRID_WIDTH + 14)
            const gy = currentY + rowSub * (NODE_HEIGHT + 12)
            const evLayout: LayoutNode = {
              key: evKey,
              node: ev,
              x: gx,
              y: gy,
              width: GRID_WIDTH,
              height: NODE_HEIGHT,
              category: 'evidence',
              isTargetBatch: false,
            }
            positionedNodes.push(evLayout)
            layoutNodeMap.set(evKey, evLayout)
            if (gy + NODE_HEIGHT > maxEvidenceY) maxEvidenceY = gy + NODE_HEIGHT
          }
        })
        const totalRows = Math.ceil(evCount / 2)
        currentY += totalRows * (NODE_HEIGHT + 12)
      }

      if (currentY > maxEvidenceY) maxEvidenceY = currentY
    })

    // Place 4: Co-Batches section (if in 'all' view and enabled)
    let finalMaxY = maxEvidenceY + 60
    if (viewMode === 'all' && visibleCategories.co_batch) {
      const coBatches = allNodes.filter(
        (n) => n.label === 'Batch' && n.stableId !== targetBatchStableId && isNodeVisible(n)
      )
      if (coBatches.length > 0) {
        const coStartY = maxEvidenceY + 70
        const CO_WIDTH = 200
        const CO_HEIGHT = 65
        const CO_COLS = Math.min(5, Math.max(3, Math.floor(totalColumnsWidth / (CO_WIDTH + 20))))

        coBatches.forEach((cb, cbIdx) => {
          const cbKey = getNodeKey(cb.label, cb.stableId)
          const colI = cbIdx % CO_COLS
          const rowI = Math.floor(cbIdx / CO_COLS)
          const cx = startX + colI * (CO_WIDTH + 24)
          const cy = coStartY + rowI * (CO_HEIGHT + 14)

          const cbLayout: LayoutNode = {
            key: cbKey,
            node: cb,
            x: cx,
            y: cy,
            width: CO_WIDTH,
            height: CO_HEIGHT,
            category: 'co_batch',
            isTargetBatch: false,
          }
          positionedNodes.push(cbLayout)
          layoutNodeMap.set(cbKey, cbLayout)
          if (cy + CO_HEIGHT > finalMaxY) finalMaxY = cy + CO_HEIGHT + 40
        })
      }
    }

    // Calculate Edges connecting visible nodes
    const positionedEdges: LayoutEdge[] = []
    data.relationships.forEach((r, idx) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)
      const src = layoutNodeMap.get(fromKey)
      const tgt = layoutNodeMap.get(toKey)

      if (src && tgt) {
        const fromNode = src.node
        const toNode = tgt.node
        const semanticType = getSemanticRelLabel(r, fromNode, toNode)

        // Route edge cleanly
        let startX = src.x + src.width / 2
        let startY = src.y + src.height
        let endX = tgt.x + tgt.width / 2
        let endY = tgt.y

        // Lateral or upward routing
        if (src.y >= tgt.y) {
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
          const cpY = Math.max(30, Math.abs(deltaY) * 0.45)
          pathD = `M ${startX} ${startY} C ${startX} ${startY + cpY}, ${endX} ${endY - cpY}, ${endX} ${endY}`
        } else {
          // Curved arc between side-by-side nodes
          const arcY = startY - 35
          pathD = `M ${startX} ${startY} C ${startX + deltaX * 0.25} ${arcY}, ${endX - deltaX * 0.25} ${arcY}, ${endX} ${endY}`
        }

        const midX = (startX + endX) / 2
        const midY = Math.abs(deltaY) < 20 ? startY - 26 : (startY + endY) / 2

        positionedEdges.push({
          key: `${fromKey}->${toKey}-${idx}`,
          fromKey,
          toKey,
          type: r.type,
          semanticType,
          fromNode,
          toNode,
          pathD,
          midX,
          midY,
        })
      }
    })

    // Compute bounding box
    let minX = 99999
    let maxX = -99999
    let minY = 99999
    let maxY = -99999

    positionedNodes.forEach((n) => {
      if (n.x < minX) minX = n.x
      if (n.x + n.width > maxX) maxX = n.x + n.width
      if (n.y < minY) minY = n.y
      if (n.y + n.height > maxY) maxY = n.y + n.height
    })

    if (minX === 99999) {
      minX = 0
      maxX = 1000
      minY = 0
      maxY = 700
    }

    const b = {
      minX: minX - 60,
      maxX: maxX + 60,
      minY: minY - 40,
      maxY: Math.max(maxY + 60, finalMaxY),
      width: maxX - minX + 120,
      height: Math.max(maxY - minY + 100, 650),
    }

    return { layoutNodes: positionedNodes, layoutEdges: positionedEdges, bounds: b }
  }, [
    data,
    allNodes,
    nodeMap,
    targetBatchNode,
    targetBatchStableId,
    nodeCategoryMap,
    visibleCategories,
    evidenceSourceFilters,
    viewMode,
  ])

  const layoutNodeMap = useMemo(() => {
    const map = new Map<string, LayoutNode>()
    for (const n of layoutNodes) {
      map.set(n.key, n)
    }
    return map
  }, [layoutNodes])

  // Fit View into Viewport
  const fitView = useCallback(() => {
    if (!svgRef.current || layoutNodes.length === 0) {
      setZoom(0.85)
      setPan({ x: 40, y: 30 })
      return
    }
    const rect = svgRef.current.getBoundingClientRect()
    const vpW = rect.width || 900
    const vpH = rect.height || 640

    const scaleX = (vpW - 80) / bounds.width
    const scaleY = (vpH - 80) / bounds.height
    const computedZoom = Math.min(Math.max(0.4, Math.min(scaleX, scaleY)), 1.15)

    const offsetX = (vpW - bounds.width * computedZoom) / 2 - bounds.minX * computedZoom
    const offsetY = 25 - bounds.minY * computedZoom

    setZoom(computedZoom)
    setPan({ x: Math.max(20, offsetX), y: Math.max(20, offsetY) })
  }, [bounds, layoutNodes.length])

  // Auto-fit on initial graph load
  useEffect(() => {
    if (layoutNodes.length > 0) {
      const t = setTimeout(() => fitView(), 80)
      return () => clearTimeout(t)
    }
  }, [data, viewMode])

  // Highlights based on Selection or Search
  const { highlightedNodeKeys, highlightedEdgeKeys } = useMemo(() => {
    const nodes = new Set<string>()
    const edges = new Set<string>()

    if (selectedNodeKey) {
      nodes.add(selectedNodeKey)
      layoutEdges.forEach((e) => {
        if (e.fromKey === selectedNodeKey) {
          nodes.add(e.toKey)
          edges.add(e.key)
        } else if (e.toKey === selectedNodeKey) {
          nodes.add(e.fromKey)
          edges.add(e.key)
        }
      })
    } else if (selectedEdgeKey) {
      const e = layoutEdges.find((edge) => edge.key === selectedEdgeKey)
      if (e) {
        nodes.add(e.fromKey)
        nodes.add(e.toKey)
        edges.add(e.key)
      }
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
          nodes.add(ln.key)
        }
      })
    }

    return { highlightedNodeKeys: nodes, highlightedEdgeKeys: edges }
  }, [selectedNodeKey, selectedEdgeKey, searchTerm, layoutEdges, layoutNodes])

  // Selected Node Details
  const selectedNode = useMemo(() => {
    if (!selectedNodeKey) return null
    return nodeMap.get(selectedNodeKey) || null
  }, [selectedNodeKey, nodeMap])

  // Selected Edge Details
  const selectedEdge = useMemo(() => {
    if (!selectedEdgeKey) return null
    return layoutEdges.find((e) => e.key === selectedEdgeKey) || null
  }, [selectedEdgeKey, layoutEdges])

  // Relationships of selected node
  const selectedNodeRelationships = useMemo(() => {
    if (!selectedNodeKey || !data) return { incoming: [], outgoing: [] }
    const incoming: { edge: TraceRel; neighbor: TraceNode | undefined; semanticLabel: string }[] = []
    const outgoing: { edge: TraceRel; neighbor: TraceNode | undefined; semanticLabel: string }[] = []

    data.relationships.forEach((r) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)
      const fromN = nodeMap.get(fromKey)
      const toN = nodeMap.get(toKey)

      if (fromKey === selectedNodeKey) {
        outgoing.push({
          edge: r,
          neighbor: toN,
          semanticLabel: getSemanticRelLabel(r, fromN, toN),
        })
      }
      if (toKey === selectedNodeKey) {
        incoming.push({
          edge: r,
          neighbor: fromN,
          semanticLabel: getSemanticRelLabel(r, fromN, toN),
        })
      }
    })

    return { incoming, outgoing }
  }, [selectedNodeKey, data, nodeMap])

  // Related Evidence for selected node (if entity or batch)
  const relatedEvidenceForSelectedNode = useMemo(() => {
    if (!selectedNode || !data) return []
    if (selectedNode.label === 'Evidence') return []

    const evSet = new Set<TraceNode>()
    const targetKey = getNodeKey(selectedNode.label, selectedNode.stableId)

    data.relationships.forEach((r) => {
      const fromKey = getNodeKey(r.fromLabel, r.fromStableId)
      const toKey = getNodeKey(r.toLabel, r.toStableId)

      if (fromKey === targetKey && r.toLabel === 'Evidence') {
        const ev = nodeMap.get(toKey)
        if (ev) evSet.add(ev)
      } else if (toKey === targetKey && r.fromLabel === 'Evidence') {
        const ev = nodeMap.get(fromKey)
        if (ev) evSet.add(ev)
      }
    })

    return Array.from(evSet)
  }, [selectedNode, data, nodeMap])

  // Zoom & Pan Handlers
  const handleZoom = (delta: number) => {
    setZoom((z) => Math.min(Math.max(0.3, z + delta), 2.2))
  }

  const handleResetView = () => {
    fitView()
    setSelectedNodeKey(null)
    setSelectedEdgeKey(null)
    setSearchTerm('')
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
    const zoomFactor = e.deltaY < 0 ? 1.08 : 0.92
    setZoom((z) => Math.min(Math.max(0.3, z * zoomFactor), 2.2))
  }

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    setCopiedId(true)
    setTimeout(() => setCopiedId(false), 1800)
  }

  // Toggle Category
  const toggleCategory = (cat: NodeCategory) => {
    setVisibleCategories((prev) => ({ ...prev, [cat]: !prev[cat] }))
  }

  // Toggle Evidence Source
  const toggleEvidenceSource = (source: string) => {
    setEvidenceSourceFilters((prev) => ({ ...prev, [source]: !prev[source] }))
  }

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
          padding: 56,
          textAlign: 'center',
          color: TEXT_SEC,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 14,
        }}
      >
        <div
          style={{
            width: 40,
            height: 40,
            border: '3px solid #3B82F6',
            borderTopColor: 'transparent',
            borderRadius: '50%',
            animation: 'spin 0.8s linear infinite',
          }}
        />
        <style>{`@keyframes spin { 0% { transform: rotate(0deg); } 100% { transform: rotate(360deg); } }`}</style>
        <div style={{ fontSize: 15, fontWeight: 700, color: TEXT_MAIN }}>Loading Investigation Graph…</div>
        <div style={{ fontSize: 13, color: MUTED }}>
          Resolving domain traceability and connected evidence records
        </div>
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
          padding: 44,
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
            fontSize: 26,
            color: '#60A5FA',
          }}
        >
          🕸️
        </div>
        <div style={{ maxWidth: 460 }}>
          <h3 style={{ fontSize: 16, fontWeight: 700, color: TEXT_MAIN, margin: '0 0 6px 0' }}>
            No traceability evidence discovered yet
          </h3>
          <p style={{ fontSize: 13, color: TEXT_SEC, margin: 0, lineHeight: 1.5 }}>
            Evidence discovery traverses related manufacturing logs, equipment maintenance records, and storage zones
            for affected batches.
          </p>
        </div>
        <button
          onClick={recoverGraph}
          disabled={recovering}
          style={{
            marginTop: 6,
            padding: '8px 18px',
            background: '#1E293B',
            border: `1px solid ${BORDER}`,
            borderRadius: 8,
            color: '#93C5FD',
            cursor: 'pointer',
            fontSize: 13,
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

  const isAnyHighlight = highlightedNodeKeys.size > 0 || highlightedEdgeKeys.size > 0

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {/* 1. Header Controls Bar */}
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
        {/* Left: Investigation Info & Target Batch Pill */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <span style={{ fontSize: 15, fontWeight: 800, color: TEXT_MAIN }}>Evidence Traceability Graph</span>
            <span
              style={{
                fontSize: 11,
                background: '#1E1B4B',
                color: '#C7D2FE',
                border: '1px solid #4338CA',
                padding: '2px 8px',
                borderRadius: 999,
                fontWeight: 700,
              }}
            >
              Incident #{investigationId}
            </span>
          </div>

          {targetBatchNode && (
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 6,
                background: '#451A03',
                border: '1px solid #F59E0B',
                borderRadius: 8,
                padding: '3px 10px',
                fontSize: 12,
              }}
            >
              <span style={{ fontSize: 14 }}>🎯</span>
              <span style={{ color: '#FDE68A', fontWeight: 700 }}>Target Batch:</span>
              <span style={{ color: '#FFFBEB', fontFamily: 'monospace', fontWeight: 800 }}>
                {targetBatchNode.stableId}
              </span>
            </div>
          )}

          {/* View Mode Toggle: Investigation Path vs All */}
          <div
            style={{
              display: 'flex',
              background: '#0B1120',
              border: `1px solid ${BORDER}`,
              borderRadius: 8,
              padding: 2,
            }}
          >
            <button
              onClick={() => setViewMode('path')}
              style={{
                padding: '4px 10px',
                background: viewMode === 'path' ? '#1E293B' : 'transparent',
                color: viewMode === 'path' ? '#60A5FA' : TEXT_SEC,
                border: 0,
                borderRadius: 6,
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
              }}
              title="Focus strictly on target batch, connected machines/warehouses, and their evidence"
            >
              ⭐ Investigation Path
            </button>
            <button
              onClick={() => setViewMode('all')}
              style={{
                padding: '4px 10px',
                background: viewMode === 'all' ? '#1E293B' : 'transparent',
                color: viewMode === 'all' ? '#60A5FA' : TEXT_SEC,
                border: 0,
                borderRadius: 6,
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
              }}
              title="View all traversed batches and peripheral graph nodes"
            >
              🌐 Full Graph ({allNodes.length})
            </button>
          </div>
        </div>

        {/* Right: Search & Viewport Controls */}
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
              width: 170,
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
              title="Fit & Center View"
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
            title="Refresh Graph"
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

      {/* 2. Filter Toggles Bar (Requirement 9: Hide/Show node types & evidence categories) */}
      <div
        style={{
          background: CARD_BG,
          border: `1px solid ${BORDER}`,
          borderRadius: 10,
          padding: '8px 14px',
          display: 'flex',
          alignItems: 'center',
          gap: 12,
          flexWrap: 'wrap',
          fontSize: 11,
        }}
      >
        <span style={{ color: MUTED, fontWeight: 700, textTransform: 'uppercase', fontSize: 10, letterSpacing: 0.5 }}>
          Filter Nodes:
        </span>

        {/* Node Type Pills */}
        {(
          [
            'incident',
            'target_batch',
            'machine',
            'warehouse',
            'supplier',
            'shipment',
            'product',
            'evidence',
            ...(viewMode === 'all' ? ['co_batch' as NodeCategory] : []),
          ] as NodeCategory[]
        ).map((cat) => {
          const style = getCategoryStyle(cat)
          const active = visibleCategories[cat]
          const count = categoryCounts[cat] || 0
          if (count === 0 && cat !== 'incident' && cat !== 'target_batch') return null

          return (
            <button
              key={cat}
              onClick={() => toggleCategory(cat)}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 5,
                background: active ? style.bg : '#0F172A',
                border: `1px solid ${active ? style.border : BORDER}`,
                color: active ? style.text : MUTED,
                borderRadius: 6,
                padding: '3px 8px',
                fontSize: 11,
                fontWeight: 600,
                cursor: 'pointer',
                opacity: active ? 1 : 0.55,
                transition: 'all 0.15s ease',
              }}
            >
              <span>{style.icon}</span>
              <span>{style.title}</span>
              <span
                style={{
                  fontSize: 10,
                  background: active ? style.badgeBg : '#1E293B',
                  color: active ? style.badgeFg : MUTED,
                  padding: '1px 5px',
                  borderRadius: 999,
                  fontWeight: 700,
                }}
              >
                {count}
              </span>
            </button>
          )
        })}

        {/* Evidence Category Sub-Filters */}
        {visibleCategories.evidence && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 6, borderLeft: `1px solid ${BORDER}`, paddingLeft: 10 }}>
            <span style={{ color: MUTED, fontSize: 10 }}>Sources:</span>
            {['CMMS', 'MES', 'LIMS', 'WAREHOUSE'].map((src) => {
              const active = evidenceSourceFilters[src]
              const badge = getSourceTypeBadge(src)
              return (
                <button
                  key={src}
                  onClick={() => toggleEvidenceSource(src)}
                  style={{
                    background: active ? badge?.bg || '#1E293B' : '#0B1120',
                    border: `1px solid ${active ? badge?.color || BORDER : BORDER}`,
                    color: active ? badge?.color || TEXT_MAIN : MUTED,
                    borderRadius: 5,
                    padding: '2px 6px',
                    fontSize: 10,
                    fontWeight: 700,
                    cursor: 'pointer',
                    opacity: active ? 1 : 0.5,
                  }}
                >
                  {src}
                </button>
              )
            })}
          </div>
        )}

        <div style={{ marginLeft: 'auto', color: MUTED, fontSize: 11 }}>
          Showing <strong style={{ color: TEXT_MAIN }}>{layoutNodes.length}</strong> nodes,{' '}
          <strong style={{ color: TEXT_MAIN }}>{layoutEdges.length}</strong> edges
        </div>
      </div>

      {/* 3. Main Graph Canvas & Details Area */}
      <div style={{ display: 'flex', gap: 12, alignItems: 'stretch', position: 'relative' }}>
        {/* SVG Graph Viewport */}
        <div
          style={{
            flex: 1,
            minHeight: 650,
            height: 680,
            background: '#0B1120',
            border: `1px solid ${BORDER}`,
            borderRadius: 12,
            position: 'relative',
            overflow: 'hidden',
          }}
        >
          {/* Subtle Help Overlay */}
          <div
            style={{
              position: 'absolute',
              top: 10,
              left: 12,
              fontSize: 11,
              color: MUTED,
              pointerEvents: 'none',
              zIndex: 5,
            }}
          >
            Drag to pan · Scroll to zoom · Click node or relationship to inspect
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
              {/* Relationship Arrowheads */}
              {[
                'TARGETS',
                'PRODUCED_ON',
                'STORED_IN',
                'SHIPPED_TO',
                'SUPPLIED_BY',
                'PRODUCT_OF',
                'HAS_EVIDENCE',
                'REFERENCES',
                'RELATED_TO',
                'DEFAULT',
              ].map((type) => {
                const color = getRelStyle(type).stroke
                return (
                  <marker
                    key={type}
                    id={`arrow-${type}`}
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
                markerWidth="8"
                markerHeight="8"
                orient="auto"
              >
                <path d="M 0 1 L 10 5 L 0 9 z" fill="#60A5FA" />
              </marker>

              {/* Target Batch Glow Filter */}
              <filter id="gold-glow" x="-20%" y="-20%" width="140%" height="140%">
                <feDropShadow dx="0" dy="0" stdDeviation="6" floodColor="#F59E0B" floodOpacity="0.45" />
              </filter>

              {/* Grid Background Pattern */}
              <pattern id="grid-dots" x="0" y="0" width="28" height="28" patternUnits="userSpaceOnUse">
                <circle cx="2" cy="2" r="1" fill="#1E293B" />
              </pattern>
            </defs>

            {/* Grid Canvas Background */}
            <rect width="100%" height="100%" fill="url(#grid-dots)" />

            <g transform={`translate(${pan.x}, ${pan.y}) scale(${zoom})`}>
              {/* Render Directed Edges */}
              {layoutEdges.map((edge) => {
                const isSelected = selectedEdgeKey === edge.key
                const isHighlighted = highlightedEdgeKeys.has(edge.key)
                const isDimmed = isAnyHighlight && !isHighlighted && !isSelected

                const relStyle = getRelStyle(edge.semanticType)
                const strokeColor = isSelected ? '#60A5FA' : isHighlighted ? '#38BDF8' : relStyle.stroke
                const markerEnd = isSelected || isHighlighted ? 'url(#arrow-highlight)' : `url(#arrow-${edge.semanticType})`

                return (
                  <g
                    key={edge.key}
                    opacity={isDimmed ? 0.15 : 0.88}
                    onClick={(e) => {
                      e.stopPropagation()
                      setSelectedEdgeKey(isSelected ? null : edge.key)
                      setSelectedNodeKey(null)
                    }}
                    style={{ cursor: 'pointer' }}
                  >
                    {/* Invisible Wide Stroke for Easy Clicking (Requirement 8) */}
                    <path
                      d={edge.pathD}
                      fill="none"
                      stroke="transparent"
                      strokeWidth="18"
                      style={{ cursor: 'pointer' }}
                    />

                    {/* Visible Directed Path */}
                    <path
                      d={edge.pathD}
                      fill="none"
                      stroke={strokeColor}
                      strokeWidth={isSelected ? 3 : isHighlighted ? 2.4 : 1.5}
                      markerEnd={markerEnd}
                      strokeDasharray={edge.semanticType === 'RELATED_TO' ? '4 3' : undefined}
                      style={{ transition: 'stroke 0.15s ease, stroke-width 0.15s ease' }}
                    />

                    {/* Relationship Label Pill (Requirement 5) */}
                    <g transform={`translate(${edge.midX}, ${edge.midY})`}>
                      <rect
                        x="-46"
                        y="-9"
                        width="92"
                        height="18"
                        rx="9"
                        fill="#0B1120"
                        stroke={strokeColor}
                        strokeWidth={isSelected ? '1.8' : '1'}
                        opacity="0.95"
                      />
                      <text
                        x="0"
                        y="3"
                        fontSize="8.5"
                        fontWeight="800"
                        fill={strokeColor}
                        textAnchor="middle"
                        fontFamily="ui-monospace, monospace"
                        letterSpacing="0.4"
                      >
                        {edge.semanticType}
                      </text>
                    </g>
                  </g>
                )
              })}

              {/* Render Nodes */}
              {layoutNodes.map((ln) => {
                const node = ln.node
                const isSelected = selectedNodeKey === ln.key
                const isHighlighted = highlightedNodeKeys.has(ln.key)
                const isDimmed = isAnyHighlight && !isHighlighted && !isSelected

                const colors = getCategoryStyle(ln.category)
                const sourceBadge = getSourceTypeBadge(node.sourceType)

                return (
                  <g
                    key={ln.key}
                    transform={`translate(${ln.x}, ${ln.y})`}
                    onClick={(e) => {
                      e.stopPropagation()
                      setSelectedNodeKey(isSelected ? null : ln.key)
                      setSelectedEdgeKey(null)
                    }}
                    filter={ln.isTargetBatch ? 'url(#gold-glow)' : undefined}
                    style={{ cursor: 'pointer', transition: 'opacity 0.2s ease' }}
                    opacity={isDimmed ? 0.22 : 1}
                  >
                    {/* Selection Indicator Ring */}
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

                    {/* Node Background Card */}
                    <rect
                      x="0"
                      y="0"
                      width={ln.width}
                      height={ln.height}
                      rx="8"
                      fill={colors.bg}
                      stroke={isSelected ? '#60A5FA' : ln.isTargetBatch ? '#F59E0B' : colors.border}
                      strokeWidth={ln.isTargetBatch ? 2.2 : isSelected ? 2 : 1.2}
                    />

                    {/* Category / Role Badge Header */}
                    <rect
                      x="8"
                      y="7"
                      width={ln.width - 16}
                      height="18"
                      rx="4"
                      fill={colors.badgeBg}
                    />

                    {/* Category Label with Icon */}
                    <text
                      x="13"
                      y="19"
                      fontSize="9"
                      fontWeight="800"
                      fill={colors.badgeFg}
                      letterSpacing="0.4"
                    >
                      {`${colors.icon} ${ln.isTargetBatch ? 'TARGET BATCH' : colors.title.toUpperCase()}`}
                    </text>

                    {/* Source Type Indicator (for Evidence) */}
                    {sourceBadge && (
                      <text
                        x={ln.width - 13}
                        y="19"
                        fontSize="8.5"
                        fontWeight="800"
                        fill={sourceBadge.color}
                        textAnchor="end"
                        fontFamily="ui-monospace, monospace"
                      >
                        {sourceBadge.text}
                      </text>
                    )}

                    {/* Stable Identifier */}
                    <text
                      x="12"
                      y="42"
                      fontSize={ln.isTargetBatch ? '12.5' : '11'}
                      fontWeight="800"
                      fill={ln.isTargetBatch ? '#FFFBEB' : colors.text}
                      fontFamily="ui-monospace, monospace"
                    >
                      {node.stableId.length > 24 ? node.stableId.slice(0, 22) + '…' : node.stableId}
                    </text>

                    {/* Title or Status Subtext */}
                    <text
                      x="12"
                      y="60"
                      fontSize="9.5"
                      fill={MUTED}
                      fontFamily="sans-serif"
                    >
                      {(node.title && node.title.length > 26 ? node.title.slice(0, 24) + '…' : node.title) ||
                        (node.status ? `Status: ${node.status}` : colors.title)}
                    </text>
                  </g>
                )
              })}
            </g>
          </svg>
        </div>

        {/* 4. Details Drawer: Selected Node OR Selected Edge (Requirements 7 & 8) */}
        {selectedNode && (
          <div
            style={{
              width: 330,
              background: CARD_BG,
              border: `1px solid ${BORDER}`,
              borderRadius: 12,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
              flexShrink: 0,
              maxHeight: 680,
              overflowY: 'auto',
            }}
          >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <span
                  style={{
                    fontSize: 10,
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    padding: '3px 8px',
                    borderRadius: 999,
                    background: getCategoryStyle(nodeCategoryMap.get(getNodeKey(selectedNode.label, selectedNode.stableId)) || 'evidence').badgeBg,
                    color: getCategoryStyle(nodeCategoryMap.get(getNodeKey(selectedNode.label, selectedNode.stableId)) || 'evidence').badgeFg,
                  }}
                >
                  {nodeCategoryMap.get(getNodeKey(selectedNode.label, selectedNode.stableId)) === 'target_batch'
                    ? '🎯 TARGET BATCH'
                    : selectedNode.label}
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

            {/* Stable Identifier Box with Copy */}
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

            {/* Traceability Role Explanation */}
            <div
              style={{
                background: '#0B1120',
                border: `1px solid ${BORDER}`,
                borderRadius: 8,
                padding: '8px 10px',
                fontSize: 11,
                lineHeight: 1.4,
              }}
            >
              <span style={{ color: MUTED, fontSize: 10, fontWeight: 700, display: 'block', marginBottom: 2 }}>
                TRACEABILITY ROLE
              </span>
              <span style={{ color: TEXT_MAIN }}>
                {selectedNode.label === 'Incident' && `Root investigation incident #${selectedNode.stableId}`}
                {selectedNode.label === 'Batch' &&
                  (selectedNode.stableId === targetBatchStableId
                    ? `Primary investigation target batch (${selectedNode.stableId}) targeted by incident`
                    : `Co-produced or shared-equipment batch traversed in traceability depth`)}
                {selectedNode.label === 'Machine' && `Production equipment (${selectedNode.stableId}) where target batch was manufactured`}
                {selectedNode.label === 'Warehouse' && `Storage facility/zone (${selectedNode.stableId}) holding investigated inventory`}
                {selectedNode.label === 'Supplier' && `Supplier (${selectedNode.stableId}) of raw materials associated with batch`}
                {selectedNode.label === 'Product' && `Product catalog SKU (${selectedNode.stableId}) for affected batch`}
                {selectedNode.label === 'Customer' && `Customer account (${selectedNode.stableId}) linked to batch shipments`}
                {selectedNode.label === 'Evidence' &&
                  `Canonical evidence record from ${selectedNode.sourceType || 'source system'} supporting incident findings`}
              </span>
            </div>

            {/* Properties Grid */}
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

            {/* Related Evidence Records (Requirement 7) */}
            {relatedEvidenceForSelectedNode.length > 0 && (
              <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 10 }}>
                <span style={{ fontSize: 11, fontWeight: 700, color: '#86EFAC', textTransform: 'uppercase' }}>
                  Connected Evidence ({relatedEvidenceForSelectedNode.length})
                </span>
                <div style={{ display: 'flex', flexDirection: 'column', gap: 6, marginTop: 6, maxHeight: 180, overflowY: 'auto' }}>
                  {relatedEvidenceForSelectedNode.map((ev) => (
                    <div
                      key={ev.stableId}
                      onClick={() => setSelectedNodeKey(getNodeKey(ev.label, ev.stableId))}
                      style={{
                        background: '#0B1120',
                        border: `1px solid ${BORDER}`,
                        borderRadius: 6,
                        padding: '6px 8px',
                        fontSize: 11,
                        cursor: 'pointer',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 2,
                      }}
                    >
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                        <span style={{ fontFamily: 'monospace', fontWeight: 700, color: '#DCFCE7' }}>
                          {ev.stableId}
                        </span>
                        {ev.sourceType && (
                          <span style={{ fontSize: 9, color: '#38BDF8', fontWeight: 700 }}>
                            {ev.sourceType}
                          </span>
                        )}
                      </div>
                      {ev.title && <span style={{ fontSize: 10, color: MUTED }}>{ev.title}</span>}
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Traceability Relationships (Requirement 7) */}
            <div style={{ borderTop: `1px solid ${BORDER}`, paddingTop: 10, display: 'flex', flexDirection: 'column', gap: 10 }}>
              {/* Incoming */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: TEXT_SEC, textTransform: 'uppercase' }}>
                  Incoming Relationships ({selectedNodeRelationships.incoming.length})
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
                        <span style={{ color: getRelStyle(item.semanticLabel).stroke, fontWeight: 700, fontSize: 10 }}>
                          ─[{item.semanticLabel}]▶
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Outgoing */}
              <div>
                <span style={{ fontSize: 11, fontWeight: 700, color: TEXT_SEC, textTransform: 'uppercase' }}>
                  Outgoing Relationships ({selectedNodeRelationships.outgoing.length})
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
                        <span style={{ color: getRelStyle(item.semanticLabel).stroke, fontWeight: 700, fontSize: 10 }}>
                          ─[{item.semanticLabel}]▶
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
          </div>
        )}

        {/* Selected Edge Details Drawer (Requirement 8: Clicking an edge shows relationship type and connected nodes) */}
        {selectedEdge && !selectedNode && (
          <div
            style={{
              width: 330,
              background: CARD_BG,
              border: `1px solid ${BORDER}`,
              borderRadius: 12,
              padding: 16,
              display: 'flex',
              flexDirection: 'column',
              gap: 14,
              flexShrink: 0,
              maxHeight: 680,
              overflowY: 'auto',
            }}
          >
            {/* Header */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <span
                  style={{
                    fontSize: 10,
                    fontWeight: 800,
                    textTransform: 'uppercase',
                    padding: '3px 8px',
                    borderRadius: 999,
                    background: getRelStyle(selectedEdge.semanticType).badgeBg,
                    color: getRelStyle(selectedEdge.semanticType).badgeFg,
                  }}
                >
                  {selectedEdge.semanticType}
                </span>
                <h4 style={{ fontSize: 14, fontWeight: 800, color: TEXT_MAIN, margin: '6px 0 0 0' }}>
                  Relationship Details
                </h4>
              </div>
              <button
                onClick={() => setSelectedEdgeKey(null)}
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

            {/* Relationship Explanation Card */}
            <div
              style={{
                background: '#0B1120',
                border: `1px solid ${BORDER}`,
                borderRadius: 8,
                padding: '10px 12px',
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
              }}
            >
              <div style={{ fontSize: 10, color: MUTED, fontWeight: 700, textTransform: 'uppercase' }}>
                Traceability Link
              </div>
              <div style={{ fontSize: 12, color: TEXT_MAIN, lineHeight: 1.4 }}>
                {selectedEdge.semanticType === 'TARGETS' &&
                  `Incident #${investigationId} targets batch ${selectedEdge.toNode.stableId} as the primary anchor.`}
                {selectedEdge.semanticType === 'PRODUCED_ON' &&
                  `Batch ${selectedEdge.fromNode.stableId} was manufactured on machine ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'STORED_IN' &&
                  `Batch ${selectedEdge.fromNode.stableId} was stored in warehouse facility ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'SUPPLIED_BY' &&
                  `Raw materials for batch ${selectedEdge.fromNode.stableId} were provided by supplier ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'SHIPPED_TO' &&
                  `Finished goods from batch ${selectedEdge.fromNode.stableId} were distributed to ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'REFERENCES' &&
                  `Evidence record ${selectedEdge.fromNode.stableId} directly references entity ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'HAS_EVIDENCE' &&
                  `Incident #${investigationId} directly associates evidence ${selectedEdge.toNode.stableId}.`}
                {selectedEdge.semanticType === 'RELATED_TO' &&
                  `Domain entity ${selectedEdge.fromNode.stableId} has an established traceability relationship with ${selectedEdge.toNode.stableId}.`}
              </div>
            </div>

            {/* Connected Nodes Cards */}
            <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {/* Source Node */}
              <div>
                <span style={{ fontSize: 10, color: MUTED, fontWeight: 700, textTransform: 'uppercase' }}>
                  Source Node (From)
                </span>
                <div
                  onClick={() => setSelectedNodeKey(selectedEdge.fromKey)}
                  style={{
                    marginTop: 4,
                    background: '#0B1120',
                    border: `1px solid ${BORDER}`,
                    borderRadius: 8,
                    padding: '8px 10px',
                    cursor: 'pointer',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <span style={{ fontSize: 10, color: MUTED, display: 'block' }}>
                      {selectedEdge.fromNode.label}
                    </span>
                    <span style={{ fontSize: 12, fontFamily: 'monospace', fontWeight: 700, color: '#60A5FA' }}>
                      {selectedEdge.fromNode.stableId}
                    </span>
                  </div>
                  <span style={{ fontSize: 11, color: TEXT_SEC }}>Inspect ↗</span>
                </div>
              </div>

              {/* Direction Indicator */}
              <div style={{ textAlign: 'center', color: getRelStyle(selectedEdge.semanticType).stroke, fontWeight: 800, fontSize: 13 }}>
                ▼ {selectedEdge.semanticType}
              </div>

              {/* Target Node */}
              <div>
                <span style={{ fontSize: 10, color: MUTED, fontWeight: 700, textTransform: 'uppercase' }}>
                  Target Node (To)
                </span>
                <div
                  onClick={() => setSelectedNodeKey(selectedEdge.toKey)}
                  style={{
                    marginTop: 4,
                    background: '#0B1120',
                    border: `1px solid ${BORDER}`,
                    borderRadius: 8,
                    padding: '8px 10px',
                    cursor: 'pointer',
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <div>
                    <span style={{ fontSize: 10, color: MUTED, display: 'block' }}>
                      {selectedEdge.toNode.label}
                    </span>
                    <span style={{ fontSize: 12, fontFamily: 'monospace', fontWeight: 700, color: '#60A5FA' }}>
                      {selectedEdge.toNode.stableId}
                    </span>
                  </div>
                  <span style={{ fontSize: 11, color: TEXT_SEC }}>Inspect ↗</span>
                </div>
              </div>
            </div>

            {/* Raw Relationship Type */}
            <div style={{ fontSize: 11, color: MUTED, borderTop: `1px solid ${BORDER}`, paddingTop: 8 }}>
              Neo4j Edge Type: <strong style={{ color: TEXT_SEC }}>{selectedEdge.type}</strong>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
