import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import { ArrowRight, Camera, Check, CheckCheck, ChevronRight, CircleCheck, ClipboardCheck, Clock3, Download, FileText, Glasses, History, Image as ImageIcon, LayoutDashboard, ListChecks, MapPin, Pause, Play, Plus, Radio, RotateCcw, ScanLine, Send, Settings2, ShieldCheck, Trash2, TriangleAlert, UserRound, X } from 'lucide-react';
import { useInspection } from './InspectionContext';
import type { AppState, Command, Draft, EvidencePhoto, StepDefinition, Task, Template, InspectionSchedule } from '../domain/types';
import { defaultAssetId, defaultAssigneeId } from '../data/seed';
import { stats } from '../services/report';
import { toAndroidTaskDispatchJson } from '../services/androidTask';
import { Modal } from '../features/Modal';
import { Photo } from '../features/Photo';
import { CaptureDialog } from '../features/CaptureDialog';
import { TemplateEditor } from '../features/TemplateEditor';
import { Report } from '../features/Report';
import { ReadingFields, ReadingSummary } from '../features/ReadingFields';
import { allowedResults, isInactive, resultLabel } from '../domain/readings';

const commandId = () => crypto.randomUUID();
const statusLabels = { assigned: '已下发（演示）', running: '巡检中', paused: '已暂停', completed: '已完成' };
export const timeLabel = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false }) : '尚未开始';

