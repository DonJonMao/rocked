import type { Task } from '../domain/types';

/**
 * JSON shape consumed by the Android phone app's task.dispatch importer.
 * Keep this mapper independent from the browser report format: a report is
 * historical evidence, while this envelope is an actionable task command.
 */
export interface AndroidTaskDispatchEnvelope {
  type: 'task.dispatch';
  version: 1;
  messageId: string;
  commandId: string;
  taskId: string;
  sentAt: string;
  payload: {
    template: {
      id: string;
      name: string;
      version: number;
      category?: string;
      description?: string;
      source?: { file: string; page: number; section: string };
      assetIds?: string[];
      timeSlots?: string[];
    };
    asset: { id: string; code: string; name: string; location: string };
    assignee: { id: string; name: string };
    steps: Array<{
      id: string;
      type: string;
      title: string;
      instruction: string;
      requirements: {
        minPhotos: number;
        resultRequired: boolean;
        abnormalNoteRequired: boolean;
        confirmationRequired: boolean;
      };
      fields?: Array<{
        id: string;
        label: string;
        kind: string;
        unit?: string;
        options?: string[];
        expected?: string;
        min?: number;
        max?: number;
        minExclusive?: boolean;
        maxExclusive?: boolean;
      }>;
    }>;
    schedule?: { date: string; timeSlot: string; shift?: string };
  };
}

function messageId(task: Task, sentAt: string, explicit?: string): string {
  if (explicit?.trim()) return explicit;
  // The fallback keeps exports usable in older WebViews without crypto.randomUUID.
  return `web-${task.id}-${sentAt.replace(/[^0-9A-Za-z]/g, '')}`;
}

/** Convert the browser task snapshot into the shared Android task.dispatch protocol. */
export function toAndroidTaskDispatch(
  task: Task,
  options: { sentAt?: string; messageId?: string } = {},
): AndroidTaskDispatchEnvelope {
  const sentAt = options.sentAt ?? new Date().toISOString();
  const template = task.templateSnapshot;
  return {
    type: 'task.dispatch',
    version: 1,
    messageId: messageId(task, sentAt, options.messageId),
    commandId: `cmd-dispatch-${task.id}`,
    taskId: task.id,
    sentAt,
    payload: {
      template: {
        id: template.id,
        name: template.name,
        version: template.version,
        ...(template.category ? { category: template.category } : {}),
        ...(template.description ? { description: template.description } : {}),
        ...(template.source ? { source: { ...template.source } } : {}),
        ...(template.assetIds ? { assetIds: [...template.assetIds] } : {}),
        ...(template.timeSlots ? { timeSlots: [...template.timeSlots] } : {}),
      },
      asset: { ...task.assetSnapshot },
      assignee: { ...task.assigneeSnapshot },
      steps: template.steps.map((step) => ({
        id: step.id,
        type: step.type,
        title: step.title,
        instruction: step.instruction,
        requirements: { ...step.requirements },
        ...(step.fields?.length ? {
          fields: step.fields.map((field) => ({
            id: field.id,
            label: field.label,
            kind: field.kind,
            ...(field.unit ? { unit: field.unit } : {}),
            ...(field.options ? { options: [...field.options] } : {}),
            ...(field.expected !== undefined ? { expected: field.expected } : {}),
            ...(field.min !== undefined ? { min: field.min } : {}),
            ...(field.max !== undefined ? { max: field.max } : {}),
            ...(field.minExclusive !== undefined ? { minExclusive: field.minExclusive } : {}),
            ...(field.maxExclusive !== undefined ? { maxExclusive: field.maxExclusive } : {}),
          })),
        } : {}),
      })),
      ...(task.schedule ? {
        schedule: {
          date: task.schedule.date,
          timeSlot: task.schedule.time,
          shift: task.schedule.shift,
        },
      } : {}),
    },
  };
}

export function toAndroidTaskDispatchJson(
  task: Task,
  options: { sentAt?: string; messageId?: string } = {},
): string {
  return JSON.stringify(toAndroidTaskDispatch(task, options), null, 2);
}
