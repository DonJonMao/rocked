import type { CheckResult, Draft, ReadingField, StepDefinition, Template } from './types';

export const resultLabels: Record<CheckResult, string> = { normal: '正常', abnormal: '异常', shutdown: '关机', standby: '待机', not_applicable: '不涉及' };
export const resultLabel = (value?: CheckResult) => value ? resultLabels[value] : '已核对确认';
export const isInactive = (result?: CheckResult) => result === 'shutdown' || result === 'standby' || result === 'not_applicable';
export function allowedResults(template: Template): CheckResult[] {
  return template.category === '暖通' ? ['normal', 'abnormal', 'shutdown', 'standby', 'not_applicable'] : template.category === '电气' ? ['normal', 'abnormal', 'shutdown', 'standby'] : ['normal', 'abnormal'];
}
export function fieldStandard(field: ReadingField): string {
  if (field.expected) return `应为${field.expected}`;
  const parts: string[] = [];
  if (field.min !== undefined) parts.push(`${field.minExclusive ? '>' : '≥'} ${field.min}`);
  if (field.max !== undefined) parts.push(`${field.maxExclusive ? '<' : '≤'} ${field.max}`);
  return parts.length ? `${parts.join(' 且 ')} ${field.unit ?? ''}`.trim() : '原表未给定数值阈值，人工确认';
}
export function readingIssue(field: ReadingField, raw?: string): string | undefined {
  if (!raw?.trim()) return `请填写${field.label}`;
  if (field.kind === 'select' && !field.options?.includes(raw)) return `${field.label}选项无效`;
  if (field.kind === 'number' && !Number.isFinite(Number(raw))) return `${field.label}必须为有效数字`;
  return undefined;
}
export function outOfRange(field: ReadingField, raw?: string): boolean {
  if (readingIssue(field, raw)) return false;
  if (field.expected) return raw !== field.expected;
  if (field.kind !== 'number') return false;
  const n = Number(raw);
  return (field.min !== undefined && (field.minExclusive ? n <= field.min : n < field.min)) ||
    (field.max !== undefined && (field.maxExclusive ? n >= field.max : n > field.max));
}
export function readingErrors(step: StepDefinition, draft: Draft): string[] {
  if (isInactive(draft.result)) return [];
  return (step.fields ?? []).flatMap(field => {
    const raw = draft.readings?.[field.id];
    const issue = readingIssue(field, raw);
    if (issue) return [issue];
    return outOfRange(field, raw) && draft.result !== 'abnormal' ? [`${field.label}不符合标准（${fieldStandard(field)}），请登记异常并说明`] : [];
  });
}
