import { useState } from 'react';
import { ArrowDown, ArrowUp, LockKeyhole, Plus, Save, Trash2 } from 'lucide-react';
import type { StepDefinition, Template } from '../domain/types';
import './features.css';
import { fieldStandard } from '../domain/readings';

export function TemplateEditor({ template, busy, onSave }: { template: Template; busy: boolean; onSave: (template: Template) => Promise<void> }) {
  const [draft, setDraft] = useState<Template>(() => ({ ...structuredClone(template), version: template.version + 1 }));
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');
  const [saving, setSaving] = useState(false);
  const disabled = busy || saving;
  function updateStep(index: number, patch: Partial<StepDefinition>) { setSaved(''); setDraft(value => ({ ...value, steps: value.steps.map((step, i) => i === index ? { ...step, ...patch } : step) })); }
  function move(index: number, delta: number) {
    setSaved(''); setDraft(value => { const steps = [...value.steps]; [steps[index], steps[index + delta]] = [steps[index + delta], steps[index]]; return { ...value, steps }; });
  }
  async function save(event: React.FormEvent) {
    event.preventDefault();
    if (disabled) return;
    setError(''); setSaved(''); setSaving(true);
    try {
      if (!draft.name.trim()) throw new Error('请填写模板名称。');
      if (!Number.isInteger(draft.version) || draft.version < 1) throw new Error('版本号必须为正整数。');
      for (const [i, step] of draft.steps.entries()) {
        if (!step.title.trim()) throw new Error(`请填写第 ${i + 1} 步的步骤名称。`);
        if (!Number.isInteger(step.requirements.minPhotos) || step.requirements.minPhotos < 0) throw new Error(`第 ${i + 1} 步的照片数量必须为非负整数。`);
      }
      const version = Math.max(template.version + 1, draft.version);
      await onSave({ ...draft, version });
      setSaved(`模板已本地保存为 v${version}，只影响后续下发任务。`);
      setDraft(value => ({ ...value, version: version + 1 }));
    } catch (reason) { setError(reason instanceof Error ? reason.message : '模板保存失败，请重试。'); }
    finally { setSaving(false); }
  }
  return <section className="template-editor"><div className="section-intro"><div><span className="eyebrow">流程配置</span><h2>把巡检要求，落实到每一步</h2><p className="muted">已下发任务保留原模板快照。修改后，新任务使用新的流程版本。</p></div><span className="badge">当前已保存 v{template.version}</span></div>
    <form onSubmit={event => void save(event)} noValidate>
      <div className="template-meta card"><label className="field">模板名称<input value={draft.name} disabled={disabled} onChange={event => { setSaved(''); setDraft({ ...draft, name: event.target.value }); }}/></label><label className="field">发布版本<input type="number" min={template.version + 1} step="1" value={Number.isNaN(draft.version) ? '' : draft.version} disabled={disabled} onChange={event => { setSaved(''); setDraft({ ...draft, version: event.target.value === '' ? NaN : Number(event.target.value) }); }}/></label></div>
      <div className="template-steps">{draft.steps.map((step, index) => {
        const fixed = index === 0 || index === draft.steps.length - 1;
        return <article className="template-step card" key={step.id} data-testid={`template-step-${index + 1}`}><header><span className="step-number">{String(index + 1).padStart(2, '0')}</span><h3>{fixed ? index === 0 ? '设备确认' : '最终提交' : '检查项'}</h3>{fixed ? <span className="badge muted"><LockKeyhole size={13}/>位置固定</span> : <div className="step-actions"><button type="button" className="icon-button" aria-label={`上移第 ${index + 1} 步`} disabled={disabled || index === 1} onClick={() => move(index, -1)}><ArrowUp size={17}/></button><button type="button" className="icon-button" aria-label={`下移第 ${index + 1} 步`} disabled={disabled || index === draft.steps.length - 2} onClick={() => move(index, 1)}><ArrowDown size={17}/></button><button type="button" className="icon-button danger" aria-label={`删除第 ${index + 1} 步`} disabled={disabled || draft.steps.length <= 3} onClick={() => { setSaved(''); setDraft(value => ({ ...value, steps: value.steps.filter(item => item.id !== step.id) })); }}><Trash2 size={17}/></button></div>}</header>
          <div className="template-fields"><label className="field">步骤名称<input aria-label={`第 ${index + 1} 步名称`} value={step.title} disabled={disabled} onChange={event => updateStep(index, { title: event.target.value })}/></label><label className="field">操作说明<textarea aria-label={`第 ${index + 1} 步说明`} rows={2} value={step.instruction} disabled={disabled} onChange={event => updateStep(index, { instruction: event.target.value })}/></label><div className="requirements-row"><label className="field">至少拍摄照片<input aria-label={`第 ${index + 1} 步照片数量`} type="number" min="0" step="1" value={Number.isNaN(step.requirements.minPhotos) ? '' : step.requirements.minPhotos} disabled={disabled} onChange={event => updateStep(index, { requirements: { ...step.requirements, minPhotos: event.target.value === '' ? NaN : Number(event.target.value) } })}/></label><label className="check-field"><input type="checkbox" aria-label={`第 ${index + 1} 步必填检查结果`} checked={step.requirements.resultRequired} disabled={disabled} onChange={event => updateStep(index, { requirements: { ...step.requirements, resultRequired: event.target.checked } })}/>必填检查结果</label><span className="muted requirements-note">异常时须填写说明</span></div></div>
          {step.fields?.length ? <div className="template-reading-info"><strong>读数字段及标准（来自原表）</strong>{step.fields.map(field => <p key={field.id}>{field.label}{field.unit ? `（${field.unit}）` : ''} · {field.kind === 'select' ? field.options?.join(' / ') + '；' : ''}{fieldStandard(field)}</p>)}<small>当前编辑器可调整步骤、说明和照片要求；读数结构与阈值由模板配置管理。</small></div> : null}
        </article>;
      })}</div>
      <button type="button" className="button secondary add-step" disabled={disabled} onClick={() => { setSaved(''); setDraft(value => ({ ...value, steps: [...value.steps.slice(0, -1), { id: `check-${crypto.randomUUID()}`, type: 'photo_check', title: '新增检查项', instruction: '请检查设备状态，拍摄照片并记录检查结果。', requirements: { minPhotos: 1, resultRequired: true, abnormalNoteRequired: true, confirmationRequired: true } }, value.steps[value.steps.length - 1]] })); }}><Plus size={17}/>新增检查项</button>
      <footer className="template-save"><div aria-live="polite">{error && <p role="alert" className="error">{error}</p>}{saved && <p className="success-message" role="status">{saved}</p>}<p className="muted">设备确认固定为第一步，提交记录固定为最后一步，至少保留一个检查项。</p></div><button type="submit" className="button primary" disabled={disabled}><Save size={17}/>{saving ? '保存中…' : '保存模板'}</button></footer>
    </form>
  </section>;
}
