import { createInitialState } from '../data/seed';
import type { AppState, AuditEvent, Command, Draft, StepDefinition, StepRecord, Task, Template } from './types';
import { allowedResults, isInactive, readingErrors, resultLabel } from './readings';

export function validateTemplate(template: Template): string[] {
  const errors: string[] = [];
  if (!template.name.trim()) errors.push('请填写模板名称');
  if (!Number.isInteger(template.version) || template.version < 1) errors.push('版本号必须为正整数');
  if (template.steps.length < 3) errors.push('至少保留一个内部检查项');
  if (template.steps[0]?.type !== 'asset_check' || template.steps.at(-1)?.type !== 'review_submit') {
    errors.push('设备确认必须位于首项，提交记录必须位于末项');
  }
  if (template.steps.slice(1, -1).some((step) => step.type !== 'photo_check')) errors.push('内部步骤必须是检查项');
  const ids = new Set<string>();
  template.steps.forEach((step, index) => {
    if (!step.id.trim() || ids.has(step.id)) errors.push(`第 ${index + 1} 步的步骤 ID 为空或重复`);
    ids.add(step.id);
    const fieldIds = new Set<string>();
    for (const field of step.fields ?? []) {
      if (!field.id || fieldIds.has(field.id) || !field.label.trim()) errors.push(`第 ${index + 1} 步的读数字段无效或重复`);
      fieldIds.add(field.id);
      if (!['number', 'select', 'text'].includes(field.kind)) errors.push('读数字段类型无效');
      if (field.kind === 'select' && !field.options?.length) errors.push('请选择字段的可用选项');
      if ((field.min !== undefined && !Number.isFinite(field.min)) || (field.max !== undefined && !Number.isFinite(field.max)) || (field.min !== undefined && field.max !== undefined && field.min > field.max)) errors.push('读数范围无效');
    }
    if (!step.title.trim()) errors.push(`请填写第 ${index + 1} 步的标题`);
    if (!Number.isInteger(step.requirements.minPhotos) || step.requirements.minPhotos < 0) {
      errors.push(`第 ${index + 1} 步的照片数量必须为非负整数`);
    }
  });
  return errors;
}

export function validateStep(task: Task, step: StepDefinition, record: StepRecord): string[] {
  const errors: string[] = [];
  if (step.type === 'asset_check') {
    if (record.draft.assetCode?.trim() !== task.assetSnapshot.code) {
      errors.push(`资产码不匹配，请核对目标设备 ${task.assetSnapshot.name}（${task.assetSnapshot.code}）`);
    }
    if (!record.draft.confirmed) errors.push('请确认设备名称和位置一致');
  }
  if (record.photoIds.length < step.requirements.minPhotos) {
    errors.push(`请先拍摄或选择至少 ${step.requirements.minPhotos} 张照片`);
  }
  if (step.requirements.resultRequired && !record.draft.result) errors.push('请选择检查结果');
  if (record.draft.result === 'abnormal' && !record.draft.note?.trim()) errors.push('请填写异常说明');
  if (record.draft.result && !allowedResults(task.templateSnapshot).includes(record.draft.result)) errors.push('该模板不支持此检查状态');
  if (isInactive(record.draft.result) && !record.draft.note?.trim()) errors.push('请说明关机、待机或不涉及的原因');
  errors.push(...readingErrors(step, record.draft));
  if (step.type === 'review_submit') {
    if (task.stepRecords.slice(0, -1).some((entry) => entry.status !== 'completed')) errors.push('请先完成前面的全部步骤');
    if (!record.draft.confirmed) errors.push('请勾选“我已核对以上记录”');
  }
  return errors;
}

function checkDraft(draft: Draft): void {
  if (draft.result !== undefined && !['normal', 'abnormal', 'shutdown', 'standby', 'not_applicable'].includes(draft.result)) throw new Error('检查结果无效，只能选择正常或异常等约定状态');
  if (draft.note !== undefined && typeof draft.note !== 'string') throw new Error('异常说明格式无效');
  if (draft.assetCode !== undefined && typeof draft.assetCode !== 'string') throw new Error('资产码格式无效');
  if (draft.readings !== undefined && (!draft.readings || typeof draft.readings !== 'object' || Array.isArray(draft.readings) || Object.values(draft.readings).some(value => typeof value !== 'string'))) throw new Error('读数格式无效');
  if (draft.faultTime !== undefined && (typeof draft.faultTime !== 'string' || (draft.faultTime && !/^([01]\d|2[0-3]):[0-5]\d$/.test(draft.faultTime)))) throw new Error('故障时间格式无效');
}

