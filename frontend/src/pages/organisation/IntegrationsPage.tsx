import { useEffect, useMemo, useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PERMISSIONS } from '../../utils/permissions'
import { getApiErrorMessage } from '../../api/client'
import { listIntegrations, getIntegration, createIntegration, updateIntegration, updateIntegrationStatus, testIntegration, deleteIntegration, syncIntegration, checkGraphReady, checkOrgGraphReady } from '../../services/integrationService'
import type { Integration } from '../../types/integration'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

const VALID_STATUSES = ['ACTIVE','INACTIVE','ENABLED','DISABLED','SUSPENDED','PENDING','DRAFT'] as const
const VALID_TYPES = ['API','WEBHOOK','DATABASE','FILE','SFTP','MANUAL','AUTOMATIC','CUSTOM'] as const

function formatDate(d?: string){ if(!d) return '-'; try{ return new Date(d).toLocaleString() } catch{ return d } }

function StatusBadge({status}:{status?:string|null}){
  const s=(status||'UNKNOWN').toUpperCase()
  const map: Record<string,{bg:string,fg:string}> = {
    ACTIVE:{bg:'#dcfce7',fg:'#166534'}, ENABLED:{bg:'#dcfce7',fg:'#166534'},
    INACTIVE:{bg:'#fee2e2',fg:'#991b1b'}, DISABLED:{bg:'#fee2e2',fg:'#7f1d1d'}, SUSPENDED:{bg:'#fef3c7',fg:'#92400e'},
    PENDING:{bg:'#e0e7ff',fg:'#3730a3'}, DRAFT:{bg:'#e2e8f0',fg:'#475569'}
  }
  const c=map[s]||{bg:'#e2e8f0',fg:'#475569'}
  return <span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:c.bg, color:c.fg, fontWeight:700}}>{s}</span>
}

