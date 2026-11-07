import type { Draft, StepDefinition } from '../domain/types';
import { fieldStandard, isInactive, outOfRange } from '../domain/readings';

export function ReadingFields({ step, draft, onChange }: { step: StepDefinition; draft: Draft; onChange: (draft: Draft) => void }) {
  if (!step.fields?.length) return null;
  const inactive = isInactive(draft.result);
  return <div className="reading-fields">{inactive && <p className="reading-hint">当前状态免填读数，请在备注中说明原因。已有读数保留为现场记录。</p>}{step.fields.map(field => {
    const value = draft.readings?.[field.id] ?? '';
    const invalid = !inactive && outOfRange(field, value);
    const update = (value: string) => onChange({ ...draft, readings: { ...draft.readings, [field.id]: value } });
    return <label className="field reading-field" key={field.id}>{field.label}{field.unit ? `（${field.unit}）` : ''}
      {field.kind === 'select' ? <select aria-label={field.label} value={value} disabled={inactive} onChange={event => update(event.target.value)}><option value="">请选择</option>{field.options?.map(option => <option key={option}>{option}</option>)}</select> : <input aria-label={field.label} type={field.kind === 'number' ? 'number' : 'text'} step={field.kind === 'number' ? 'any' : undefined} value={value} disabled={inactive} onChange={event => update(event.target.value)} placeholder="填写现场实测值" />}
      <small className={invalid ? 'reading-warning' : 'reading-hint'}>{invalid ? '不符合标准，请登记异常：' : '标准：'}{fieldStandard(field)}</small>
    </label>;
  })}</div>;
}

export function ReadingSummary({ step, draft }: { step: StepDefinition; draft: Draft }) {
  if (!step.fields?.length) return null;
  return <div className="reading-summary">{step.fields.map(field => <div key={field.id}><strong>{field.label}</strong><span>{draft.readings?.[field.id] || (isInactive(draft.result) ? '免填' : '未填写')} {draft.readings?.[field.id] ? field.unit : ''}</span><small>标准：{fieldStandard(field)}{draft.readings?.[field.id] && outOfRange(field, draft.readings[field.id]) ? ' · 不符合标准' : ''}</small></div>)}</div>;
}