function assertCurrent(task: Task, expectedStepId: string): { step: StepDefinition; record: StepRecord; index: number } {
  if (task.status !== 'running') throw new Error('任务尚未开始、已暂停或已完成，不能修改记录');
  if (task.currentStepId !== expectedStepId) throw new Error('该操作已过期，当前步骤已改变，请查看当前步骤');
  const index = task.templateSnapshot.steps.findIndex((step) => step.id === expectedStepId);
  const record = task.stepRecords[index];
  if (!record || record.status !== 'current') throw new Error('只有当前未完成步骤可以提交，历史记录只读');
  return { step: task.templateSnapshot.steps[index], record, index };
}

/** Pure transition: all guards run before any state is returned for persistence. */
export function applyCommand(state: AppState, command: Command, now: string = new Date().toISOString()): AppState {
  if (!command.commandId.trim()) throw new Error('操作缺少唯一编号，请重试');
  if (state.processedCommandIds.includes(command.commandId)) return state;
  if (command.type === 'reset') return { ...createInitialState(), processedCommandIds: [command.commandId] };
  const next: AppState = { ...state, processedCommandIds: [...state.processedCommandIds, command.commandId] };
  if (command.type === 'saveTemplate') {
    const errors = validateTemplate(command.template);
    if (errors.length) throw new Error(errors.join('\n'));
    const previousTemplate = state.templates.find(item => item.id === command.template.id);
    if (!previousTemplate) throw new Error('模板不存在');
    const saved = structuredClone({ ...command.template, version: Math.max(previousTemplate.version + 1, command.template.version) });
    next.templates = state.templates.map(item => item.id === saved.id ? saved : item);
    if (saved.id === state.template.id) next.template = saved;
    return next;
  }
  const event = (task: Task, type: string, summary: string, stepId?: string): void => {
    const entry: AuditEvent = { id: `event-${command.commandId}`, commandId: command.commandId, taskId: task.id, stepId, type, occurredAt: now, summary, sequence: state.events.length };
    next.events = [...state.events, entry];
  };
  if (command.type === 'dispatch') {
    if (state.tasks.some((task) => task.status !== 'completed')) throw new Error('请先完成当前任务，再下发新任务');
    const asset = state.assets.find((entry) => entry.id === command.assetId);
    const assignee = state.assignees.find((entry) => entry.id === command.assigneeId);
    if (!asset || !assignee) throw new Error('请选择有效的目标设备和执行人');
    const template = command.templateId ? state.templates.find(item => item.id === command.templateId) : state.template;
    if (!template) throw new Error('请选择有效的巡检模板');
    if (template.assetIds && !template.assetIds.includes(command.assetId)) throw new Error('目标设备不属于所选巡检模板');
    if (template.source) {
      const schedule = command.schedule;
      if (!schedule || !/^\d{4}-\d{2}-\d{2}$/.test(schedule.date) || Number.isNaN(Date.parse(schedule.date)) || new Date(schedule.date).toISOString().slice(0, 10) !== schedule.date || !template.timeSlots?.includes(schedule.time) || !['白班', '夜班'].includes(schedule.shift)) throw new Error('请填写有效的巡检日期、时段和班次');
    }
    const templateErrors = validateTemplate(template);
    if (templateErrors.length) throw new Error(templateErrors.join('\n'));
    const task: Task = {
      id: `task-${command.commandId}`, templateSnapshot: structuredClone(template),
      assetSnapshot: { ...asset }, assigneeSnapshot: { ...assignee }, status: 'assigned',
      currentStepId: template.steps[0].id,
      stepRecords: template.steps.map((step, index) => ({ stepId: step.id, status: index === 0 ? 'current' : 'locked', draft: {}, photoIds: [] })),
      schedule: template.source ? structuredClone(command.schedule) : undefined,
      createdAt: now, revision: 1,
    };
    next.tasks = [...state.tasks, task];
    event(task, 'assigned', `已下发（演示）：${asset.name} · ${assignee.name}`);
    return next;
  }
  const previous = state.tasks.find((task) => task.id === command.taskId);
  if (!previous) throw new Error('任务已不存在，采集或操作已取消');
  const task: Task = { ...previous, revision: previous.revision + 1, stepRecords: previous.stepRecords.map((record) => ({ ...record, draft: { ...record.draft }, photoIds: [...record.photoIds] })) };
  next.tasks = state.tasks.map((entry) => entry.id === task.id ? task : entry);
  if (command.type === 'start') {
    if (task.status !== 'assigned') throw new Error('任务已开始或已完成，不能重复开始');
    task.status = 'running';
    task.startedAt = now;
    event(task, 'started', '开始巡检', task.currentStepId);
  } else if (command.type === 'pause') {
    if (task.status !== 'running') throw new Error('只有执行中的任务可以暂停');
    task.status = 'paused';
    event(task, 'paused', '暂停巡检，当前草稿与照片已本地保存', task.currentStepId);
  } else if (command.type === 'resume') {
    if (task.status !== 'paused') throw new Error('只有已暂停的任务可以继续');
    task.status = 'running';
    event(task, 'resumed', '继续巡检，恢复原步骤', task.currentStepId);
  } else {
    const { step, record, index } = assertCurrent(task, command.expectedStepId);
    if (command.type === 'draft') {
      checkDraft(command.draft);
      record.draft = { ...record.draft, ...command.draft };
    } else if (command.type === 'addPhoto') {
      const photo = command.photo;
      if (photo.taskId !== task.id || photo.stepId !== step.id) throw new Error('图片与采集时的任务或步骤不一致，已取消保存');
      if (state.photos.some((entry) => entry.id === photo.id)) throw new Error('图片编号重复，请重新采集');
      if (!photo.blob || photo.blob.size === 0 || photo.blob.size > 10 * 1024 * 1024) throw new Error('图片不能为空，大小不能超过 10MB');
      if (!['image/jpeg', 'image/png', 'image/webp'].includes(photo.blob.type)) throw new Error('仅支持 JPEG、PNG、WebP 图片');
      if (!(photo.width > 0 && photo.height > 0)) throw new Error('图片无法解码，请选择有效图片');
      if (!['fixture', 'upload', 'camera'].includes(photo.source)) throw new Error('图片来源无效');
      record.photoIds.push(photo.id);
      next.photos = [...state.photos, { ...photo, isDemo: photo.source === 'fixture' }];
      event(task, 'photo_added', `已本地保存照片：${step.title}（${photo.source === 'fixture' ? '演示素材' : photo.source === 'camera' ? '电脑摄像头' : '本地上传'}）`, step.id);
    } else if (command.type === 'deletePhoto') {
      if (!record.photoIds.includes(command.photoId)) throw new Error('当前步骤不存在该照片');
      record.photoIds = record.photoIds.filter((id) => id !== command.photoId);
      next.photos = state.photos.filter((photo) => photo.id !== command.photoId);
      event(task, 'photo_deleted', `删除当前步骤照片：${step.title}`, step.id);
    } else if (command.type === 'complete') {
      if (command.draft) {
        checkDraft(command.draft);
        record.draft = { ...record.draft, ...command.draft };
      }
      // On check steps, pressing the explicit completion action is the worker's confirmation.
      if (step.type === 'photo_check') record.draft.confirmed = true;
      const errors = validateStep(task, step, record);
      const hasMissingBlob = record.photoIds.some((id) => !state.photos.some((photo) => photo.id === id && photo.taskId === task.id && photo.stepId === step.id));
      if (hasMissingBlob) errors.push('照片尚未成功保存，请重新采集后重试');
      if (errors.length) throw new Error(errors.join('\n'));
      record.status = 'completed';
      record.confirmedAt = now;
      if (index === task.stepRecords.length - 1) {
        task.status = 'completed';
        task.completedAt = now;
        event(task, 'completed', '巡检已完成，报告已生成并本地保存', step.id);
      } else {
        task.currentStepId = task.templateSnapshot.steps[index + 1].id;
        task.stepRecords[index + 1].status = 'current';
        event(task, 'step_completed', `完成：${step.title}${record.draft.result ? ` · ${resultLabel(record.draft.result)}` : ''}`, step.id);
      }
    }
  }
  return next;
}