export function IntegrationsPage(){
  const orgId = useAuthStore(s=>s.organisationId)
  const hasAny = useAuthStore(s=>s.hasAnyAuthority)
  const canRead = hasAny(...PERMISSIONS.INTEGRATION_READ)
  const canCreate = hasAny(...PERMISSIONS.INTEGRATION_CREATE)
  const canUpdate = hasAny(...PERMISSIONS.INTEGRATION_UPDATE)
  const canDelete = hasAny(...PERMISSIONS.INTEGRATION_DELETE)

  const [items, setItems] = useState<Integration[]>([])
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState<string|null>(null)
  const [query,setQuery]=useState('')
  const [typeFilter,setTypeFilter]=useState('ALL')
  const [statusFilter,setStatusFilter]=useState('ALL')

  // create/edit
  const [modalMode,setModalMode]=useState<'create'|'edit'|null>(null)
  const [editing,setEditing]=useState<Integration|null>(null)
  const [form,setForm]=useState({ name:'', type:'', status:'', configuration:'' })
  const [formError,setFormError]=useState<string|null>(null)
  const [submitting,setSubmitting]=useState(false)
  const [showConfig,setShowConfig]=useState(false)

  // view
  const [viewTarget,setViewTarget]=useState<Integration|null>(null)
  const [viewLoading,setViewLoading]=useState(false)
  const [viewError,setViewError]=useState<string|null>(null)
  const [viewShowSecret,setViewShowSecret]=useState(false)

  // delete
  const [deleteTarget,setDeleteTarget]=useState<Integration|null>(null)
  const [deleting,setDeleting]=useState(false)
  const [deleteError,setDeleteError]=useState<string|null>(null)

  // status patch inline
  const [statusUpdatingId,setStatusUpdatingId]=useState<number|null>(null)

  // test
  const [testingId,setTestingId]=useState<number|null>(null)
  const [testResult,setTestResult]=useState<{id:number, ok:boolean, msg:string}|null>(null)

  // sync
  const [syncingId,setSyncingId]=useState<number|null>(null)
  const [syncResult,setSyncResult]=useState<{id:number, status:string, syncId?:number, message?:string}|null>(null)
  const [syncError,setSyncError]=useState<string|null>(null)

  // graph readiness
  const [graphReadyMap,setGraphReadyMap]=useState<Record<number,{ready:boolean, evidenceCount:number, message:string}>>({})
  const [orgGraphReady,setOrgGraphReady]=useState<{ready:boolean, evidenceCount:number, message:string}|null>(null)

  const load = async()=>{
    setLoading(true); setError(null); setTestResult(null)
    try{
      const data = await listIntegrations()
      setItems(data)
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      if(st===403) setError('Forbidden – missing INTEGRATION_READ permission.')
      else setError(getApiErrorMessage(err,'Failed to load integrations'))
    }finally{ setLoading(false) }
  }
  useEffect(()=>{ if(canRead) load() },[orgId])

  const filtered = useMemo(()=>{
    const q=query.trim().toLowerCase()
    return items.filter(i=>{
      const mq=!q || i.name.toLowerCase().includes(q) || (i.type||'').toLowerCase().includes(q) || (i.status||'').toLowerCase().includes(q)
      const mt=typeFilter==='ALL' || (i.type||'').toUpperCase()===typeFilter
      const ms=statusFilter==='ALL' || (i.status||'').toUpperCase()===statusFilter
      return mq && mt && ms
    })
  },[items,query,typeFilter,statusFilter])

  const openCreate=()=>{
    if(!canCreate) return
    setForm({name:'',type:'',status:'',configuration:''}); setFormError(null); setEditing(null); setModalMode('create'); setShowConfig(false)
  }
  const openEdit=(it:Integration)=>{
    if(!canUpdate) return
    setEditing(it)
    setForm({name:it.name, type:it.type||'', status:it.status||'', configuration:it.configuration||''})
    setFormError(null); setModalMode('edit'); setShowConfig(false)
  }
  const openView=async(it:Integration)=>{
    setViewTarget(it); setViewError(null); setViewLoading(true); setViewShowSecret(false)
    try{
      const fresh= await getIntegration(it.id)
      setViewTarget(fresh)
    }catch(err:unknown){
      setViewError(getApiErrorMessage(err,'Failed to load details'))
    }finally{ setViewLoading(false) }
  }

  const handleSubmit=async(e:React.FormEvent)=>{
    e.preventDefault(); setFormError(null)
    if(!form.name.trim()){ setFormError('Name is required'); return }
    if(form.type.trim() && form.type.trim().length===0){ setFormError('Type cannot be blank'); return }
    // status will be uppercased backend validates against VALID_STATUSES – surface 400
    setSubmitting(true)
    try{
      if(modalMode==='create'){
        await createIntegration({ name:form.name.trim(), type: form.type.trim()||null, status: form.status.trim()||null, configuration: form.configuration||null })
      } else if(modalMode==='edit' && editing){
        await updateIntegration(editing.id, { name:form.name.trim(), type: form.type.trim()||null, status: form.status.trim()||null, configuration: form.configuration||null })
      }
      setModalMode(null); setEditing(null); await load()
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      let msg=getApiErrorMessage(err,'Save failed')
      if(st===409) msg=msg.includes('already exists')?msg:'Integration name already exists in this organisation (409)'
      if(st===403) msg='Forbidden – missing permission (403)'
      if(st===400 && msg.toLowerCase().includes('status')) msg=msg // keep Allowed: [...]
      setFormError(msg)
    }finally{ setSubmitting(false) }
  }

  const handleStatusChange=async(it:Integration, newStatus:string)=>{
    if(!canUpdate) return
    if(!newStatus) return
    if(newStatus.toUpperCase()===(it.status||'')) return
    setStatusUpdatingId(it.id)
    try{
      const updated= await updateIntegrationStatus(it.id, newStatus)
      setItems(prev=> prev.map(x=> x.id===it.id? updated: x))
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      let msg=getApiErrorMessage(err,'Status update failed')
      if(st===403) msg='Forbidden (403)'
      alert(msg)
    }finally{ setStatusUpdatingId(null) }
  }

  const handleTest=async(it:Integration)=>{
    if(!canRead) return
    setTestingId(it.id); setTestResult(null)
    try{
      const msg= await testIntegration(it.id)
      setTestResult({id:it.id, ok:true, msg})
    }catch(err:unknown){
      setTestResult({id:it.id, ok:false, msg:getApiErrorMessage(err,'Test failed')})
    }finally{ setTestingId(null) }
  }

  const handleSync=async(it:Integration)=>{
    if(syncingId) return
    setSyncingId(it.id); setSyncError(null); setSyncResult(null)
    try{
      const res = await syncIntegration(it.id)
      setSyncResult({id:it.id, status: res.status, syncId: res.syncId, message: `Sync ${res.status}`})
      // Refresh graph readiness after successful sync
      try{
        const gr = await checkGraphReady(it.id)
        setGraphReadyMap(prev=> ({...prev, [it.id]: {ready: gr.graphReady, evidenceCount: gr.evidenceCount, message: gr.message||'' }}))
      }catch{}
      try{
        const orgGr = await checkOrgGraphReady()
        setOrgGraphReady({ready: orgGr.graphReady, evidenceCount: orgGr.evidenceCount, message: orgGr.message||''})
      }catch{}
      await load()
    }catch(err:unknown){
      setSyncError(getApiErrorMessage(err,'Sync failed'))
      setSyncResult({id:it.id, status:'FAILED', message: getApiErrorMessage(err,'Sync failed')})
    }finally{ setSyncingId(null) }
  }

  const refreshGraphReady=async()=>{
    try{
      const orgGr = await checkOrgGraphReady()
      setOrgGraphReady({ready: orgGr.graphReady, evidenceCount: orgGr.evidenceCount, message: orgGr.message||''})
      // Also refresh per-integration
      for(const it of items){
        try{
          const gr = await checkGraphReady(it.id)
          setGraphReadyMap(prev=> ({...prev, [it.id]: {ready: gr.graphReady, evidenceCount: gr.evidenceCount, message: gr.message||'' }}))
        }catch{}
      }
    }catch{}
  }

  useEffect(()=>{ if(canRead && items.length>0) refreshGraphReady() },[items.length])

  const handleDelete=async()=>{
    if(!deleteTarget || !canDelete) return
    setDeleting(true); setDeleteError(null)
    try{
      await deleteIntegration(deleteTarget.id)
      setItems(prev=> prev.filter(x=> x.id!==deleteTarget.id))
      setDeleteTarget(null)
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      let msg=getApiErrorMessage(err,'Delete failed')
      if(st===403) msg='Forbidden – missing INTEGRATION_DELETE (403)'
      if(st===404) msg='Integration not found (404)'
      if(st===409) msg=msg // Cannot delete integration with existing files (n)
      setDeleteError(msg)
    }finally{ setDeleting(false) }
  }

  if(!canRead){
    return <div style={{padding:24, background:'#451a1a', border:'1px solid #7f1d1d', borderRadius:12, color:'#fecaca'}}><h3 style={{margin:0}}>Forbidden</h3><p style={{fontSize:14,marginTop:8}}>You do not have INTEGRATION_READ permission.</p></div>
  }

  return (
    <div style={{display:'flex', flexDirection:'column', gap:16}}>
      <div style={{display:'flex', justifyContent:'space-between', alignItems:'center', flexWrap:'wrap', gap:12}}>
        <div>
          <h2 style={{margin:0, color:TEXT_MAIN, fontSize:20, fontWeight:800}}>Integrations</h2>
          <div style={{fontSize:12, color:TEXT_SEC, marginTop:4}}>{items.length} integration(s) · Org #{orgId} · Types: API/WEBHOOK/DATABASE/FILE/SFTP …</div>
        </div>
        <div style={{display:'flex', gap:8}}>
          <button onClick={load} style={{padding:'8px 12px', background:CARD_BG, border:`1px solid ${BORDER}`, color:TEXT_MAIN, borderRadius:8, cursor:'pointer'}}>Refresh</button>
          {canCreate && <button onClick={openCreate} style={{padding:'8px 14px', background:'#6366F1', color:'#fff', border:0, borderRadius:8, cursor:'pointer', fontWeight:700}}>+ Create integration</button>}
        </div>
      </div>

      <div style={{display:'flex', gap:12, flexWrap:'wrap'}}>
        <input placeholder="Search name, type, status…" value={query} onChange={e=>setQuery(e.target.value)} style={{flex:1, minWidth:220, padding:'9px 12px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}} />
        <select value={typeFilter} onChange={e=>setTypeFilter(e.target.value)} style={{padding:'8px 10px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}}>
          <option value="ALL">All types</option>
          {VALID_TYPES.map(t=> <option key={t} value={t}>{t}</option>)}
        </select>
        <select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)} style={{padding:'8px 10px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}}>
          <option value="ALL">All statuses</option>
          {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
        </select>
        <span style={{fontSize:12, color:MUTED, alignSelf:'center'}}>{filtered.length} filtered</span>
      </div>

      {testResult && <div style={{padding:'10px 12px', background: testResult.ok?'#dcfce7':'#fee2e2', border:`1px solid ${testResult.ok?'#86efac':'#fecaca'}`, borderRadius:8, color: testResult.ok?'#166534':'#7f1d1d', fontSize:13}}>{testResult.ok?'✓ ': '✗ '}{testResult.msg}</div>}
      {syncResult && <div style={{padding:'10px 12px', background: syncResult.status==='SUCCESS'?'#dcfce7': syncResult.status==='FAILED'?'#fee2e2':'#fef3c7', border:`1px solid ${syncResult.status==='SUCCESS'?'#86efac': syncResult.status==='FAILED'?'#fecaca':'#fde68a'}`, borderRadius:8, color: syncResult.status==='SUCCESS'?'#166534': syncResult.status==='FAILED'?'#7f1d1d':'#92400e', fontSize:13}}>{syncResult.status==='SUCCESS'?'✓ ': syncResult.status==='FAILED'?'✗ ':'↻ '}Sync {syncResult.status} {syncResult.syncId?`(sync #${syncResult.syncId})`:''} {syncResult.message?`- ${syncResult.message}`:''}</div>}
      {syncError && <ErrorAlert message={syncError} />}
      {orgGraphReady && <div style={{padding:'10px 12px', background: orgGraphReady.ready?'#dcfce7':'#fef3c7', border:`1px solid ${orgGraphReady.ready?'#86efac':'#fde68a'}`, borderRadius:8, color: orgGraphReady.ready?'#166534':'#92400e', fontSize:13, display:'flex', justifyContent:'space-between', alignItems:'center'}}><span>{orgGraphReady.ready?'✓ Graph Ready':'○ Graph Not Ready'} — Evidence: {orgGraphReady.evidenceCount} {orgGraphReady.message?`· ${orgGraphReady.message}`:''}</span><button onClick={refreshGraphReady} style={{padding:'4px 8px', background:'#fff', border:'1px solid #CBD5E1', borderRadius:6, cursor:'pointer', fontSize:11}}>Refresh</button></div>}

      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading ? <LoadingSpinner label="Loading integrations…" /> : filtered.length===0 ? (
        <div style={{padding:32, textAlign:'center', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:12, color:TEXT_SEC}}>
          <div style={{fontWeight:700, color:TEXT_MAIN}}>{items.length===0?'No integrations yet':'No matches'}</div>
          <div style={{fontSize:13, marginTop:6}}>{items.length===0?'Create your first integration to connect external systems.':'Try a different search or filter.'}</div>
          <div style={{fontSize:11, color:MUTED, marginTop:8}}>Valid statuses: {VALID_STATUSES.join(', ')} · Valid types include {VALID_TYPES.join(', ')} (custom allowed).</div>
          {canCreate && items.length===0 && <button onClick={openCreate} style={{marginTop:12, padding:'8px 14px', background:'#6366F1', color:'#fff', border:0, borderRadius:8, cursor:'pointer'}}>Create integration</button>}
        </div>
      ) : (
        <div style={{background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:12, overflow:'hidden'}}>
          <div style={{overflowX:'auto'}}>
            <table style={{width:'100%', borderCollapse:'collapse', fontSize:13, minWidth:760}}>
              <thead>
                <tr style={{textAlign:'left', background:'#0F172A'}}>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC, fontWeight:600}}>Name</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Type</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Status</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Sync</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Graph</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Updated</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(it=>(
                  <tr key={it.id} style={{borderBottom:`1px solid ${BORDER}`}}>
                    <td style={{padding:'10px 12px', color:TEXT_MAIN, fontWeight:700}}>{it.name}</td>
                    <td style={{padding:'10px 12px', color:TEXT_SEC}}>{it.type ? <span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:'#0B1120', border:`1px solid ${BORDER}`}}>{it.type}</span> : <span style={{color:MUTED}}>-</span>}</td>
                    <td style={{padding:'10px 12px'}}><StatusBadge status={it.status} /></td>
                    <td style={{padding:'10px 12px', fontSize:12}}>
                      {syncingId===it.id ? <span style={{color:'#38bdf8', fontWeight:700}}>Syncing…</span> : syncResult?.id===it.id ? <span style={{padding:'2px 6px', borderRadius:999, background: syncResult.status==='SUCCESS'?'#dcfce7': syncResult.status==='FAILED'?'#fee2e2':'#fef3c7', color: syncResult.status==='SUCCESS'?'#166534': syncResult.status==='FAILED'?'#7f1d1d':'#92400e', fontSize:11, fontWeight:700}}>{syncResult.status}</span> : <span style={{color:MUTED}}>-</span>}
                    </td>
                    <td style={{padding:'10px 12px', fontSize:12}}>
                      {graphReadyMap[it.id] ? <span style={{padding:'2px 6px', borderRadius:999, background: graphReadyMap[it.id].ready?'#dcfce7':'#fef3c7', color: graphReadyMap[it.id].ready?'#166534':'#92400e', fontSize:11, fontWeight:700}}>{graphReadyMap[it.id].ready?'Ready':'Not Ready'} · {graphReadyMap[it.id].evidenceCount}</span> : <span style={{color:MUTED}}>-</span>}
                    </td>
                    <td style={{padding:'10px 12px', color:TEXT_SEC, fontSize:12}}>{formatDate(it.updatedAt)}</td>
                    <td style={{padding:'10px 12px'}}>
                      <div style={{display:'flex', gap:6, flexWrap:'wrap', alignItems:'center'}}>
                        <button onClick={()=>openView(it)} style={{padding:'4px 8px', background:'transparent', border:`1px solid ${BORDER}`, color:TEXT_SEC, borderRadius:6, cursor:'pointer', fontSize:12}}>View</button>
                        {canUpdate && <>
                          <button onClick={()=>openEdit(it)} style={{padding:'4px 8px', background:'transparent', border:`1px solid ${BORDER}`, color:TEXT_MAIN, borderRadius:6, cursor:'pointer', fontSize:12}}>Edit</button>
                          <select value={(it.status||'').toUpperCase()} onChange={e=>handleStatusChange(it, e.target.value)} disabled={statusUpdatingId===it.id} style={{padding:'4px 6px', background:'#0B1120', border:`1px solid ${BORDER}`, color:TEXT_SEC, borderRadius:6, fontSize:12}}>
                            <option value="">Set status…</option>
                            {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
                          </select>
                        </>}
                        <button onClick={()=>handleTest(it)} disabled={testingId===it.id} style={{padding:'4px 8px', background: canRead ? '#1e293b' : 'transparent', border:`1px solid ${BORDER}`, color: canRead? '#38bdf8': TEXT_SEC, borderRadius:6, cursor: testingId===it.id? 'not-allowed':'pointer', fontSize:12}}>{testingId===it.id?'Testing…':'Test'}</button>
                        <button onClick={()=>handleSync(it)} disabled={syncingId===it.id || syncingId!==null} style={{padding:'4px 8px', background: syncingId===it.id?'#475569':'#10B981', color:'#fff', border:0, borderRadius:6, cursor: syncingId? 'not-allowed':'pointer', fontSize:12, fontWeight:700}}>{syncingId===it.id?'Syncing…':'Sync'}</button>
                        {canDelete && <button onClick={()=>{setDeleteTarget(it); setDeleteError(null)}} style={{padding:'4px 8px', background:'transparent', border:'1px solid #7f1d1d', color:'#F87171', borderRadius:6, cursor:'pointer', fontSize:12}}>Delete</button>}
                        {!canUpdate && !canDelete && <span style={{color:MUTED, fontSize:11}}>Limited</span>}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Create / Edit */}
      {modalMode && (
        <div onClick={()=>!submitting && setModalMode(null)} style={{position:'fixed', inset:0, background:'rgba(0,0,0,0.6)', display:'flex', alignItems:'center', justifyContent:'center', zIndex:50, padding:16}}>
          <div onClick={e=>e.stopPropagation()} style={{width:480, maxWidth:'100%', background:'#fff', borderRadius:12, overflow:'hidden', boxShadow:'0 20px 60px rgba(0,0,0,0.3)', maxHeight:'90vh', overflowY:'auto'}}>
            <div style={{padding:16, borderBottom:'1px solid #e5e7eb', display:'flex', justifyContent:'space-between', alignItems:'center', position:'sticky', top:0, background:'#fff'}}>
              <h3 style={{margin:0, fontSize:16, fontWeight:800, color:'#0F172A'}}>{modalMode==='create' ? 'Create integration' : `Edit · ${editing?.name}`}</h3>
              <button onClick={()=>setModalMode(null)} style={{background:'transparent', border:0, cursor:'pointer', fontSize:18, color:'#64748B'}}>×</button>
            </div>
            <form onSubmit={handleSubmit} style={{padding:16, display:'flex', flexDirection:'column', gap:12}}>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Name *</span>
                <input value={form.name} onChange={e=>setForm({...form, name:e.target.value})} placeholder="e.g. SAP Connector" required style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
              </label>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Type</span>
                <input list="integration-types" value={form.type} onChange={e=>setForm({...form, type:e.target.value})} placeholder="API, WEBHOOK, DATABASE, FILE, SFTP…" style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
                <datalist id="integration-types">{VALID_TYPES.map(t=> <option key={t} value={t} />)}</datalist>
                <span style={{fontSize:11, color:MUTED}}>Valid types: {VALID_TYPES.join(', ')} – custom allowed, blank not allowed if set.</span>
              </label>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Status</span>
                <select value={form.status} onChange={e=>setForm({...form, status:e.target.value})} style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}}>
                  <option value="">-- none --</option>
                  {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
                </select>
                <span style={{fontSize:11, color:MUTED}}>Allowed: {VALID_STATUSES.join(', ')}</span>
              </label>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <div style={{display:'flex', justifyContent:'space-between', alignItems:'center'}}>
                  <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Configuration</span>
                  <button type="button" onClick={()=>setShowConfig(v=>!v)} style={{fontSize:11, color:'#6366F1', background:'transparent', border:0, cursor:'pointer'}}>{showConfig?'Hide':'Show'} secrets</button>
                </div>
                <textarea value={form.configuration} onChange={e=>setForm({...form, configuration:e.target.value})} placeholder='{"url":"https://...","token":"***"}' rows={4} style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8, fontFamily:'ui-monospace, monospace', fontSize:12, filter: showConfig? 'none':'blur(0px)'}} />
                <span style={{fontSize:11, color:MUTED}}>Stored as TEXT; never logged. Test requires type + configuration non-empty.</span>
              </label>
              <div style={{fontSize:11, color:MUTED, background:'#f8fafc', padding:'8px 10px', borderRadius:8, border:'1px solid #e2e8f0'}}>Organisation forced from session – never sent. Duplicate name in org → 409.</div>
              {formError && <div role="alert" style={{background:'#FEF2F2', border:'1px solid #FECACA', color:'#DC2626', padding:'8px 10px', borderRadius:8, fontSize:13}}>{formError}</div>}
              <div style={{display:'flex', justifyContent:'flex-end', gap:8, marginTop:4}}>
                <button type="button" onClick={()=>setModalMode(null)} disabled={submitting} style={{padding:'8px 14px', background:'#fff', border:'1px solid #CBD5E1', borderRadius:8, cursor:'pointer'}}>Cancel</button>
                <button type="submit" disabled={submitting} style={{padding:'8px 16px', background: submitting?'#475569':'#0F172A', color:'#fff', border:0, borderRadius:8, cursor: submitting?'not-allowed':'pointer', fontWeight:700}}>{submitting?'Saving…': modalMode==='create'?'Create':'Save'}</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* View */}
      {viewTarget && (
        <div onClick={()=>setViewTarget(null)} style={{position:'fixed', inset:0, background:'rgba(0,0,0,0.6)', display:'flex', alignItems:'center', justifyContent:'center', zIndex:50, padding:16}}>
          <div onClick={e=>e.stopPropagation()} style={{width:520, maxWidth:'100%', background:'#fff', borderRadius:12, overflow:'hidden', boxShadow:'0 20px 60px rgba(0,0,0,0.3)', maxHeight:'85vh', display:'flex', flexDirection:'column'}}>
            <div style={{padding:16, borderBottom:'1px solid #e5e7eb', display:'flex', justifyContent:'space-between', alignItems:'center'}}>
              <h3 style={{margin:0, fontSize:16, fontWeight:800, color:'#0F172A'}}>Integration · {viewTarget.name}</h3>
              <button onClick={()=>setViewTarget(null)} style={{background:'transparent', border:0, cursor:'pointer', fontSize:18, color:'#64748B'}}>×</button>
            </div>
            <div style={{padding:16, display:'flex', flexDirection:'column', gap:12, overflowY:'auto'}}>
              {viewLoading ? <LoadingSpinner label="Loading details…" /> : viewError ? <ErrorAlert message={viewError} onRetry={()=> viewTarget && openView(viewTarget)} /> : (
                <>
                  <div style={{display:'grid', gridTemplateColumns:'120px 1fr', gap:8, fontSize:13}}>
                    <span style={{color:MUTED, fontWeight:600}}>ID</span><span style={{fontFamily:'ui-monospace, monospace'}}>{viewTarget.id}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Name</span><span style={{fontWeight:700}}>{viewTarget.name}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Type</span><span>{viewTarget.type || '-'}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Status</span><span><StatusBadge status={viewTarget.status} /></span>
                    <span style={{color:MUTED, fontWeight:600}}>Created</span><span>{formatDate(viewTarget.createdAt)}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Updated</span><span>{formatDate(viewTarget.updatedAt)}</span>
                  </div>
                  <div style={{border:'1px solid #e2e8f0', borderRadius:8, overflow:'hidden'}}>
                    <div style={{padding:'8px 12px', background:'#0F172A', color:'#fff', fontSize:11, fontWeight:700, display:'flex', justifyContent:'space-between', alignItems:'center'}}>
                      <span>Configuration (sensitive)</span>
                      <button onClick={()=>setViewShowSecret(v=>!v)} style={{fontSize:11, background:'#1e293b', color:'#38bdf8', border:'1px solid #334155', borderRadius:6, padding:'2px 8px', cursor:'pointer'}}>{viewShowSecret?'Hide':'Show'}</button>
                    </div>
                    <pre style={{margin:0, padding:12, background:'#f8fafc', fontSize:12, whiteSpace:'pre-wrap', wordBreak:'break-all', maxHeight:200, overflowY:'auto', filter: viewShowSecret? 'none':'blur(4px)', userSelect: viewShowSecret? 'text':'none'}}>{viewTarget.configuration ? viewTarget.configuration : '(empty)'}</pre>
                    {!viewShowSecret && <div style={{padding:'6px 12px', fontSize:11, color:MUTED, background:'#fff', borderTop:'1px solid #e2e8f0'}}>Hidden for security – click Show to reveal. Never copied to logs.</div>}
                  </div>
                  <div style={{display:'flex', gap:8, flexWrap:'wrap'}}>
                    {canUpdate && <button onClick={()=>{setViewTarget(null); if(viewTarget) openEdit(viewTarget)}} style={{padding:'8px 12px', background:'#0F172A', color:'#fff', border:0, borderRadius:8, cursor:'pointer', fontSize:13}}>Edit</button>}
                    <button onClick={()=>handleTest(viewTarget)} disabled={testingId===viewTarget.id} style={{padding:'8px 12px', background: testingId===viewTarget.id? '#475569':'#1e293b', color:'#fff', border:0, borderRadius:8, cursor:'pointer', fontSize:13}}>{testingId===viewTarget.id?'Testing…':'Test connection'}</button>
                    <button onClick={()=>setViewTarget(null)} style={{padding:'8px 12px', background:'#fff', border:'1px solid #CBD5E1', borderRadius:8, cursor:'pointer', fontSize:13}}>Close</button>
                  </div>
                  {testResult && testResult.id===viewTarget.id && <div style={{padding:'8px 10px', background: testResult.ok?'#dcfce7':'#fee2e2', border:`1px solid ${testResult.ok?'#86efac':'#fecaca'}`, borderRadius:8, color: testResult.ok?'#166534':'#7f1d1d', fontSize:13}}>{testResult.ok?'✓ ':'✗ '}{testResult.msg}</div>}
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Delete confirm */}
      {deleteTarget && (
        <div onClick={()=>!deleting && setDeleteTarget(null)} style={{position:'fixed', inset:0, background:'rgba(0,0,0,0.6)', display:'flex', alignItems:'center', justifyContent:'center', zIndex:50, padding:16}}>
          <div onClick={e=>e.stopPropagation()} style={{width:400, maxWidth:'100%', background:'#fff', borderRadius:12, padding:16}}>
            <h3 style={{margin:0, color:'#7f1d1d'}}>Delete integration?</h3>
            <p style={{fontSize:13, color:'#475569', marginTop:8}}>Delete <strong style={{color:'#0F172A'}}>{deleteTarget.name}</strong> ({deleteTarget.type||'-'})? Backend returns <code>409</code> if files still linked.</p>
            {deleteError && <div style={{marginTop:8, background:'#FEF2F2', border:'1px solid #FECACA', color:'#DC2626', padding:'8px 10px', borderRadius:8, fontSize:13}}>{deleteError}</div>}
            <div style={{display:'flex', justifyContent:'flex-end', gap:8, marginTop:16}}>
              <button onClick={()=>setDeleteTarget(null)} disabled={deleting} style={{padding:'8px 14px', background:'#fff', border:'1px solid #CBD5E1', borderRadius:8, cursor:'pointer'}}>Cancel</button>
              <button onClick={handleDelete} disabled={deleting} style={{padding:'8px 16px', background: deleting?'#7f1d1d':'#DC2626', color:'#fff', border:0, borderRadius:8, cursor: deleting?'not-allowed':'pointer', fontWeight:700}}>{deleting?'Deleting…':'Delete'}</button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
