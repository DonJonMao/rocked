import { Blob as NodeBlob } from 'node:buffer';
import { describe, expect, it } from 'vitest';
import { createInitialState } from '../../src/data/seed';
import { applyCommand, validateStep, validateTemplate } from '../../src/domain/commands';
import type { AppState, Command, EvidencePhoto, Task } from '../../src/domain/types';
import { exportRecord, reportConclusion, stats } from '../../src/services/report';

type Input = Command extends infer C ? C extends Command ? Omit<C, 'commandId'> : never : never;
let sequence = 0;
const now = '2026-10-28T08:00:00.000Z';
const command = (input: Input): Command => ({ ...input, commandId: `unit-${++sequence}` });
const run = (state: AppState, input: Input) => applyCommand(state, command(input), now);
const taskOf = (state: AppState) => state.tasks.at(-1)!;
const current = (state: AppState) => ({ taskId: taskOf(state).id, expectedStepId: taskOf(state).currentStepId });
function started(): AppState {
  const state = run(createInitialState(), { type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' });
  return run(state, { type: 'start', taskId: taskOf(state).id });
}
function photoStep(): AppState {
  const state = started();
  return run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A03', confirmed: true } });
}
function photo(state: AppState): EvidencePhoto {
  return { id: `photo-${++sequence}`, taskId: taskOf(state).id, stepId: taskOf(state).currentStepId,
    source: 'fixture', isDemo: true, blob: new NodeBlob(['test-image'], { type: 'image/png' }) as unknown as Blob,
    width: 40, height: 30, addedAt: now, name: '演示素材.png' };
}
function addPhoto(state: AppState): AppState { return run(state, { type: 'addPhoto', ...current(state), photo: photo(state) }); }
function finishCheck(state: AppState, abnormal = false): AppState {
  const withPhoto = addPhoto(state);
  return run(withPhoto, { type: 'complete', ...current(withPhoto), draft: { result: abnormal ? 'abnormal' : 'normal', note: abnormal ? '故障灯亮，请复核' : '' } });
}
function finishTask(state: AppState): AppState {
  let next = state;
  while (taskOf(next).templateSnapshot.steps.find((step) => step.id === taskOf(next).currentStepId)?.type === 'photo_check') next = finishCheck(next);
  return run(next, { type: 'complete', ...current(next), draft: { confirmed: true } });
}

describe('task command transitions and field rules', () => {
  it('seeds are independent and dispatch saves separate snapshots, awaiting an explicit start', () => {
    const state = createInitialState();
    const next = run(state, { type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' });
    expect(taskOf(next).status).toBe('assigned');
    expect(taskOf(next).startedAt).toBeUndefined();
    state.template.steps[1].title = '被改动';
    state.assets[0].name = '被改动';
    expect(taskOf(next).templateSnapshot.steps[1].title).toBe('检查柜门与外观');
    expect(taskOf(next).assetSnapshot.name).toBe('机柜 A03');
    expect(createInitialState().template.steps[1].title).toBe('检查柜门与外观');
    expect(() => run(next, { type: 'dispatch', assetId: 'asset-a04', assigneeId: 'worker-li' })).toThrow('先完成当前任务');
    expect(() => run(next, { type: 'complete', ...current(next) })).toThrow('尚未开始');
  });

  it('rejects invalid target/person and duplicate start', () => {
    expect(() => run(createInitialState(), { type: 'dispatch', assetId: 'missing', assigneeId: 'worker-zhang' })).toThrow('有效');
    const state = started();
    expect(taskOf(state).startedAt).toBe(now);
    expect(() => run(state, { type: 'start', taskId: taskOf(state).id })).toThrow('不能重复开始');
  });

  it('requires the exact assigned asset and worker confirmation without advancing on error', () => {
    const state = started();
    expect(() => run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A04', confirmed: true } })).toThrow('ASSET-A03');
    expect(() => run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A03' } })).toThrow('确认设备');
    expect(taskOf(state).currentStepId).toBe('verify-asset');
    const next = run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A03', confirmed: true } });
    expect(taskOf(next).currentStepId).toBe('check-cabinet');
    expect(taskOf(next).stepRecords[0].confirmedAt).toBe(now);
    expect(taskOf(next).stepRecords[2].status).toBe('locked');
  });

  it('reports specific missing photos, result and abnormal note and preserves the original state', () => {
    let state = photoStep();
    const before = taskOf(state);
    expect(() => run(state, { type: 'complete', ...current(state) })).toThrow('请先拍摄或选择至少 1 张照片');
    expect(taskOf(state)).toBe(before);
    expect(taskOf(state).stepRecords[1].draft).toEqual({});
    state = addPhoto(state);
    expect(() => run(state, { type: 'complete', ...current(state) })).toThrow('请选择检查结果');
    expect(() => run(state, { type: 'complete', ...current(state), draft: { result: 'abnormal', note: '  ' } })).toThrow('请填写异常说明');
    const next = run(state, { type: 'complete', ...current(state), draft: { result: 'abnormal', note: '灯罩破损' } });
    expect(taskOf(next).currentStepId).toBe('check-indicators');
    expect(stats(taskOf(next)).abnormal).toBe(1);
  });

  it('honors configurable multi-photo and optional-result requirements using the generic step', () => {
    let state = createInitialState();
    state.template.steps[1].requirements = { minPhotos: 2, resultRequired: false, abnormalNoteRequired: true, confirmationRequired: true };
    state = run(state, { type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' });
    state = run(state, { type: 'start', taskId: taskOf(state).id });
    state = run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A03', confirmed: true } });
    state = addPhoto(state);
    expect(() => run(state, { type: 'complete', ...current(state) })).toThrow('至少 2 张照片');
    state = addPhoto(state);
    state = run(state, { type: 'complete', ...current(state) });
    expect(taskOf(state).currentStepId).toBe('check-indicators');
  });

  it('replaying commandId does not advance or add another completion event', () => {
    const state = addPhoto(photoStep());
    const completion = command({ type: 'complete', ...current(state), draft: { result: 'normal' } });
    const next = applyCommand(state, completion, now);
    const replay = applyCommand(next, completion, now);
    expect(replay).toBe(next);
    expect(replay.events.filter((event) => event.commandId === completion.commandId)).toHaveLength(1);
    expect(taskOf(replay).currentStepId).toBe('check-indicators');
  });

  it('rejects a late completion with an old expectedStepId and any future step', () => {
    const state = finishCheck(photoStep());
    expect(() => run(state, { type: 'complete', taskId: taskOf(state).id, expectedStepId: 'check-cabinet' })).toThrow('已过期');
    expect(() => run(state, { type: 'complete', taskId: taskOf(state).id, expectedStepId: 'check-panel' })).toThrow('已过期');
    expect(taskOf(state).currentStepId).toBe('check-indicators');
  });

  it('saves drafts and photos through pause/resume while disallowing paused mutations', () => {
    let state = addPhoto(photoStep());
    state = run(state, { type: 'draft', ...current(state), draft: { result: 'abnormal', note: '待复核' } });
    const before = structuredClone(taskOf(state).stepRecords);
    state = run(state, { type: 'pause', taskId: taskOf(state).id });
    expect(taskOf(state).stepRecords).toEqual(before);
    expect(() => run(state, { type: 'draft', ...current(state), draft: { note: '修改' } })).toThrow('已暂停');
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: photo(state) })).toThrow('已暂停');
    expect(() => run(state, { type: 'complete', ...current(state) })).toThrow('已暂停');
    state = run(state, { type: 'resume', taskId: taskOf(state).id });
    expect(taskOf(state).stepRecords).toEqual(before);
    expect(taskOf(state).status).toBe('running');
  });

  it('rejects wrong photo bindings and late photos, and recalculates validation after deletion', () => {
    let state = photoStep();
    const wrong = { ...photo(state), stepId: 'check-panel' };
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: wrong })).toThrow('不一致');
    const late = photo(state);
    state = addPhoto(state);
    const id = taskOf(state).stepRecords[1].photoIds[0];
    state = run(state, { type: 'deletePhoto', ...current(state), photoId: id });
    expect(state.photos).toHaveLength(0);
    expect(() => run(state, { type: 'complete', ...current(state), draft: { result: 'normal' } })).toThrow('至少 1 张');
    state = finishCheck(state);
    expect(() => run(state, { type: 'addPhoto', taskId: late.taskId, expectedStepId: late.stepId, photo: late })).toThrow('已过期');
    expect(() => run(state, { type: 'deletePhoto', taskId: late.taskId, expectedStepId: late.stepId, photoId: id })).toThrow('已过期');
  });

  it('refuses metadata pretending to represent a saved photo when its Blob association is absent', () => {
    const state = photoStep();
    taskOf(state).stepRecords[1].photoIds.push('missing-blob');
    expect(() => run(state, { type: 'complete', ...current(state), draft: { result: 'normal' } })).toThrow('尚未成功保存');
  });

  it('validates photo size, MIME and decoded dimensions at command boundary', () => {
    const state = photoStep();
    const original = photo(state);
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: { ...original, blob: new NodeBlob([], { type: 'image/png' }) as unknown as Blob } })).toThrow('不能为空');
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: { ...original, blob: new NodeBlob(['svg'], { type: 'image/svg+xml' }) as unknown as Blob } })).toThrow('仅支持');
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: { ...original, width: 0 } })).toThrow('无法解码');
  });

  it('requires review confirmation, reaches 100%, and separates completion from abnormal conclusion', () => {
    let state = finishCheck(photoStep(), true);
    state = finishCheck(state);
    state = finishCheck(state);
    expect(stats(taskOf(state))).toEqual({ completed: 4, total: 5, photos: 3, abnormal: 1, percent: 80 });
    expect(() => run(state, { type: 'complete', ...current(state) })).toThrow('我已核对以上记录');
    state = run(state, { type: 'complete', ...current(state), draft: { confirmed: true } });
    expect(taskOf(state).status).toBe('completed');
    expect(taskOf(state).completedAt).toBe(now);
    expect(stats(taskOf(state)).percent).toBe(100);
    expect(reportConclusion(taskOf(state))).toBe('发现异常');
    expect(() => run(state, { type: 'draft', ...current(state), draft: {} })).toThrow('已完成');
    expect(() => run(state, { type: 'addPhoto', ...current(state), photo: photo(state) })).toThrow('已完成');
  });
});

