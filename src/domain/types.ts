export type StepType = 'asset_check' | 'photo_check' | 'review_submit';
export type CheckResult = 'normal' | 'abnormal' | 'shutdown' | 'standby' | 'not_applicable';
export interface ReadingField {
  id: string;
  label: string;
  kind: 'number' | 'select' | 'text';
  unit?: string;
  options?: string[];
  min?: number;
  max?: number;
  minExclusive?: boolean;
  maxExclusive?: boolean;
  expected?: string;
}
export interface InspectionSchedule { date: string; time: string; shift: '白班' | '夜班'; engineer?: string }
export interface StepDefinition {
  id: string;
  type: StepType;
  title: string;
  instruction: string;
  fields?: ReadingField[];
  requirements: {
    minPhotos: number;
    resultRequired: boolean;
    abnormalNoteRequired: boolean;
    confirmationRequired: boolean;
  };
}
export interface Template {
  id: string;
  name: string;
  version: number;
  description?: string;
  category?: '电气' | '暖通';
  source?: { file: string; page: number; section: string };
  assetIds?: string[];
  timeSlots?: string[];
  steps: StepDefinition[];
}
export interface Asset { id: string; code: string; name: string; location: string; type: string }
export interface Assignee { id: string; name: string }
export interface Draft {
  assetCode?: string;
  confirmed?: boolean;
  result?: CheckResult;
  note?: string;
  readings?: Record<string, string>;
  faultTime?: string;
}
export interface StepRecord {
  stepId: string;
  status: 'locked' | 'current' | 'completed';
  draft: Draft;
  photoIds: string[];
  confirmedAt?: string;
}
export interface Task {
  id: string;
  templateSnapshot: Template;
  assetSnapshot: Asset;
  assigneeSnapshot: Assignee;
  status: 'assigned' | 'running' | 'paused' | 'completed';
  currentStepId: string;
  stepRecords: StepRecord[];
  createdAt: string;
  startedAt?: string;
  completedAt?: string;
  revision: number;
  schedule?: InspectionSchedule;
}
export interface EvidencePhoto {
  id: string;
  taskId: string;
  stepId: string;
  source: 'fixture' | 'upload' | 'camera';
  isDemo: boolean;
  blob: Blob;
  width: number;
  height: number;
  addedAt: string;
  name: string;
}
export interface AuditEvent {
  id: string;
  commandId: string;
  taskId: string;
  stepId?: string;
  type: string;
  occurredAt: string;
  summary: string;
  sequence?: number;
}
export interface AppState {
  template: Template;
  templates: Template[];
  assets: Asset[];
  assignees: Assignee[];
  tasks: Task[];
  photos: EvidencePhoto[];
  events: AuditEvent[];
  processedCommandIds: string[];
}
type TaskCommand = { taskId: string };
type StepCommand = TaskCommand & { expectedStepId: string };
export type Command = { commandId: string } & (
  | { type: 'dispatch'; assetId: string; assigneeId: string; templateId?: string; schedule?: InspectionSchedule }
  | ({ type: 'start' } & TaskCommand)
  | ({ type: 'pause' } & TaskCommand)
  | ({ type: 'resume' } & TaskCommand)
  | ({ type: 'draft'; draft: Draft } & StepCommand)
  | ({ type: 'complete'; draft?: Draft } & StepCommand)
  | ({ type: 'addPhoto'; photo: EvidencePhoto } & StepCommand)
  | ({ type: 'deletePhoto'; photoId: string } & StepCommand)
  | { type: 'saveTemplate'; template: Template }
  | { type: 'reset' }
);
