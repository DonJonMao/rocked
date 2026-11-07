import { describe, expect, it } from 'vitest';
import { createInitialState, migrateCatalog } from '../../src/data/seed';
import { applyCommand, validateTemplate } from '../../src/domain/commands';
import { fieldStandard, outOfRange, readingErrors } from '../../src/domain/readings';
import { exportRecord } from '../../src/services/report';
import type { AppState, Command, Draft } from '../../src/domain/types';
import { openDB } from 'idb';
import { IndexedDbRepository } from '../../src/repositories/indexedDb';

let seq = 0;
const run = (state: AppState, input: Omit<Command, 'commandId'>) => applyCommand(state, { ...input, commandId: `catalog-${++seq}` } as Command);
const schedule = { date: '2026-11-03', time: '09:30', shift: '白班' as const };
function chiller() {
  let state = applyCommand(createInitialState(), { type: 'dispatch', commandId: `catalog-${++seq}`, templateId: 'chiller', assetId: 'chiller-1', assigneeId: 'worker-zhang', schedule });
  state = run(state, { type: 'start', taskId: state.tasks[0].id } as Command);
  return state;
}
function complete(state: AppState, draft: Draft) {
  const task = state.tasks[0];
  return applyCommand(state, { type: 'complete', commandId: `catalog-${++seq}`, taskId: task.id, expectedStepId: task.currentStepId, draft });
}
describe('PDF catalog and execution', () => {
  it('persists the legacy catalog upgrade without losing an existing issued task', async () => {
    const name = 'catalog-migration-test';
    const repo = new IndexedDbRepository(name);
    await repo.load();
    const state = chiller();
    await repo.mutate(() => state);
    await repo.close();
    const db = await openDB(name);
    const config = await db.get('configuration', 'state');
    delete config.templates;
    config.assets = config.assets.slice(0, 3);
    config.template.name = '保留的旧模板';
    await db.put('configuration', config, 'state');
    db.close();
    const recovered = await repo.load();
    expect(recovered.templates).toHaveLength(13);
    expect(recovered.template.name).toBe('保留的旧模板');
    expect(recovered.tasks).toEqual(state.tasks);
    await repo.close();
    const reopened = await repo.load();
    expect(reopened.templates).toHaveLength(13);
    expect(reopened.assets).toHaveLength(33);
    await repo.close();
  });
  it('covers all ten source pages, 12 flows, 30 targets and 135 checks without mandatory invented photos', () => {
    const state = createInitialState();
    const flows = state.templates.filter(template => template.source);
    expect(flows).toHaveLength(12);
    expect(state.assets.filter(asset => asset.type === 'inspection-target')).toHaveLength(30);
    expect(flows.reduce((sum, template) => sum + template.steps.length - 2, 0)).toBe(135);
    expect(new Set(flows.map(template => `${template.category}-${template.source!.page}`)).size).toBe(10);
    expect(Object.fromEntries(flows.map(t => [t.id, t.steps.length - 2]))).toEqual({ diesel: 11, parallel: 11, oil: 7, distribution: 15, 'battery-2': 14, 'battery-3': 14, 'battery-4': 14, chiller: 9, tower: 9, pump: 10, auxiliary: 11, ahu: 10 });
    for (const template of flows) {
      expect(validateTemplate(template)).toEqual([]);
      expect(template.steps.every(step => step.requirements.minPhotos === 0)).toBe(true);
      expect(template.assetIds!.every(id => state.assets.some(asset => asset.id === id))).toBe(true);
    }
    expect(state.templates.find(t => t.id === 'pump')!.timeSlots).toEqual(['09:30', '14:00', '21:00', '03:00']);
    expect(state.templates.find(t => t.id === 'auxiliary')!.timeSlots).toEqual(['09:30', '14:00', '22:00', '03:00']);
  });
  it('preserves strict and inclusive numeric boundaries and does not invent unspecified limits', () => {
    const state = createInitialState();
    const field = (template: string, label: string) => state.templates.find(t => t.id === template)!.steps.flatMap(s => s.fields ?? []).find(f => f.label === label)!;
    expect(outOfRange(field('chiller', '冷凝压力'), '958')).toBe(true);
    expect(outOfRange(field('chiller', '冷凝压力'), '957.9')).toBe(false);
    expect(outOfRange(field('chiller', '蒸发压力'), '226')).toBe(true);
    expect(outOfRange(field('pump', '水泵出口压力'), '0.38')).toBe(true);
    expect(outOfRange(field('pump', '水泵出口压力'), '0.6')).toBe(true);
    expect(outOfRange(field('pump', '水泵出口压力'), '0.5')).toBe(false);
    expect(outOfRange(field('auxiliary', '软水箱液位'), '1.5')).toBe(false);
    expect(outOfRange(field('auxiliary', 'pH'), '9.5')).toBe(false);
    expect(outOfRange(field('diesel', '日用油箱液位'), '50')).toBe(false);
    expect(fieldStandard(field('distribution', '铁芯温度'))).toContain('未给定');
    expect(field('ahu', '运行模式').kind).toBe('text');
  });
  it('requires date, correct time and matching device, preserving schedule in the snapshot', () => {
    const initial = createInitialState();
    const command: Command = { type: 'dispatch', commandId: 'invalid', templateId: 'chiller', assetId: 'chiller-1', assigneeId: 'worker-zhang', schedule };
    expect(() => applyCommand(initial, { ...command, schedule: undefined })).toThrow('巡检日期');
    expect(() => applyCommand(initial, { ...command, schedule: { ...schedule, date: '2026-02-30' } })).toThrow('巡检日期');
    expect(() => applyCommand(initial, { ...command, schedule: { ...schedule, time: '21:00' } })).toThrow('巡检日期');
    expect(() => applyCommand(initial, { ...command, assetId: 'room-101' })).toThrow('不属于');
    expect(chiller().tasks[0].schedule).toEqual(schedule);
  });
  it('blocks missing, invalid and out-of-range readings until an abnormal result and note are supplied', () => {
    let state = chiller();
    state = complete(state, { assetCode: 'B4-CHILLER-1', confirmed: true });
    state = complete(state, { result: 'normal' });
    state = complete(state, { result: 'normal' });
    expect(() => complete(state, { result: 'normal' })).toThrow('请填写冷凝压力');
    expect(() => complete(state, { result: 'normal', readings: { 'value-1': 'NaN' } })).toThrow('有效数字');
    expect(() => complete(state, { result: 'normal', readings: { 'value-1': '958' } })).toThrow('登记异常');
    expect(() => complete(state, { result: 'abnormal', readings: { 'value-1': '958' } })).toThrow('异常说明');
    state = complete(state, { result: 'abnormal', readings: { 'value-1': '958' }, note: '压力达到上限，已记录待复核', faultTime: '09:35' });
    const exported = exportRecord(state, state.tasks[0]);
    expect(exported.task.stepRecords[3].draft).toMatchObject({ readings: { 'value-1': '958' }, faultTime: '09:35' });
    expect(exported.statistics.abnormal).toBe(1);
  });
  it('accepts inactive states with a reason, never silently fills in a normal reading', () => {
    let state = complete(chiller(), { assetCode: 'B4-CHILLER-1', confirmed: true });
    expect(() => complete(state, { result: 'shutdown' })).toThrow('原因');
    state = complete(state, { result: 'shutdown', note: '计划停机' });
    state = complete(state, { result: 'not_applicable', note: '停机期间不涉及' });
    state = complete(state, { result: 'standby', note: '备用机未运行' });
    expect(state.tasks[0].stepRecords[3].draft.readings).toBeUndefined();
    expect(state.tasks[0].stepRecords[3].draft.result).toBe('standby');
  });
  it('requires both paired measurements and reports incorrect control modes', () => {
    const state = createInitialState();
    const temp = state.templates.find(t => t.id === 'chiller')!.steps.find(s => s.title.includes('供回水'))!;
    expect(readingErrors(temp, { result: 'normal', readings: { 'value-1': '7' } })).toEqual(['请填写回水温度']);
    const mode = state.templates.find(t => t.id === 'distribution')!.steps.find(s => s.title === '高压柜 · 控制方式')!;
    expect(readingErrors(mode, { result: 'normal', readings: { 'value-1': '自动' } }).join('')).toContain('登记异常');
  });
  it('migrates old data idempotently and edits only the chosen template, keeping issued snapshots', () => {
    const state = chiller();
    const legacy = { ...state, assets: state.assets.slice(0, 3), template: { ...state.template, name: '已编辑旧模板', version: 9 } };
    delete (legacy as Partial<AppState>).templates;
    const migrated = migrateCatalog(legacy);
    expect(migrated.template.name).toBe('已编辑旧模板');
    expect(migrated.tasks).toBe(legacy.tasks);
    expect(migrated.photos).toBe(legacy.photos);
    expect(migrateCatalog(migrated)).toBe(migrated);
    const edited = applyCommand(state, { type: 'saveTemplate', commandId: `catalog-${++seq}`, template: { ...state.templates.find(t => t.id === 'chiller')!, name: '修改冷机流程' } });
    expect(edited.template.name).toBe('机柜例行巡检');
    expect(edited.templates.find(t => t.id === 'chiller')!.name).toBe('修改冷机流程');
    expect(edited.tasks[0].templateSnapshot.name).toBe('冷水机组巡检');
  });
});
