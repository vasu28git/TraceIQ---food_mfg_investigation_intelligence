import { useEffect, useMemo, useState } from 'react'
import { useAuthStore } from '../../store/authStore'
import { PERMISSIONS } from '../../utils/permissions'
import { getApiErrorMessage } from '../../api/client'
import { listFilesByOrg, getFile, createFile, updateFile, updateFileStatus, deleteFile, uploadFile } from '../../services/fileService'
import { listIntegrations } from '../../services/integrationService'
import type { FileRecord } from '../../types/file'
import type { Integration } from '../../types/integration'
import { LoadingSpinner } from '../../components/ui/LoadingSpinner'
import { ErrorAlert } from '../../components/ui/ErrorAlert'

const CARD_BG = '#111827'
const BORDER = '#1E293B'
const TEXT_MAIN = '#F8FAFC'
const TEXT_SEC = '#94A3B8'
const MUTED = '#475569'

const VALID_STATUSES = ['UPLOADED','PROCESSING','READY','FAILED','ARCHIVED','PENDING'] as const
const SOURCE_TYPES = ['MANUAL_UPLOAD','INTEGRATION','API_UPLOAD','SFTP'] as const

function formatDate(d?: string|null){ if(!d) return '-'; try{ return new Date(d).toLocaleString()}catch{return d} }
function formatSize(n?: number|null){ if(n==null) return '-'; if(n<1024) return `${n} B`; if(n<1024*1024) return `${(n/1024).toFixed(1)} KB`; return `${(n/1024/1024).toFixed(2)} MB` }

function StatusBadge({status}:{status?:string|null}){
  const s=(status||'UNKNOWN').toUpperCase()
  const map: Record<string,{bg:string,fg:string}> = {
    UPLOADED:{bg:'#e0e7ff',fg:'#3730a3'}, PROCESSING:{bg:'#fef3c7',fg:'#92400e'},
    READY:{bg:'#dcfce7',fg:'#166534'}, FAILED:{bg:'#fee2e2',fg:'#991b1b'},
    ARCHIVED:{bg:'#e2e8f0',fg:'#475569'}, PENDING:{bg:'#e0e7ff',fg:'#3730a3'},
  }
  const c=map[s]||{bg:'#e2e8f0',fg:'#475569'}
  return <span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:c.bg, color:c.fg, fontWeight:700}}>{s}</span>
}

