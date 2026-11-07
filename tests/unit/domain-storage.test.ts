import { Blob as NodeBlob } from 'node:buffer';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { IndexedDbRepository } from '../../src/repositories/indexedDb';
import { InspectionService } from '../../src/services/inspectionService';
import type { AppState, Command, EvidencePhoto } from '../../src/domain/types';

type Input = Command extends infer C ? C extends Command ? Omit<C, 'commandId'> : never : never;
let sequence = 0;
const command = (input: Input): Command => ({ ...input, commandId: `storage-${++sequence}` });
const repositories: IndexedDbRepository[] = [];
function setup(name = `test-inspection-${++sequence}`) {
  const repository = new IndexedDbRepository(name);
  repositories.push(repository);
  return { name, repository, service: new InspectionService(repository) };
}
async function atPhotoStep(service: InspectionService): Promise<AppState> {
  await service.init();
  let state = await service.execute(command({ type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' }));
  const taskId = state.tasks[0].id;
  state = await service.execute(command({ type: 'start', taskId }));
  return service.execute(command({ type: 'complete', taskId, expectedStepId: state.tasks[0].currentStepId, draft: { assetCode: 'ASSET-A03', confirmed: true } }));
}
function photo(state: AppState): EvidencePhoto {
  return { id: `image-${++sequence}`, taskId: state.tasks[0].id, stepId: state.tasks[0].currentStepId,
    source: 'upload', isDemo: false, blob: new NodeBlob(['durable bytes'], { type: 'image/png' }) as unknown as Blob,
    width: 40, height: 30, name: 'test.png', addedAt: new Date().toISOString() };
}
afterEach(async () => {
  vi.restoreAllMocks();
  await Promise.all(repositories.splice(0).map((repository) => repository.close()));
});

describe('IndexedDB command transactions and reload recovery', () => {
  it('initializes only once and recovers task, draft, photo bytes and events from a new connection', async () => {
    const first = setup();
    let state = await atPhotoStep(first.service);
    const attachment = photo(state);
    state = await first.service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    state = await first.service.execute(command({ type: 'draft', taskId: attachment.taskId, expectedStepId: attachment.stepId, draft: { result: 'abnormal', note: '暂停前的草稿' } }));
    state = await first.service.execute(command({ type: 'pause', taskId: attachment.taskId }));
    await first.repository.close();
    const recovered = await setup(first.name).service.init();
    expect(recovered.tasks).toEqual(state.tasks);
    expect(recovered.events).toEqual(state.events);
    expect(recovered.photos[0].blob.size).toBe(13);
    expect(await recovered.photos[0].blob.text()).toBe('durable bytes');
    expect(recovered.photos[0].stepId).toBe('check-cabinet');
    expect(recovered.tasks[0].stepRecords[1].photoIds).toEqual([attachment.id]);
    expect(recovered.tasks[0].status).toBe('paused');
    expect(recovered.assets).toHaveLength(33);
  });

  it('rolls back Blob, association and event when the event write fails; the same command can retry', async () => {
    const { service } = setup();
    const state = await atPhotoStep(service);
    const attachment = photo(state);
    const add = command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment });
    const original = IDBObjectStore.prototype.put;
    const fail = vi.spyOn(IDBObjectStore.prototype, 'put').mockImplementation(function (this: IDBObjectStore, ...args) {
      if (this.name === 'events') throw new DOMException('Test storage full', 'QuotaExceededError');
      return original.apply(this, args);
    });
    await expect(service.execute(add)).rejects.toThrow('保存失败，当前步骤未推进');
    fail.mockRestore();
    const unchanged = await service.init();
    expect(unchanged.tasks).toEqual(state.tasks);
    expect(unchanged.photos).toHaveLength(0);
    expect(unchanged.events).toEqual(state.events);
    expect(unchanged.processedCommandIds).not.toContain(add.commandId);
    const retried = await service.execute(add);
    expect(retried.photos).toHaveLength(1);
    expect(retried.tasks[0].stepRecords[1].photoIds).toEqual([attachment.id]);
    expect(retried.events.filter((event) => event.commandId === add.commandId)).toHaveLength(1);
  });

  it('failed completion does not advance and can be retried without duplicate events', async () => {
    const { service } = setup();
    let state = await atPhotoStep(service);
    const attachment = photo(state);
    state = await service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    const complete = command({ type: 'complete', taskId: attachment.taskId, expectedStepId: attachment.stepId, draft: { result: 'normal' } });
    vi.spyOn(IDBObjectStore.prototype, 'put').mockImplementationOnce(() => { throw new DOMException('full', 'QuotaExceededError'); });
    await expect(service.execute(complete)).rejects.toThrow('保存失败');
    expect((await service.init()).tasks).toEqual(state.tasks);
    const next = await service.execute(complete);
    expect(next.tasks[0].currentStepId).toBe('check-indicators');
    const replay = await service.execute(complete);
    expect(replay.events.filter((event) => event.commandId === complete.commandId)).toHaveLength(1);
  });

  it('serializes rapid commands and rejects a distinct late completion instead of advancing twice', async () => {
    const { service } = setup();
    const state = await atPhotoStep(service);
    const attachment = photo(state);
    await service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    const first = command({ type: 'complete', taskId: attachment.taskId, expectedStepId: attachment.stepId, draft: { result: 'normal' } });
    const [a, b, c] = await Promise.allSettled([
      service.execute(first), service.execute(first), service.execute({ ...first, commandId: 'distinct-late' }),
    ]);
    expect(a.status).toBe('fulfilled');
    expect(b.status).toBe('fulfilled');
    expect(c.status).toBe('rejected');
    const persisted = await service.init();
    expect(persisted.tasks[0].currentStepId).toBe('check-indicators');
    expect(persisted.events.filter((event) => event.commandId === first.commandId)).toHaveLength(1);
  });

  it('independent connections serialize read/check/write and preserve the one-active-task invariant', async () => {
    const first = setup();
    const second = setup(first.name);
    await first.service.init();
    const results = await Promise.allSettled([
      first.service.execute(command({ type: 'dispatch', assetId: 'asset-a03', assigneeId: 'worker-zhang' })),
      second.service.execute(command({ type: 'dispatch', assetId: 'asset-a04', assigneeId: 'worker-li' })),
    ]);
    expect(results.filter((result) => result.status === 'fulfilled')).toHaveLength(1);
    expect((await first.service.init()).tasks).toHaveLength(1);
  });

  it('reset atomically restores defaults and removes all task, image and event records', async () => {
    const { service } = setup();
    const state = await atPhotoStep(service);
    const attachment = photo(state);
    await service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    await service.execute(command({ type: 'saveTemplate', template: { ...state.template, name: '修改后模板' } }));
    await service.execute(command({ type: 'reset' }));
    const recovered = await service.init();
    expect(recovered.template.name).toBe('机柜例行巡检');
    expect(recovered.template.version).toBe(1);
    expect(recovered.tasks).toHaveLength(0);
    expect(recovered.photos).toHaveLength(0);
    expect(recovered.events).toHaveLength(0);
    await expect(service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }))).rejects.toThrow('任务已不存在');
  });

  it('a failed photo deletion preserves the Blob and association; retry deletes them together', async () => {
    const { service } = setup();
    let state = await atPhotoStep(service);
    const attachment = photo(state);
    state = await service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    const remove = command({ type: 'deletePhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photoId: attachment.id });
    const original = IDBObjectStore.prototype.put;
    const fail = vi.spyOn(IDBObjectStore.prototype, 'put').mockImplementation(function (this: IDBObjectStore, ...args) {
      if (this.name === 'events') throw new DOMException('Test storage full', 'QuotaExceededError');
      return original.apply(this, args);
    });
    await expect(service.execute(remove)).rejects.toThrow('保存失败');
    fail.mockRestore();
    const unchanged = await service.init();
    expect(unchanged.tasks).toEqual(state.tasks);
    expect(unchanged.events).toEqual(state.events);
    expect(await unchanged.photos[0].blob.text()).toBe('durable bytes');
    expect(unchanged.processedCommandIds).not.toContain(remove.commandId);
    const retried = await service.execute(remove);
    expect(retried.photos).toHaveLength(0);
    expect(retried.tasks[0].stepRecords[1].photoIds).toHaveLength(0);
    expect(retried.events.filter(event => event.commandId === remove.commandId)).toHaveLength(1);
    await expect(service.execute(command({ type: 'complete', taskId: attachment.taskId, expectedStepId: attachment.stepId, draft: { result: 'normal' } }))).rejects.toThrow('至少 1 张照片');
  });

  it('a failed reset rolls back default config and partial deletions, then retries safely', async () => {
    const { service } = setup();
    let state = await atPhotoStep(service);
    const attachment = photo(state);
    await service.execute(command({ type: 'addPhoto', taskId: attachment.taskId, expectedStepId: attachment.stepId, photo: attachment }));
    state = await service.execute(command({ type: 'saveTemplate', template: { ...state.template, name: '保留自定义配置' } }));
    const reset = command({ type: 'reset' });
    const original = IDBObjectStore.prototype.delete;
    const fail = vi.spyOn(IDBObjectStore.prototype, 'delete').mockImplementation(function (this: IDBObjectStore, ...args) {
      if (this.name === 'photos') throw new DOMException('Test interrupted reset', 'UnknownError');
      return original.apply(this, args);
    });
    await expect(service.execute(reset)).rejects.toThrow('保存失败');
    fail.mockRestore();
    const unchanged = await service.init();
    expect(unchanged.template).toEqual(state.template);
    expect(unchanged.tasks).toEqual(state.tasks);
    expect(unchanged.events).toEqual(state.events);
    expect(unchanged.photos).toHaveLength(1);
    expect(await unchanged.photos[0].blob.text()).toBe('durable bytes');
    const retried = await service.execute(reset);
    expect(retried.template.name).toBe('机柜例行巡检');
    expect(retried.tasks).toHaveLength(0);
    expect(retried.photos).toHaveLength(0);
    expect(retried.events).toHaveLength(0);
  });
});