type View = 'workspace' | 'templates' | 'history';
export default function App() {
  const { state, execute, pending } = useInspection();
  const [view, setView] = useState<View>('workspace');
  const [templateId, setTemplateId] = useState(state.template.id);
  const selectedTemplate = state.templates.find(item => item.id === templateId) ?? state.template;
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [resetOpen, setResetOpen] = useState(false);
  const [capture, setCapture] = useState<{ task: Task; step: StepDefinition } | null>(null);
  const [photo, setPhoto] = useState<EvidencePhoto | null>(null);
  const [inspect, setInspect] = useState<{ task: Task; stepId: string } | null>(null);
  const [reportId, setReportId] = useState<string | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const actionLock = useRef(false);
  const retry = useRef<(() => Promise<unknown>) | null>(null);
  const unfinished = state.tasks.find(t => t.status !== 'completed');
  const task = state.tasks.find(t => t.id === selectedId) ?? unfinished ?? state.tasks.at(-1);
  const reportTask = state.tasks.find(t => t.id === reportId);
  async function run(action: () => Promise<unknown>, success?: string) {
    if (actionLock.current) return;
    actionLock.current = true; setBusy(true); setError(''); setMessage(''); retry.current = action;
    try { await action(); retry.current = null; if (success) setMessage(success); }
    catch (cause) { setError(cause instanceof Error ? cause.message : '保存失败，请重试。'); }
    finally { actionLock.current = false; setBusy(false); }
  }
  function navigate(next: View) { setView(next); setCapture(null); setError(''); setMessage(''); }
  const apply = (command: Command) => execute(command);
  function exportAndroidTask(taskToExport: Task) {
    const blob = new Blob([toAndroidTaskDispatchJson(taskToExport)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `android-task-dispatch-${taskToExport.id}.json`;
    anchor.click();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
  const openReport = (id: string) => { setReportId(id); setCapture(null); window.scrollTo({ top: 0 }); };
  return <>
    {!reportTask && <header className="topbar no-print">
      <a className="brand" href="#workspace" onClick={event => { event.preventDefault(); navigate('workspace'); }}><span className="brand-mark"><ScanLine size={24} /></span><span>智能穿戴巡检工作台<small>标准作业 · 现场留证</small></span></a>
      <nav aria-label="主导航">
        <button className={view === 'workspace' ? 'nav-item active' : 'nav-item'} onClick={() => navigate('workspace')}><LayoutDashboard size={17} />演示工作台</button>
        <button className={view === 'templates' ? 'nav-item active' : 'nav-item'} onClick={() => navigate('templates')}><ListChecks size={17} />流程模板</button>
        <button className={view === 'history' ? 'nav-item active' : 'nav-item'} onClick={() => navigate('history')}><History size={17} />巡检记录{state.tasks.length > 0 && <span className="nav-count">{state.tasks.length}</span>}</button>
      </nav>
      <div className="header-actions"><span className="local-badge"><span />本地演示</span><button className="text-button" onClick={() => { setCapture(null); setResetOpen(true); }}><RotateCcw size={15} />重新演示</button></div>
    </header>}
    {!reportTask && <main className="page no-print">
      <div className="page-heading"><div><div className="eyebrow">WEARABLE INSPECTION</div><h1>{view === 'workspace' ? '巡检演示工作台' : view === 'templates' ? '流程模板' : '巡检记录'}</h1><p>{view === 'workspace' ? '从任务下发到报告归档，逐项执行，全程留痕。' : view === 'templates' ? '将巡检规程编排为清晰步骤，让现场执行有据可依。' : '查看本地巡检任务、现场照片和人员确认记录。'}</p></div><div className="flow-caption"><span><Send size={15} />下发任务</span><ChevronRight size={15} /><span><Glasses size={17} />现场检查</span><ChevronRight size={15} /><span><FileText size={16} />报告归档</span></div></div>
      {error && <div className="notice error" role="alert"><TriangleAlert size={18} /><span>{error}</span><button disabled={busy} onClick={() => retry.current && void run(retry.current)}>重试</button><button aria-label="关闭错误提示" onClick={() => setError('')}><X size={16} /></button></div>}
      {message && <div className="notice success" role="status"><CircleCheck size={17} /><span>{message}</span><button aria-label="关闭通知" onClick={() => setMessage('')}><X size={16} /></button></div>}
      {view === 'workspace' && <div className="workspace">
        <TaskManager state={state} task={task} selectedTemplate={selectedTemplate} onTemplate={setTemplateId} busy={busy || pending > 0} onEdit={() => { setTemplateId(unfinished?.templateSnapshot.id ?? selectedTemplate.id); navigate('templates'); }} onInspect={stepId => task && setInspect({ task, stepId })} onSelect={id => { setSelectedId(id); setError(''); }} onExport={exportAndroidTask} onDispatch={(assetId, assigneeId, templateId, schedule) => void run(async () => { const next = await apply({ type: 'dispatch', commandId: commandId(), assetId, assigneeId, templateId, schedule }); setSelectedId(next.tasks.find(t => t.status !== 'completed')?.id ?? null); }, '任务已下发至眼镜界面模拟。')} />
        <GlassesPanel key={task?.id ?? 'empty'} task={task} state={state} busy={busy || pending > 0} execute={execute} onRun={run} onCapture={(task, step) => setCapture({ task, step })} onPhoto={setPhoto} onReport={openReport} />
        <ExecutionLog state={state} task={task} template={selectedTemplate} onPhoto={setPhoto} onInspect={stepId => task && setInspect({ task, stepId })} onReport={openReport} />
      </div>}
      {view === 'templates' && <><div className="catalog-bar card"><label className="field">选择流程模板<TemplateSelect label="选择流程模板" templates={state.templates} value={selectedTemplate.id} onChange={setTemplateId}/></label><p>电气 7 套 · 暖通 5 套 · 135 个检查项；另保留机柜演示。</p><SourceNote template={selectedTemplate}/></div><TemplateEditor key={selectedTemplate.id} template={selectedTemplate} busy={busy || pending > 0} onSave={async template => { await execute({ type: 'saveTemplate', commandId: commandId(), template }); setMessage('模板已本地保存；新任务将使用新版本，已下发任务保持原快照。'); }} /></>}
      {view === 'history' && <HistoryPage state={state} onView={id => { setSelectedId(id); navigate('workspace'); }} onReport={openReport} onNew={() => navigate('workspace')} />}
      <footer className="page-footer"><span><ShieldCheck size={14} />任务、照片与操作记录保存在当前浏览器</span><span>{pending > 0 ? '正在本地保存…' : '本地存储就绪'}<i />电脑模拟执行 · 无真实眼镜连接</span></footer>
    </main>}
    {resetOpen && <Modal title="重新演示" onClose={() => !busy && setResetOpen(false)}><div className="reset-explanation"><span className="warning-icon"><RotateCcw size={27} /></span><h3>清空记录，开始新的演示</h3><p>将清除本应用的全部任务、照片和操作记录，恢复默认模板与资产。需要保留的结果请先导出报告。</p></div><div className="dialog-actions"><button disabled={busy} onClick={() => setResetOpen(false)}>取消</button><button className="danger" disabled={busy} onClick={() => void run(async () => { await execute({ type: 'reset', commandId: commandId() }); setResetOpen(false); setCapture(null); setPhoto(null); setInspect(null); setReportId(null); setSelectedId(null); setView('workspace'); }, '演示数据已重置。')}>确认重置</button></div></Modal>}
    {capture && <CaptureDialog task={capture.task} step={capture.step} onClose={() => setCapture(null)} onSave={async evidence => { await execute({ type: 'addPhoto', commandId: commandId(), taskId: capture.task.id, expectedStepId: capture.step.id, photo: evidence }); }} />}
    {photo && <Modal title={photo.name} onClose={() => setPhoto(null)} wide><Photo photo={photo} /><p className="muted">{photo.isDemo ? '演示素材' : photo.source === 'camera' ? '电脑摄像头' : '本地上传'} · {photo.width} × {photo.height} · {timeLabel(photo.addedAt)}</p></Modal>}
    {inspect && <StepDetail state={state} task={inspect.task} stepId={inspect.stepId} onClose={() => setInspect(null)} onPhoto={setPhoto} />}
    {reportTask && <main className="report-page"><Report state={state} task={reportTask} onClose={() => setReportId(null)} /></main>}
  </>;
}

function TaskManager({ state, task, selectedTemplate, onTemplate, busy, onEdit, onDispatch, onInspect, onSelect, onExport }: { state: AppState; task?: Task; selectedTemplate: Template; onTemplate: (id: string) => void; busy: boolean; onEdit: () => void; onDispatch: (assetId: string, assigneeId: string, templateId: string, schedule?: InspectionSchedule) => void; onInspect: (stepId: string) => void; onSelect: (id: string) => void; onExport: (task: Task) => void }) {
  const [assetId, setAssetId] = useState(defaultAssetId);
  const [assigneeId, setAssigneeId] = useState(defaultAssigneeId);
  const active = state.tasks.find(t => t.status !== 'completed');
  const [scheduleDate, setScheduleDate] = useState(() => { const now = new Date(); return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`; });
  const [slot, setSlot] = useState('');
  const [shift, setShift] = useState<'白班' | '夜班'>('白班');
  const [engineer, setEngineer] = useState('');
  const template = active ? task?.templateSnapshot ?? active.templateSnapshot : selectedTemplate;
  const dispatchTemplate = active?.templateSnapshot ?? selectedTemplate;
  const availableAssets = state.assets.filter(asset => dispatchTemplate.assetIds ? dispatchTemplate.assetIds.includes(asset.id) : asset.type === 'cabinet');
  const shownAssetId = active?.assetSnapshot.id ?? (availableAssets.some(item => item.id === assetId) ? assetId : availableAssets[0]?.id ?? '');
  const selectedSlot = dispatchTemplate.timeSlots?.includes(slot) ? slot : dispatchTemplate.timeSlots?.[0] ?? '';
  const schedule = active?.schedule ?? { date: scheduleDate, time: selectedSlot, shift, engineer: dispatchTemplate.source?.page === 1 ? engineer : undefined };

  const shownAssigneeId = active?.assigneeSnapshot.id ?? assigneeId;
  return <section className="card manager" aria-labelledby="manager-title">
    <div className="section-title"><span className="section-icon"><ClipboardCheck size={18} /></span><h2 id="manager-title">任务管理</h2><span className="section-kicker">管理端</span></div>
    <div className={dispatchTemplate.source ? "manager-form catalog-manager" : "manager-form"}><label className="field">巡检模板<TemplateSelect templates={state.templates} value={dispatchTemplate.id} disabled={!!active} onChange={id => { onTemplate(id); setSlot(''); }}/></label><button className="inline-link edit-link" onClick={onEdit}><Settings2 size={13} />编辑流程模板<ChevronRight size={13} /></button>
      <label className="field">目标设备<select aria-label="目标设备" value={shownAssetId} disabled={!!active} onChange={event => setAssetId(event.target.value)}>{availableAssets.map(asset => <option key={asset.id} value={asset.id}>{asset.name}</option>)}</select></label>
      <p className="field-hint"><MapPin size={13} />{state.assets.find(asset => asset.id === shownAssetId)?.location}</p>
      <label className="field">执行人<select aria-label="执行人" value={shownAssigneeId} disabled={!!active} onChange={event => setAssigneeId(event.target.value)}>{state.assignees.map(person => <option key={person.id} value={person.id}>{person.name}</option>)}</select></label>
      {dispatchTemplate.source && <div className="schedule-fields"><SourceNote template={dispatchTemplate}/><div className="schedule-grid"><label className="field">巡检日期<input type="date" value={schedule.date} disabled={!!active} onChange={event => setScheduleDate(event.target.value)}/></label><label className="field">巡检时段<select aria-label="巡检时段" value={schedule.time} disabled={!!active} onChange={event => setSlot(event.target.value)}>{dispatchTemplate.timeSlots?.map(time => <option key={time}>{time}</option>)}</select></label><label className="field">班次<select aria-label="班次" value={schedule.shift} disabled={!!active} onChange={event => setShift(event.target.value as '白班' | '夜班')}><option>白班</option><option>夜班</option></select></label>{dispatchTemplate.source.page === 1 && <label className="field">值班工程师<input placeholder="选填" value={schedule.engineer ?? ''} disabled={!!active} onChange={event => setEngineer(event.target.value)}/></label>}</div><details className="schedule-help"><summary>日期与设备码说明</summary><p>日期为实际巡检日期，03:00 也按所选日期记录；设备码为演示编号。</p></details></div>}
      <button className="primary dispatch-button" disabled={!!active || busy} onClick={() => onDispatch(shownAssetId, assigneeId, dispatchTemplate.id, dispatchTemplate.source ? schedule : undefined)}><Send size={16} />{state.tasks.length > 0 && !active ? '下发新任务' : '下发任务'}</button>
      {task && <button className="button secondary dispatch-button" disabled={busy} onClick={() => onExport(task)}><Download size={16} />导出 Android task.dispatch</button>}
      <p className="small muted dispatch-hint">{active ? '已有未完成任务，完成后可下发新任务。' : '下发后由执行人在眼镜模拟区开始巡检'}</p>
    </div>
    <div className="step-section"><div className="subheading"><h3>执行步骤</h3><span>{template.steps.length} 步 · V{template.version}{task ? ' 快照' : ''}</span></div><ol className="step-list">{template.steps.map((step, index) => {
      const record = task?.stepRecords.find(record => record.stepId === step.id);
      const completed = record?.status === 'completed'; const current = record?.status === 'current';
      return <li key={step.id} className={completed ? 'done' : current ? 'current' : ''}><button disabled={!completed} onClick={() => onInspect(step.id)} aria-label={`${step.title} ${completed ? '已完成，查看记录' : current ? '当前步骤' : '待执行'}`}><span className="step-number">{completed ? <Check size={14} /> : String(index + 1).padStart(2, '0')}</span><span className="step-copy"><strong>{step.title}</strong><small>{completed ? '已完成 · 查看记录' : current ? '当前步骤' : step.type === 'photo_check' ? `${step.requirements.minPhotos} 张照片${step.requirements.resultRequired ? ' · 填写结果' : ''}` : step.type === 'asset_check' ? '核对设备信息' : '核对并归档'}</small></span>{current && <span className="current-dot" />}</button></li>;
    })}</ol></div>
    {state.tasks.length > 1 && <label className="field switch-task">切换查看任务<select value={task?.id ?? ''} onChange={event => onSelect(event.target.value)}>{state.tasks.map(item => <option key={item.id} value={item.id}>{item.assetSnapshot.name} · {statusLabels[item.status]} · {timeLabel(item.createdAt)}</option>)}</select></label>}
  </section>;
}

interface GlassesProps { task?: Task; state: AppState; busy: boolean; execute: (command: Command) => Promise<AppState>; onRun: (action: () => Promise<unknown>, success?: string) => Promise<void>; onCapture: (task: Task, step: StepDefinition) => void; onPhoto: (photo: EvidencePhoto) => void; onReport: (id: string) => void }
function GlassesPanel({ task, state, busy, execute, onRun, onCapture, onPhoto, onReport }: GlassesProps) {
  const record = task?.stepRecords.find(record => record.stepId === task.currentStepId);
  const step = task?.templateSnapshot.steps.find(step => step.id === task.currentStepId);
  const [draft, setDraft] = useState<Draft>(record?.draft ?? {});
  const [validation, setValidation] = useState('');
  const [draftError, setDraftError] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const submitLock = useRef(false);
  const executionRef = useRef<HTMLDivElement>(null);
  const validationRef = useRef<HTMLDivElement>(null);
  const draftRef = useRef(draft);
  const stepId = step?.id;
  useEffect(() => { executionRef.current?.scrollTo({ top: 0 }); }, [stepId, task?.status]);
  useEffect(() => { if (validation || draftError) validationRef.current?.scrollIntoView({ block: 'nearest' }); }, [validation, draftError]);
  useEffect(() => { const next = task?.stepRecords.find(record => record.stepId === stepId)?.draft ?? {}; setDraft(next); draftRef.current = next; setValidation(''); setDraftError(''); }, [task?.id, stepId]); // eslint-disable-line react-hooks/exhaustive-deps
  function saveDraft(next: Draft) {
    if (!task || !step) return;
    setDraft(next); draftRef.current = next; setDraftError(''); setValidation('');
    void execute({ type: 'draft', taskId: task.id, expectedStepId: step.id, commandId: commandId(), draft: next }).catch((cause: Error) => setDraftError(cause.message));
  }
  async function complete() {
    if (!task || !step || submitLock.current) return;
    submitLock.current = true; setSubmitting(true); setValidation('');
    try { await execute({ type: 'complete', taskId: task.id, expectedStepId: step.id, commandId: commandId(), draft: draftRef.current }); }
    catch (cause) { setValidation(cause instanceof Error ? cause.message : '保存失败，请重试。'); }
    finally { submitLock.current = false; setSubmitting(false); }
  }
  function moveFocus(event: KeyboardEvent<HTMLElement>) {
    if (!['ArrowDown', 'ArrowUp', 'ArrowLeft', 'ArrowRight'].includes(event.key) || (event.target instanceof HTMLElement && ['INPUT', 'TEXTAREA', 'SELECT'].includes(event.target.tagName))) return;
    const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex="0"]')).filter(control => control.getClientRects().length > 0);
    const index = controls.indexOf(document.activeElement as HTMLElement);
    if (!controls.length) return;
    event.preventDefault(); controls[(index + (event.key === 'ArrowDown' || event.key === 'ArrowRight' ? 1 : -1) + controls.length) % controls.length].focus();
  }
  const progress = task ? stats(task) : null;
  const photos = state.photos.filter(photo => photo.taskId === task?.id && photo.stepId === step?.id);
  const checkReadings = task && step ? <>
              <fieldset className="result-field"><legend>检查结果 <span>{step.requirements.resultRequired ? '必填' : '选填'}</span></legend><div className="result-options">{allowedResults(task.templateSnapshot).map(result => <label key={result} className={draft.result === result ? `selected ${result}` : ''}><input type="radio" name="result" checked={draft.result === result} onChange={() => saveDraft({ ...draft, result })}/>{resultLabel(result)}</label>)}</div></fieldset>
              <ReadingFields step={step} draft={draft} onChange={saveDraft}/>
              {task.templateSnapshot.source && draft.result === 'abnormal' && <label className="field note-field">故障发现时间（选填）<input type="time" value={draft.faultTime ?? ''} onChange={event => saveDraft({ ...draft, faultTime: event.target.value })}/></label>}
              <label className="field note-field">{draft.result === 'abnormal' ? '异常说明（必填）' : isInactive(draft.result) ? '状态原因（必填）' : '检查备注（选填）'}<textarea rows={2} value={draft.note ?? ''} placeholder={draft.result === 'abnormal' ? '请记录异常现象及需要关注的事项' : '补充现场情况'} onChange={event => saveDraft({ ...draft, note: event.target.value })} /></label>
  </> : null;
  return <section className="glasses-column" aria-labelledby="glasses-title">
    <div className="glasses-label"><h2 id="glasses-title"><Glasses size={20} />眼镜执行预览</h2><span>眼镜界面模拟</span></div>
    <div className="glasses-screen" onKeyDown={moveFocus} tabIndex={0} aria-label="眼镜执行视窗">
      <div className="screen-top"><span><Radio size={13} />现场作业</span><span>{task ? statusLabels[task.status] : '等待任务'}</span></div>
      {!task ? <div className="screen-empty"><div className="scan-graphic"><ScanLine size={60} strokeWidth={1.2} /></div><span className="screen-eyebrow">准备就绪</span><h3>等待下发巡检任务</h3><p>在左侧选择设备与执行人<br />下发后，在此开始逐步巡检</p><div className="empty-steps"><span><ScanLine size={16} />核对设备</span><span><Camera size={16} />现场留证</span><span><CheckCheck size={16} />确认提交</span></div></div> : <>
        <div className="screen-asset"><div><span className="screen-eyebrow">当前巡检设备</span><h3>{task.assetSnapshot.name}</h3><p><MapPin size={13} />{task.assetSnapshot.location}</p></div><span className="asset-code">{task.assetSnapshot.code}</span></div>
        {task.status === 'assigned' ? <div className="screen-start"><span className="screen-status-icon"><ClipboardCheck size={38} /></span><span className="screen-eyebrow">收到任务</span><h3>{task.templateSnapshot.name}</h3><p>{task.templateSnapshot.steps.length} 个步骤 · {task.assigneeSnapshot.name}</p><div className="task-receipt">{task.schedule && <p>{task.schedule.date} · {task.schedule.time} · {task.schedule.shift}</p>}<span>任务已下发（演示）</span><small>{timeLabel(task.createdAt)}</small><p>请按步骤核对设备、采集照片并确认检查结果。</p></div><button className="primary" disabled={busy} onClick={() => void onRun(() => execute({ type: 'start', taskId: task.id, commandId: commandId() }))}><Play size={17} />开始巡检</button></div> : task.status === 'completed' ? <div className="screen-complete"><span className="screen-status-icon complete"><CheckCheck size={39} /></span><h3>本次巡检已完成</h3><p>全部 {progress?.total} 个步骤已确认并本地保存</p><span className={`conclusion ${progress?.abnormal ? 'abnormal' : 'normal'}`}>{progress?.abnormal ? `发现异常 · ${progress.abnormal} 项` : '检查结论：未发现异常'}</span><div className="completion-info"><span>{progress?.photos} 张现场照片</span><span>{timeLabel(task.completedAt)}</span></div><button className="primary" onClick={() => onReport(task.id)}><FileText size={17} />查看巡检报告</button></div> : step && <div className="screen-execution" data-testid="current-step">
          <div className="execution-heading"><div className="screen-step-heading"><span className="screen-eyebrow">步骤 {task.templateSnapshot.steps.findIndex(item => item.id === step.id) + 1} / {task.templateSnapshot.steps.length}</span><span className="screen-step-type">{step.type === 'photo_check' ? '采集与检查' : step.type === 'asset_check' ? '设备确认' : '核对与提交'}</span></div><h3 className="current-step-title">{step.title}</h3><p className="instruction">{step.instruction}</p></div>
          <div className="execution-scroll" ref={executionRef}>{task.status === 'paused' ? <div className="paused-panel"><Pause size={33} /><h4>巡检已暂停</h4><p>当前步骤、草稿和 {photos.length} 张照片已保留。<br />继续后从这里恢复。</p>{draft.result && <span>已保存结果：{resultLabel(draft.result)}</span>}{draft.note && <p className="saved-note">{draft.note}</p>}<div className="photo-grid">{photos.map(photo => <Photo key={photo.id} photo={photo} onOpen={() => onPhoto(photo)} />)}</div></div> : <>
            {step.type === 'asset_check' && <div className="asset-verification"><label className="field">设备资产码<input value={draft.assetCode ?? ''} placeholder="请输入或模拟扫描资产码" onChange={event => saveDraft({ ...draft, assetCode: event.target.value })} /></label><button className="screen-secondary" onClick={() => saveDraft({ ...draft, assetCode: task.assetSnapshot.code })}><ScanLine size={17} />模拟扫描当前设备</button><label className="check-label"><input type="checkbox" checked={draft.confirmed ?? false} onChange={event => saveDraft({ ...draft, confirmed: event.target.checked })} />我已核对设备名称与位置</label><details className="demo-helper"><summary>演示辅助</summary><button onClick={() => saveDraft({ ...draft, assetCode: 'ASSET-WRONG' })}>填入错误资产码</button><small>扫码为模拟输入，未进行二维码解码。</small></details></div>}
            {(step.type === 'photo_check' || step.requirements.minPhotos > 0 || step.requirements.resultRequired) && <>
              {task.templateSnapshot.source && checkReadings}
              <div className="photo-heading"><span><Camera size={15} />现场照片 <strong>{photos.length}</strong></span><span>{step.requirements.minPhotos ? `至少 ${step.requirements.minPhotos} 张` : '按需留证'}</span></div>
              <div className="photo-grid current-photos">{photos.map(photo => <div key={photo.id} className="evidence-tile"><Photo photo={photo} onOpen={() => onPhoto(photo)} /><button className="remove-photo" aria-label={`删除照片 ${photo.name}`} disabled={busy} onClick={() => void onRun(() => execute({ type: 'deletePhoto', taskId: task.id, expectedStepId: step.id, photoId: photo.id, commandId: commandId() }))}><Trash2 size={13} /></button></div>)}</div>
              <button className="capture-button" aria-label="采集照片" disabled={busy} onClick={() => onCapture(task, step)}><Plus size={20} />采集照片<span>演示素材 / 上传 / 摄像头</span></button>
              {!task.templateSnapshot.source && checkReadings}
            </>}
            {step.type === 'review_submit' && <><div className="review-summary"><span><strong>{progress?.completed}</strong> 项已完成</span><span><strong>{progress?.photos}</strong> 张照片</span><span className={progress?.abnormal ? 'orange-text' : ''}><strong>{progress?.abnormal}</strong> 项异常</span></div><div className="review-items">{task.stepRecords.filter(item => item.status === 'completed').map(item => <div key={item.stepId}><Check size={15} /><span>{task.templateSnapshot.steps.find(step => step.id === item.stepId)?.title}</span><strong className={item.draft.result === 'abnormal' ? 'orange-text' : ''}>{resultLabel(item.draft.result)}</strong></div>)}</div><details className="review-evidence"><summary>查看照片与异常说明</summary><div className="review-photo-grid">{state.photos.filter(photo => photo.taskId === task.id && photo.stepId !== step.id).map(photo => <Photo key={photo.id} photo={photo} onOpen={() => onPhoto(photo)} />)}</div>{task.stepRecords.filter(item => item.status === 'completed' && item.draft.result === 'abnormal').map(item => <p key={item.stepId}><strong>{task.templateSnapshot.steps.find(def => def.id === item.stepId)?.title}</strong>{item.draft.note}</p>)}</details><label className="check-label"><input type="checkbox" checked={draft.confirmed ?? false} onChange={event => saveDraft({ ...draft, confirmed: event.target.checked })} />我已核对以上记录</label></>}
            {(validation || draftError) && <div className="validation-error" role="alert" ref={validationRef}><TriangleAlert size={16} /><div>{(validation || draftError).split('\n').map((line, i) => <p key={i}>{line}</p>)}{draftError && <button onClick={() => saveDraft(draftRef.current)}>重试保存草稿</button>}</div></div>}
          </>}</div>
          {task.status === 'paused' ? <div className="screen-actions"><button className="primary" disabled={busy} onClick={() => void onRun(() => execute({ type: 'resume', taskId: task.id, commandId: commandId() }))}><Play size={16} />继续巡检</button></div> : <div className="screen-actions"><button className="primary" disabled={busy || submitting} onClick={event => { if (event.detail <= 1) void complete(); }}>{submitting ? '正在保存…' : step.type === 'review_submit' ? '提交巡检记录' : '完成本步'}<ArrowRight size={17} /></button><button className="pause-button" disabled={busy || submitting} onClick={() => void onRun(async () => { await execute({ type: 'draft', taskId: task.id, expectedStepId: step.id, commandId: commandId(), draft: draftRef.current }); await execute({ type: 'pause', taskId: task.id, commandId: commandId() }); })}><Pause size={14} />暂停巡检</button></div>}
        </div>}
      </>}
      <div className="screen-bottom"><ShieldCheck size={12} /><span>记录保存在本机浏览器</span><span>{task ? `${progress?.completed} / ${progress?.total}` : '— / —'}</span></div>
    </div>
    <p className="preview-hint"><Glasses size={14} />模拟逐步引导 · 方向键移动焦点，Enter 确认操作</p>
  </section>;
}

function ExecutionLog({ state, task, template, onPhoto, onInspect, onReport }: { state: AppState; task?: Task; template: Template; onPhoto: (photo: EvidencePhoto) => void; onInspect: (stepId: string) => void; onReport: (id: string) => void }) {
  const progress = task ? stats(task) : { completed: 0, total: template.steps.length, photos: 0, abnormal: 0, percent: 0 };
  const events = [...state.events.filter(event => event.taskId === task?.id)].reverse();
  const photos = state.photos.filter(photo => photo.taskId === task?.id);
  return <section className="card execution-log" aria-labelledby="log-title"><div className="section-title"><span className="section-icon"><History size={18} /></span><h2 id="log-title">执行记录</h2><span className="save-badge">本地记录</span></div><div className="log-body"><div className="progress-heading"><span>任务进度</span><strong>{progress.percent}%</strong></div><div className="progress-track"><span style={{ width: `${progress.percent}%` }} /></div><div className="metrics"><div><strong data-testid="completed-count">{progress.completed}<small> / {progress.total}</small></strong><span>已完成步骤</span></div><div><strong data-testid="photo-count">{progress.photos}</strong><span>已保存照片</span></div><div className={progress.abnormal ? 'has-abnormal' : ''}><strong data-testid="abnormal-count">{progress.abnormal}</strong><span>异常记录</span></div></div>
      {task && <div className="task-meta"><span><UserRound size={13} />{task.assigneeSnapshot.name}</span><span className={`badge ${task.status}`}>{statusLabels[task.status]}</span></div>}
      <div className="subheading"><h3>操作时间线</h3><span>{events.length ? `${events.length} 条记录` : '等待执行'}</span></div>
      {!events.length ? <div className="log-empty"><Clock3 size={29} strokeWidth={1.3} /><h4>还没有执行记录</h4><p>任务下发后，操作记录将在这里更新</p></div> : <ol className="timeline">{events.map(event => <li key={event.id}><span className={`event-dot ${event.type.includes('complete') ? 'done' : ''}`} /><div><p>{event.summary}</p><time dateTime={event.occurredAt}>{timeLabel(event.occurredAt)}</time></div>{event.stepId && task?.stepRecords.find(record => record.stepId === event.stepId)?.status === 'completed' && <button className="inline-link" onClick={() => onInspect(event.stepId!)}>查看</button>}</li>)}</ol>}
      <div className="subheading evidence-heading"><h3>现场留证</h3><span>{photos.length} 张</span></div>{!photos.length ? <div className="evidence-empty"><ImageIcon size={20} /><span>采集的照片会按步骤保存在这里</span></div> : <div className="log-photos">{photos.map(photo => <Photo key={photo.id} photo={photo} onOpen={() => onPhoto(photo)} />)}</div>}
      {task?.status === 'completed' && <button className="report-outline" onClick={() => onReport(task.id)}><FileText size={16} />打开报告<ArrowRight size={16} /></button>}
    </div></section>;
}
function StepDetail({ state, task, stepId, onClose, onPhoto }: { state: AppState; task: Task; stepId: string; onClose: () => void; onPhoto: (photo: EvidencePhoto) => void }) {
  const step = task.templateSnapshot.steps.find(step => step.id === stepId)!;
  const record = task.stepRecords.find(record => record.stepId === stepId)!;
  return <Modal title={step.title} onClose={onClose} wide><div className="detail-status"><span className="badge completed">已完成 · 只读</span><span>{timeLabel(record.confirmedAt)}</span></div><p>{step.instruction}</p><dl className="detail-data"><dt>设备</dt><dd>{task.assetSnapshot.name} · {task.assetSnapshot.code}</dd><dt>检查结果</dt><dd>{resultLabel(record.draft.result)}</dd>{record.draft.note && <><dt>检查说明</dt><dd>{record.draft.note}</dd></>}</dl><ReadingSummary step={step} draft={record.draft}/>{record.draft.faultTime && <p>故障发现时间：{record.draft.faultTime}</p>}<div className="detail-photos">{state.photos.filter(photo => photo.taskId === task.id && photo.stepId === stepId).map(photo => <Photo key={photo.id} photo={photo} onOpen={() => onPhoto(photo)} />)}</div></Modal>;
}
function HistoryPage({ state, onView, onReport, onNew }: { state: AppState; onView: (id: string) => void; onReport: (id: string) => void; onNew: () => void }) {
  return <section className="card history-card"><div className="section-title"><h2>任务档案</h2><span className="muted">{state.tasks.length} 个本地任务</span><button className="primary" onClick={onNew}><Plus size={15} />返回工作台</button></div>{state.tasks.length === 0 ? <div className="history-empty"><FileText size={40} strokeWidth={1.3} /><h3>暂无巡检记录</h3><p>完成一次巡检，照片与结果将按任务归档。</p><button className="primary" onClick={onNew}>创建首个任务</button></div> : <div className="table-scroll"><table><thead><tr><th>任务 / 目标设备</th><th>执行人</th><th>任务状态</th><th>检查结论</th><th>照片</th><th>开始 / 完成时间</th><th>操作</th></tr></thead><tbody>{[...state.tasks].reverse().map(task => { const progress = stats(task); return <tr key={task.id}><td><strong>{task.templateSnapshot.name}</strong><span>{task.assetSnapshot.name} · V{task.templateSnapshot.version}</span><small>{task.schedule ? `${task.schedule.date} ${task.schedule.time} · ${task.schedule.shift}` : task.id}</small></td><td>{task.assigneeSnapshot.name}</td><td><span className={`badge ${task.status}`}>{statusLabels[task.status]}</span></td><td>{progress.abnormal ? <span className="orange-text">发现异常 · {progress.abnormal} 项</span> : task.status === 'completed' ? '未发现异常' : '待完成'}</td><td>{progress.photos} 张</td><td><span>{timeLabel(task.startedAt)}</span><span>{task.completedAt ? timeLabel(task.completedAt) : '—'}</span></td><td><div className="table-actions"><button className="inline-link" onClick={() => onView(task.id)}>查看任务</button>{task.status === 'completed' && <button className="inline-link" onClick={() => onReport(task.id)}>查看报告</button>}</div></td></tr>; })}</tbody></table></div>}</section>;
}

function TemplateSelect({ templates, value, disabled, onChange, label = '巡检模板' }: { templates: Template[]; label?: string; value: string; disabled?: boolean; onChange: (id: string) => void }) {
  return <select aria-label={label} value={value} disabled={disabled} onChange={event => onChange(event.target.value)}>{['电气', '暖通', '机柜演示'].map(category => <optgroup key={category} label={category}>{templates.filter(template => (template.category ?? '机柜演示') === category).map(template => <option key={template.id} value={template.id}>{template.name} · V{template.version}</option>)}</optgroup>)}</select>;
}
function SourceNote({ template }: { template: Template }) {
  return template.source ? <details className="source-note"><summary>原表第 {template.source.page} 页 · {template.steps.length - 2} 个检查项</summary><p>{template.source.file} · {template.source.section}。默认照片按需采集，具体要求见当前步骤。</p></details> : null;
}