describe('templates, history, reporting and reset', () => {
  it('validates template names, titles, IDs, minimum items, anchors, photos and versions', () => {
    const template = createInitialState().template;
    expect(validateTemplate(template)).toEqual([]);
    expect(validateTemplate({ ...template, name: ' ' })).toContain('请填写模板名称');
    expect(validateTemplate({ ...template, version: 1.2 })).toContain('版本号必须为正整数');
    const invalid = structuredClone(template);
    invalid.steps[1].id = invalid.steps[0].id;
    invalid.steps[1].title = ' ';
    invalid.steps[1].requirements.minPhotos = -1;
    expect(validateTemplate(invalid).join('\n')).toMatch(/ID.*重复/);
    expect(validateTemplate(invalid).join('\n')).toContain('照片数量必须为非负整数');
    expect(validateTemplate({ ...template, steps: [template.steps[0], template.steps.at(-1)!] })).toContain('至少保留一个内部检查项');
    expect(validateTemplate({ ...template, steps: [...template.steps].reverse() }).join('\n')).toContain('必须位于首项');
  });

  it('saving template creates a higher version and does not alter dispatched task snapshots', () => {
    let state = photoStep();
    const old = taskOf(state);
    const revised = structuredClone(state.template);
    revised.steps[2].title = '新指示灯项目';
    revised.steps[2].requirements.minPhotos = 2;
    state = run(state, { type: 'saveTemplate', template: revised });
    expect(state.template.version).toBe(2);
    expect(taskOf(state)).toBe(old);
    expect(old.templateSnapshot.steps[2].title).toBe('检查设备指示灯');
    state = finishTask(state);
    state = run(state, { type: 'dispatch', assetId: 'asset-a04', assigneeId: 'worker-li' });
    expect(taskOf(state).templateSnapshot.version).toBe(2);
    expect(taskOf(state).templateSnapshot.steps[2].requirements.minPhotos).toBe(2);
    expect(state.tasks[0].templateSnapshot.version).toBe(1);
  });

  it('newly configured steps execute through the same rule and preserve anchor positions', () => {
    let state = createInitialState();
    const template = structuredClone(state.template);
    template.steps.splice(2, 0, { ...structuredClone(template.steps[1]), id: 'new-check', title: '新增检查' });
    state = run(state, { type: 'saveTemplate', template });
    state = run(state, { type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' });
    state = run(state, { type: 'start', taskId: taskOf(state).id });
    state = run(state, { type: 'complete', ...current(state), draft: { assetCode: 'ASSET-A03', confirmed: true } });
    state = finishCheck(state);
    expect(taskOf(state).currentStepId).toBe('new-check');
    state = finishTask(state);
    expect(stats(taskOf(state))).toEqual({ completed: 6, total: 6, photos: 4, abnormal: 0, percent: 100 });
  });

  it('exports durable photo metadata with correctly isolated history and no object URLs or Blobs', () => {
    let state = finishTask(photoStep());
    const first = taskOf(state);
    state = run(state, { type: 'dispatch', assetId: 'asset-a04', assigneeId: 'worker-li' });
    const exported = exportRecord(state, first);
    expect(exported.photos).toHaveLength(3);
    expect(exported.events.every((event) => event.taskId === first.id)).toBe(true);
    expect(exported.photos.every((entry) => entry.taskId === first.id && entry.source === 'fixture' && entry.isDemo)).toBe(true);
    expect(JSON.stringify(exported)).not.toContain('blob:');
    expect(exported.photos[0]).not.toHaveProperty('blob');
    expect(exported.photos[0]).toMatchObject({ width: 40, height: 30, mimeType: 'image/png', size: 10 });
    expect(stats(taskOf(state)).photos).toBe(0);
    expect(reportConclusion(first)).toBe('未发现异常');
  });

  it('reset clears tasks, photos, events and edits, cancels late photo commands, and is repeatable', () => {
    for (let round = 0; round < 3; round++) {
      let state = addPhoto(photoStep());
      const pending = command({ type: 'addPhoto', ...current(state), photo: photo(state) });
      state = run(state, { type: 'reset' });
      expect(state.tasks).toEqual([]);
      expect(state.photos).toEqual([]);
      expect(state.events).toEqual([]);
      expect(state.template.version).toBe(1);
      expect(state.assets).toHaveLength(33);
      expect(() => applyCommand(state, pending)).toThrow('任务已不存在');
    }
  });

  it('never allows final submission if any previous step is incomplete', () => {
    const state = started();
    const task: Task = taskOf(state);
    const step = task.templateSnapshot.steps.at(-1)!;
    const record = { ...task.stepRecords.at(-1)!, draft: { confirmed: true } };
    expect(validateStep(task, step, record)).toContain('请先完成前面的全部步骤');
  });
});
