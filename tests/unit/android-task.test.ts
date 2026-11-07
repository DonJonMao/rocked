import { describe, expect, it } from 'vitest';
import { createInitialState } from '../../src/data/seed';
import { applyCommand } from '../../src/domain/commands';
import { toAndroidTaskDispatch, toAndroidTaskDispatchJson } from '../../src/services/androidTask';

describe('Android task.dispatch export', () => {
  it('maps a building task to the shared envelope, including readings and schedule', () => {
    const state = applyCommand(createInitialState(), {
      type: 'dispatch',
      commandId: 'android-export-1',
      templateId: 'chiller',
      assetId: 'chiller-1',
      assigneeId: 'worker-zhang',
      schedule: { date: '2026-11-06', time: '09:30', shift: '白班', engineer: '王工' },
    });
    const envelope = toAndroidTaskDispatch(state.tasks[0], { sentAt: '2026-11-06T01:00:00.000Z', messageId: 'web-message-1' });
    expect(envelope.type).toBe('task.dispatch');
    expect(envelope.taskId).toBe(state.tasks[0].id);
    expect(envelope.commandId).toBe(`cmd-dispatch-${state.tasks[0].id}`);
    expect(envelope.payload.template.source?.file).toContain('暖通');
    expect(envelope.payload.asset.code).toBe('B4-CHILLER-1');
    expect(envelope.payload.steps).toHaveLength(state.tasks[0].templateSnapshot.steps.length);
    expect(envelope.payload.steps.flatMap(step => step.fields ?? []).length).toBeGreaterThan(0);
    expect(envelope.payload.schedule).toEqual({ date: '2026-11-06', timeSlot: '09:30', shift: '白班' });
  });

  it('emits valid JSON without browser-only task fields', () => {
    const state = createInitialState();
    const stateWithTask = applyCommand(state, { type: 'dispatch', commandId: 'android-export-2', assetId: 'asset-a03', assigneeId: 'worker-zhang' });
    const parsed = JSON.parse(toAndroidTaskDispatchJson(stateWithTask.tasks[0], { sentAt: '2026-11-06T01:00:00.000Z', messageId: 'web-message-2' })) as Record<string, unknown>;
    expect(parsed).toMatchObject({ type: 'task.dispatch', version: 1, messageId: 'web-message-2' });
    expect(parsed).not.toHaveProperty('task.stepRecords');
    expect((parsed.payload as Record<string, unknown>).schedule).toBeUndefined();
  });
});