export function FilesPage(){
  const orgId = useAuthStore(s=>s.organisationId)
  const hasAny = useAuthStore(s=>s.hasAnyAuthority)
  const canRead = hasAny(...PERMISSIONS.FILE_READ)
  const canCreate = hasAny(...PERMISSIONS.FILE_CREATE)
  const canUpdate = hasAny(...PERMISSIONS.FILE_UPDATE)
  const canDelete = hasAny(...PERMISSIONS.FILE_DELETE)

  const [items,setItems]=useState<FileRecord[]>([])
  const [integrations,setIntegrations]=useState<Integration[]>([])
  const [loading,setLoading]=useState(true)
  const [error,setError]=useState<string|null>(null)
  const [query,setQuery]=useState('')
  const [sourceFilter,setSourceFilter]=useState('ALL')
  const [statusFilter,setStatusFilter]=useState('ALL')

  // create/edit
  const [modalMode,setModalMode]=useState<'create'|'edit'|null>(null)
  const [editing,setEditing]=useState<FileRecord|null>(null)
  const [form,setForm]=useState({ originalName:'', sourceType:'MANUAL_UPLOAD', storageKey:'', contentType:'', size:'', status:'', integrationId:'' })
  const [formError,setFormError]=useState<string|null>(null)
  const [submitting,setSubmitting]=useState(false)

  // view
  const [viewTarget,setViewTarget]=useState<FileRecord|null>(null)
  const [viewLoading,setViewLoading]=useState(false)
  const [viewError,setViewError]=useState<string|null>(null)

  // delete
  const [deleteTarget,setDeleteTarget]=useState<FileRecord|null>(null)
  const [deleting,setDeleting]=useState(false)
  const [deleteError,setDeleteError]=useState<string|null>(null)

  // inline status
  const [statusUpdatingId,setStatusUpdatingId]=useState<number|null>(null)

  // upload
  const [selectedFile,setSelectedFile]=useState<File|null>(null)
  const [uploading,setUploading]=useState(false)
  const [uploadError,setUploadError]=useState<string|null>(null)
  const [uploadSuccess,setUploadSuccess]=useState<string|null>(null)

  const load = async()=>{
    if(!orgId) return
    setLoading(true); setError(null)
    try{
      const [files, ints] = await Promise.all([
        listFilesByOrg(orgId),
        listIntegrations().catch(()=> [] as Integration[])
      ])
      setItems(files)
      setIntegrations(ints)
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      if(st===403) setError('Forbidden – missing FILE_READ permission.')
      else setError(getApiErrorMessage(err,'Failed to load files'))
    }finally{ setLoading(false) }
  }
  useEffect(()=>{ if(canRead) load() },[orgId])

  const filtered = useMemo(()=>{
    const q=query.trim().toLowerCase()
    return items.filter(f=>{
      const mq=!q || f.originalName.toLowerCase().includes(q) || (f.sourceType||'').toLowerCase().includes(q) || (f.status||'').toLowerCase().includes(q) || (f.integration ? String(f.integration.id).includes(q) : false)
      const ms=sourceFilter==='ALL' || (f.sourceType||'').toUpperCase()===sourceFilter
      const mst=statusFilter==='ALL' || (f.status||'').toUpperCase()===statusFilter
      return mq && ms && mst
    })
  },[items,query,sourceFilter,statusFilter])

  const openCreate=()=>{
    if(!canCreate) return
    setForm({ originalName:'', sourceType:'MANUAL_UPLOAD', storageKey:'', contentType:'', size:'', status:'', integrationId:'' })
    setFormError(null); setEditing(null); setModalMode('create')
  }
  const openEdit=(f:FileRecord)=>{
    if(!canUpdate) return
    setEditing(f)
    setForm({
      originalName: f.originalName,
      sourceType: f.sourceType,
      storageKey: f.storageKey||'',
      contentType: f.contentType||'',
      size: f.size!=null? String(f.size):'',
      status: f.status||'',
      integrationId: f.integration?.id ? String(f.integration.id) : ''
    })
    setFormError(null); setModalMode('edit')
  }
  const openView=async(f:FileRecord)=>{
    setViewTarget(f); setViewError(null); setViewLoading(true)
    try{
      const fresh= await getFile(f.id)
      setViewTarget(fresh)
    }catch(err:unknown){ setViewError(getApiErrorMessage(err,'Failed to load details'))}
    finally{ setViewLoading(false)}
  }

  const handleSubmit=async(e:React.FormEvent)=>{
    e.preventDefault(); setFormError(null)
    if(!form.originalName.trim()){ setFormError('Filename (originalName) is required'); return }
    if(!form.sourceType.trim()){ setFormError('Source type is required'); return }
    const sizeNum = form.size.trim() ? Number(form.size.trim()) : null
    if(form.size.trim() && (isNaN(sizeNum!) || sizeNum! <0)){ setFormError('Size must be a positive number'); return }
    // status validation handled by backend; surface 400
    setSubmitting(true)
    try{
      const payloadBase:any = {
        originalName: form.originalName.trim(),
        sourceType: form.sourceType.trim(),
        storageKey: form.storageKey.trim() || null,
        contentType: form.contentType.trim() || null,
        size: sizeNum,
        status: form.status.trim() ? form.status.trim().toUpperCase() : null,
      }
      if(form.integrationId) payloadBase.integration = { id: Number(form.integrationId) }
      else if(modalMode==='edit') payloadBase.integration = null // allow clearing? backend will ignore null vs not set? we send null to keep clear option
      if(modalMode==='create'){
        await createFile(payloadBase)
      } else if(modalMode==='edit' && editing){
        // only send mutable fields per FileService.java:147 – integration change only if org-scoped
        const upd:any={}
        if(form.originalName.trim() !== editing.originalName) upd.originalName = form.originalName.trim()
        else upd.originalName = form.originalName.trim() // send always for simplicity
        upd.sourceType = form.sourceType.trim() // service will ignore? actually updateFile only updates originalName etc, not sourceType – but we include status etc
        upd.storageKey = form.storageKey.trim() || null
        upd.contentType = form.contentType.trim() || null
        upd.size = sizeNum
        upd.status = form.status.trim() ? form.status.trim().toUpperCase() : null
        if(form.integrationId) upd.integration = { id: Number(form.integrationId) }
        // backend does not update sourceType via PUT – but we don't send organisationId ever
        await updateFile(editing.id, upd)
      }
      setModalMode(null); setEditing(null); await load()
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      let msg=getApiErrorMessage(err,'Save failed')
      if(st===409) msg='Conflict – file already exists or duplicate (409)'
      if(st===403) msg='Forbidden – missing permission or cross-org integration (403)'
      // keep allowed status message
      setFormError(msg)
    }finally{ setSubmitting(false) }
  }

  const handleStatusChange=async(f:FileRecord, newStatus:string)=>{
    if(!canUpdate) return
    if(!newStatus) return
    if(newStatus.toUpperCase()===(f.status||'')) return
    setStatusUpdatingId(f.id)
    try{
      const updated = await updateFileStatus(f.id, newStatus)
      setItems(prev=> prev.map(x=> x.id===f.id? updated : x))
    }catch(err:unknown){
      alert(getApiErrorMessage(err,'Status update failed'))
    }finally{ setStatusUpdatingId(null)}
  }

  const handleDelete=async()=>{
    if(!deleteTarget || !canDelete) return
    setDeleting(true); setDeleteError(null)
    try{
      await deleteFile(deleteTarget.id)
      setItems(prev=> prev.filter(x=> x.id!==deleteTarget.id))
      setDeleteTarget(null)
    }catch(err:unknown){
      const st=(err as {response?:{status?:number}})?.response?.status
      let msg=getApiErrorMessage(err,'Delete failed')
      if(st===403) msg='Forbidden – missing FILE_DELETE (403)'
      if(st===404) msg='File not found (404)'
      if(st===409) msg=msg // Cannot delete file that is the last reference for integration ID: ...
      setDeleteError(msg)
    }finally{ setDeleting(false) }
  }

  const handleFileSelect=(e:React.ChangeEvent<HTMLInputElement>)=>{
    const file=e.target.files?.[0] || null
    setUploadError(null); setUploadSuccess(null)
    if(!file){ setSelectedFile(null); return }
    // Basic client validation
    if(file.size > 10*1024*1024){ setUploadError('File size exceeds 10MB limit'); setSelectedFile(null); return }
    if(file.size===0){ setUploadError('File must not be empty'); setSelectedFile(null); return }
    setSelectedFile(file)
  }

  const handleUpload=async()=>{
    if(!selectedFile || !canCreate) return
    setUploading(true); setUploadError(null); setUploadSuccess(null)
    try{
      const uploaded = await uploadFile(selectedFile)
      setUploadSuccess(`Uploaded ${uploaded.originalName} (${formatSize(uploaded.size)})`)
      setSelectedFile(null)
      // Reset file input
      const el=document.getElementById('taceiq-file-input') as HTMLInputElement|null
      if(el) el.value=''
      await load()
    }catch(err:unknown){
      setUploadError(getApiErrorMessage(err,'Upload failed'))
    }finally{ setUploading(false) }
  }

  if(!canRead){
    return <div style={{padding:24, background:'#451a1a', border:'1px solid #7f1d1d', borderRadius:12, color:'#fecaca'}}><h3 style={{margin:0}}>Forbidden</h3><p style={{fontSize:14,marginTop:8}}>You do not have FILE_READ permission.</p></div>
  }

  return (
    <div style={{display:'flex', flexDirection:'column', gap:16}}>
      <div style={{display:'flex', justifyContent:'space-between', alignItems:'center', flexWrap:'wrap', gap:12}}>
        <div>
          <h2 style={{margin:0, color:TEXT_MAIN, fontSize:20, fontWeight:800}}>Files</h2>
          <div style={{fontSize:12, color:TEXT_SEC, marginTop:4}}>{items.length} file(s) · Org #{orgId} · Source: MANUAL_UPLOAD/INTEGRATION …</div>
        </div>
        <div style={{display:'flex', gap:8, alignItems:'center'}}>
          <button onClick={load} style={{padding:'8px 12px', background:CARD_BG, border:`1px solid ${BORDER}`, color:TEXT_MAIN, borderRadius:8, cursor:'pointer'}}>Refresh</button>
          {canCreate && <button onClick={openCreate} style={{padding:'8px 14px', background:'#334155', color:'#fff', border:`1px solid ${BORDER}`, borderRadius:8, cursor:'pointer', fontWeight:700}}>+ Create</button>}
          {canCreate && (
            <label style={{padding:'8px 14px', background:'#6366F1', color:'#fff', border:0, borderRadius:8, cursor:'pointer', fontWeight:700, display:'inline-flex', alignItems:'center', gap:6}}>
              <input id="taceiq-file-input" type="file" onChange={handleFileSelect} style={{display:'none'}} />
              Upload File
            </label>
          )}
        </div>
      </div>

      <div style={{display:'flex', gap:12, flexWrap:'wrap'}}>
        <input placeholder="Search filename, source, status, integration…" value={query} onChange={e=>setQuery(e.target.value)} style={{flex:1, minWidth:220, padding:'9px 12px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}} />
        <select value={sourceFilter} onChange={e=>setSourceFilter(e.target.value)} style={{padding:'8px 10px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}}>
          <option value="ALL">All sources</option>
          {SOURCE_TYPES.map(s=> <option key={s} value={s}>{s}</option>)}
          <option value="OTHER">Other</option>
        </select>
        <select value={statusFilter} onChange={e=>setStatusFilter(e.target.value)} style={{padding:'8px 10px', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:8, color:TEXT_MAIN}}>
          <option value="ALL">All statuses</option>
          {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
        </select>
        <span style={{fontSize:12, color:MUTED, alignSelf:'center'}}>{filtered.length} filtered</span>
      </div>

      {canCreate && selectedFile && (
        <div style={{padding:12, background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:12, display:'flex', flexDirection:'column', gap:8}}>
          <div style={{fontSize:13, color:TEXT_MAIN, fontWeight:700}}>Selected file</div>
          <div style={{display:'flex', gap:12, flexWrap:'wrap', alignItems:'center', fontSize:13, color:TEXT_SEC}}>
            <span style={{fontWeight:600, color:TEXT_MAIN}}>{selectedFile.name}</span>
            <span>{formatSize(selectedFile.size)}</span>
            <span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:'#0B1120', border:`1px solid ${BORDER}`}}>{selectedFile.type || 'unknown'}</span>
          </div>
          <div style={{display:'flex', gap:8}}>
            <button onClick={handleUpload} disabled={uploading} style={{padding:'8px 14px', background: uploading?'#475569':'#10B981', color:'#fff', border:0, borderRadius:8, cursor: uploading?'not-allowed':'pointer', fontWeight:700}}>{uploading?'Uploading…':'Confirm upload'}</button>
            <button onClick={()=>{setSelectedFile(null); setUploadError(null); const el=document.getElementById('taceiq-file-input') as HTMLInputElement|null; if(el) el.value=''}} disabled={uploading} style={{padding:'8px 14px', background:'transparent', border:`1px solid ${BORDER}`, color:TEXT_SEC, borderRadius:8, cursor:'pointer'}}>Cancel</button>
          </div>
          {uploadError && <div role="alert" style={{background:'#FEF2F2', border:'1px solid #FECACA', color:'#DC2626', padding:'8px 10px', borderRadius:8, fontSize:13}}>{uploadError}</div>}
          {uploadSuccess && <div role="status" style={{background:'#DCFCE7', border:'1px solid #BBF7D0', color:'#166534', padding:'8px 10px', borderRadius:8, fontSize:13}}>{uploadSuccess}</div>}
        </div>
      )}
      {canCreate && uploadError && !selectedFile && <ErrorAlert message={uploadError} />}
      {canCreate && uploadSuccess && !selectedFile && <div style={{background:'#DCFCE7', border:'1px solid #BBF7D0', color:'#166534', padding:'8px 10px', borderRadius:8, fontSize:13}}>{uploadSuccess}</div>}

      {error && <ErrorAlert message={error} onRetry={load} />}
      {loading ? <LoadingSpinner label="Loading files…" /> : filtered.length===0 ? (
        <div style={{padding:32, textAlign:'center', background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:12, color:TEXT_SEC}}>
          <div style={{fontWeight:700, color:TEXT_MAIN}}>{items.length===0?'No files yet':'No matches'}</div>
          <div style={{fontSize:13, marginTop:6}}>{items.length===0?'Upload your first file or create a record linked to an integration.':'Try a different search or filter.'}</div>
          <div style={{fontSize:11, color:MUTED, marginTop:8}}>Valid statuses: {VALID_STATUSES.join(', ')} · Do not log file contents.</div>
          {canCreate && items.length===0 && <button onClick={openCreate} style={{marginTop:12, padding:'8px 14px', background:'#6366F1', color:'#fff', border:0, borderRadius:8, cursor:'pointer'}}>Create file</button>}
        </div>
      ) : (
        <div style={{background:CARD_BG, border:`1px solid ${BORDER}`, borderRadius:12, overflow:'hidden'}}>
          <div style={{overflowX:'auto'}}>
            <table style={{width:'100%', borderCollapse:'collapse', fontSize:13, minWidth:900}}>
              <thead>
                <tr style={{textAlign:'left', background:'#0F172A'}}>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC, fontWeight:600}}>Filename</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Source</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Status</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Integration</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Size</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Updated</th>
                  <th style={{padding:'10px 12px', borderBottom:`1px solid ${BORDER}`, color:TEXT_SEC}}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map(f=>(
                  <tr key={f.id} style={{borderBottom:`1px solid ${BORDER}`}}>
                    <td style={{padding:'10px 12px', color:TEXT_MAIN, fontWeight:600, maxWidth:220, whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis'}} title={f.originalName}>{f.originalName}</td>
                    <td style={{padding:'10px 12px'}}><span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:'#0B1120', border:`1px solid ${BORDER}`, color:TEXT_SEC}}>{f.sourceType}</span></td>
                    <td style={{padding:'10px 12px'}}><StatusBadge status={f.status} /></td>
                    <td style={{padding:'10px 12px', color:TEXT_SEC, fontSize:12}}>{f.integration?.id ? `#${f.integration.id}${f.integration.name?` · ${f.integration.name}`:''}` : <span style={{color:MUTED}}>-</span>}</td>
                    <td style={{padding:'10px 12px', color:TEXT_SEC, fontSize:12}}>{formatSize(f.size)}</td>
                    <td style={{padding:'10px 12px', color:TEXT_SEC, fontSize:12}}>{formatDate(f.updatedAt)}</td>
                    <td style={{padding:'10px 12px'}}>
                      <div style={{display:'flex', gap:6, flexWrap:'wrap', alignItems:'center'}}>
                        <button onClick={()=>openView(f)} style={{padding:'4px 8px', background:'transparent', border:`1px solid ${BORDER}`, color:TEXT_SEC, borderRadius:6, cursor:'pointer', fontSize:12}}>View</button>
                        {canUpdate && <>
                          <button onClick={()=>openEdit(f)} style={{padding:'4px 8px', background:'transparent', border:`1px solid ${BORDER}`, color:TEXT_MAIN, borderRadius:6, cursor:'pointer', fontSize:12}}>Edit</button>
                          <select value={(f.status||'').toUpperCase()} onChange={e=>handleStatusChange(f, e.target.value)} disabled={statusUpdatingId===f.id} style={{padding:'4px 6px', background:'#0B1120', border:`1px solid ${BORDER}`, color:TEXT_SEC, borderRadius:6, fontSize:12}}>
                            <option value="">Set status…</option>
                            {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
                          </select>
                        </>}
                        {canDelete && <button onClick={()=>{setDeleteTarget(f); setDeleteError(null)}} style={{padding:'4px 8px', background:'transparent', border:'1px solid #7f1d1d', color:'#F87171', borderRadius:6, cursor:'pointer', fontSize:12}}>Delete</button>}
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
              <h3 style={{margin:0, fontSize:16, fontWeight:800, color:'#0F172A'}}>{modalMode==='create' ? 'Create file' : `Edit · ${editing?.originalName}`}</h3>
              <button onClick={()=>setModalMode(null)} style={{background:'transparent', border:0, cursor:'pointer', fontSize:18, color:'#64748B'}}>×</button>
            </div>
            <form onSubmit={handleSubmit} style={{padding:16, display:'flex', flexDirection:'column', gap:12}}>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Filename (originalName) *</span>
                <input value={form.originalName} onChange={e=>setForm({...form, originalName:e.target.value})} placeholder="report.pdf" required style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
              </label>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Source type *</span>
                <input list="file-source-types" value={form.sourceType} onChange={e=>setForm({...form, sourceType:e.target.value})} placeholder="MANUAL_UPLOAD or INTEGRATION" required style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
                <datalist id="file-source-types">{SOURCE_TYPES.map(s=> <option key={s} value={s} />)}</datalist>
              </label>
              <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:12}}>
                <label style={{display:'flex', flexDirection:'column', gap:4}}>
                  <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Storage key</span>
                  <input value={form.storageKey} onChange={e=>setForm({...form, storageKey:e.target.value})} placeholder="s3://bucket/key" style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
                </label>
                <label style={{display:'flex', flexDirection:'column', gap:4}}>
                  <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Content type</span>
                  <input value={form.contentType} onChange={e=>setForm({...form, contentType:e.target.value})} placeholder="application/pdf" style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
                </label>
              </div>
              <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:12}}>
                <label style={{display:'flex', flexDirection:'column', gap:4}}>
                  <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Size (bytes)</span>
                  <input type="number" min={0} value={form.size} onChange={e=>setForm({...form, size:e.target.value})} placeholder="12345" style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}} />
                </label>
                <label style={{display:'flex', flexDirection:'column', gap:4}}>
                  <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Status</span>
                  <select value={form.status} onChange={e=>setForm({...form, status:e.target.value})} style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}}>
                    <option value="">-- none --</option>
                    {VALID_STATUSES.map(s=> <option key={s} value={s}>{s}</option>)}
                  </select>
                </label>
              </div>
              <label style={{display:'flex', flexDirection:'column', gap:4}}>
                <span style={{fontSize:12, fontWeight:600, color:'#334155'}}>Integration (optional, org-scoped)</span>
                <select value={form.integrationId} onChange={e=>setForm({...form, integrationId:e.target.value})} style={{padding:'9px 12px', border:'1px solid #CBD5E1', borderRadius:8}}>
                  <option value="">-- No integration --</option>
                  {integrations.map(i=> <option key={i.id} value={String(i.id)}>{i.name} · {i.type||'-'} · #{i.id}</option>)}
                </select>
                <span style={{fontSize:11, color:MUTED}}>Only integrations in your organisation are listed. Cross-org selection → 403.</span>
              </label>
              <div style={{fontSize:11, color:MUTED, background:'#f8fafc', padding:'8px 10px', borderRadius:8, border:'1px solid #e2e8f0'}}>Organisation forced from session – never sent. Backend validates required fields; invalid status → 400 Allowed: {VALID_STATUSES.join(', ')}.</div>
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
              <h3 style={{margin:0, fontSize:16, fontWeight:800, color:'#0F172A', whiteSpace:'nowrap', overflow:'hidden', textOverflow:'ellipsis'}} title={viewTarget.originalName}>{viewTarget.originalName}</h3>
              <button onClick={()=>setViewTarget(null)} style={{background:'transparent', border:0, cursor:'pointer', fontSize:18, color:'#64748B'}}>×</button>
            </div>
            <div style={{padding:16, display:'flex', flexDirection:'column', gap:12, overflowY:'auto'}}>
              {viewLoading ? <LoadingSpinner label="Loading details…" /> : viewError ? <ErrorAlert message={viewError} onRetry={()=> viewTarget && openView(viewTarget)} /> : (
                <>
                  <div style={{display:'grid', gridTemplateColumns:'140px 1fr', gap:8, fontSize:13}}>
                    <span style={{color:MUTED, fontWeight:600}}>ID</span><span style={{fontFamily:'ui-monospace, monospace'}}>{viewTarget.id}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Filename</span><span style={{fontWeight:700}}>{viewTarget.originalName}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Source</span><span><span style={{fontSize:11, padding:'2px 8px', borderRadius:999, background:'#f1f5f9', border:'1px solid #e2e8f0'}}>{viewTarget.sourceType}</span></span>
                    <span style={{color:MUTED, fontWeight:600}}>Status</span><span><StatusBadge status={viewTarget.status} /></span>
                    <span style={{color:MUTED, fontWeight:600}}>Integration</span><span>{viewTarget.integration ? `#${viewTarget.integration.id}` : '-'}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Storage key</span><span style={{fontFamily:'ui-monospace, monospace', fontSize:12, wordBreak:'break-all'}}>{viewTarget.storageKey || '-'}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Content type</span><span>{viewTarget.contentType || '-'}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Size</span><span>{formatSize(viewTarget.size)}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Received</span><span>{formatDate(viewTarget.receivedAt)}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Created</span><span>{formatDate(viewTarget.createdAt)}</span>
                    <span style={{color:MUTED, fontWeight:600}}>Updated</span><span>{formatDate(viewTarget.updatedAt)}</span>
                  </div>
                  <div style={{fontSize:11, color:MUTED, background:'#f8fafc', padding:'8px 10px', borderRadius:8, border:'1px solid #e2e8f0'}}>Backend returns exactly stored metadata – no file contents. Organisation scoping enforced server-side. Do not log sensitive data.</div>
                  <div style={{display:'flex', gap:8, flexWrap:'wrap'}}>
                    {canUpdate && <button onClick={()=>{setViewTarget(null); if(viewTarget) openEdit(viewTarget)}} style={{padding:'8px 12px', background:'#0F172A', color:'#fff', border:0, borderRadius:8, cursor:'pointer', fontSize:13}}>Edit</button>}
                    <button onClick={()=>setViewTarget(null)} style={{padding:'8px 12px', background:'#fff', border:'1px solid #CBD5E1', borderRadius:8, cursor:'pointer', fontSize:13}}>Close</button>
                  </div>
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
            <h3 style={{margin:0, color:'#7f1d1d'}}>Delete file?</h3>
            <p style={{fontSize:13, color:'#475569', marginTop:8}}>Delete <strong style={{color:'#0F172A'}}>{deleteTarget.originalName}</strong> ({deleteTarget.sourceType})? Backend returns <code>409</code> if it is the last file for its integration.</p>
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
