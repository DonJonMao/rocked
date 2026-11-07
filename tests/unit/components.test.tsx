import { Blob as NodeBlob } from 'node:buffer';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Modal } from '../../src/features/Modal';
import { Photo } from '../../src/features/Photo';
import { TemplateEditor } from '../../src/features/TemplateEditor';
import { CaptureDialog } from '../../src/features/CaptureDialog';
import { Report } from '../../src/features/Report';
import { validateImage } from '../../src/features/captureImage';
import { createInitialState } from '../../src/data/seed';
import { applyCommand } from '../../src/domain/commands';
import type { EvidencePhoto } from '../../src/domain/types';

function assigned() {
  const seed = createInitialState();
  return applyCommand(seed, { type: 'dispatch', commandId: 'test-dispatch', assetId: seed.assets[0].id, assigneeId: seed.assignees[0].id });
}
const photo: EvidencePhoto = { id: 'p1', taskId: 'task-test-dispatch', stepId: 'check-cabinet', source: 'fixture', isDemo: true, blob: new Blob(['photo'], { type: 'image/png' }), width: 960, height: 720, name: '机柜外观（演示素材）.png', addedAt: '2026-10-28T08:00:00.000Z' };

beforeEach(() => {
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:local-preview') });
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() });
});
afterEach(() => { cleanup(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });

describe('reusable inspection components', () => {
  it('closes native dialog through cancel and restores focus', () => {
    const previous = document.createElement('button'); document.body.append(previous); previous.focus();
    const onClose = vi.fn();
    const { unmount } = render(<Modal title="测试弹层" onClose={onClose}><input aria-label="测试字段"/></Modal>);
    const dialog = screen.getByRole('dialog', { name: '测试弹层' });
    fireEvent(dialog, new Event('cancel', { cancelable: true }));
    expect(onClose).toHaveBeenCalledTimes(1);
    unmount();
    expect(document.activeElement).toBe(previous);
    previous.remove();
  });

  it('wraps Tab in both directions and skips disabled or nonvisible controls', () => {
    vi.spyOn(HTMLElement.prototype, 'getClientRects').mockImplementation(function (this: HTMLElement) { return { length: this.dataset.nonvisible ? 0 : 1 } as DOMRectList; });
    render(<Modal title="焦点约束" onClose={vi.fn()}><input aria-label="照片名称"/><button disabled>不可用</button><button data-nonvisible>不可见</button><button>最后一个操作</button></Modal>);
    const close = screen.getByRole('button', { name: '关闭弹窗' });
    const last = screen.getByRole('button', { name: '最后一个操作' });
    expect(document.activeElement).toBe(close);
    fireEvent.keyDown(close, { key: 'Tab', shiftKey: true });
    expect(document.activeElement).toBe(last);
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(document.activeElement).toBe(close);
    fireEvent.keyDown(close, { key: 'Tab' });
    expect(document.activeElement).toBe(screen.getByRole('textbox', { name: '照片名称' }));
    fireEvent.keyDown(document.activeElement!, { key: 'Tab' });
    expect(document.activeElement).toBe(last);
  });

  it('creates previews from Blobs, labels fixtures and releases object URLs', () => {
    const onOpen = vi.fn();
    const { unmount } = render(<Photo photo={photo} onOpen={onOpen}/>);
    expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:local-preview');
    expect(screen.getByText('演示素材 · 已保存')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: `查看照片：${photo.name}` }));
    expect(onOpen).toHaveBeenCalledOnce();
    unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:local-preview');
  });

  it('rejects empty template fields and preserves fixed endpoint order when reordering', async () => {
    const template = createInitialState().template;
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<TemplateEditor template={template} busy={false} onSave={onSave}/>);
    fireEvent.change(screen.getByLabelText('模板名称'), { target: { value: '' } });
    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('请填写模板名称'));
    expect(onSave).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('模板名称'), { target: { value: '新巡检模板' } });
    fireEvent.click(screen.getByRole('button', { name: '上移第 3 步' }));
    fireEvent.click(screen.getByRole('button', { name: '新增检查项' }));
    fireEvent.click(screen.getByRole('button', { name: '保存模板' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledOnce());
    const saved = onSave.mock.calls[0][0];
    expect(saved.steps[0].id).toBe('verify-asset');
    expect(saved.steps[1].id).toBe('check-indicators');
    expect(saved.steps.at(-1).id).toBe('submit-report');
    expect(saved.steps).toHaveLength(6);
    expect(template.steps).toHaveLength(5);
  });

  it('shows camera refusal without disabling fixture and file fallback', async () => {
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockRejectedValue(new DOMException('denied', 'NotAllowedError')) } });
    const state = assigned(); const task = state.tasks[0];
    render(<CaptureDialog task={task} step={task.templateSnapshot.steps[1]} onClose={vi.fn()} onSave={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button', { name: '开启摄像头' }));
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('无法访问摄像头'));
    expect(screen.getByRole('button', { name: '使用演示图片' })).toBeEnabled();
    expect(screen.getByLabelText('选择本地图片')).toBeEnabled();
    expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledOnce();
  });

  it('stops a camera stream that resolves after the capture dialog closes', async () => {
    let resolve!: (value: MediaStream) => void;
    const stop = vi.fn();
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn(() => new Promise<MediaStream>(value => { resolve = value; })) } });
    const state = assigned(); const task = state.tasks[0];
    const { unmount } = render(<CaptureDialog task={task} step={task.templateSnapshot.steps[1]} onClose={vi.fn()} onSave={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button', { name: '开启摄像头' }));
    unmount();
    resolve({ getTracks: () => [{ stop }] } as unknown as MediaStream);
    await waitFor(() => expect(stop).toHaveBeenCalledOnce());
  });

  it('stops active camera tracks when leaving the preview', async () => {
    const stop = vi.fn();
    vi.spyOn(HTMLMediaElement.prototype, 'play').mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop }] }) } });
    const state = assigned(); const task = state.tasks[0];
    const { unmount } = render(<CaptureDialog task={task} step={task.templateSnapshot.steps[1]} onClose={vi.fn()} onSave={vi.fn()}/>);
    fireEvent.click(screen.getByRole('button', { name: '开启摄像头' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '摄像头已开启' })).toBeDisabled());
    unmount();
    expect(stop).toHaveBeenCalledOnce();
  });

  it('renders archived step records as read-only and separates completion from abnormal results', () => {
    const state = assigned(); const task = state.tasks[0];
    task.status = 'completed'; task.completedAt = '2026-10-28T08:00:00.000Z';
    task.stepRecords.forEach(record => { record.status = 'completed'; record.draft.confirmed = true; record.confirmedAt = task.completedAt; });
    task.stepRecords[1].draft = { result: 'abnormal', note: '柜门松动' };
    task.stepRecords[1].photoIds = [photo.id]; state.photos = [photo];
    render(<Report state={state} task={task} onClose={vi.fn()}/>);
    expect(screen.getByText('已完成')).toBeInTheDocument();
    expect(screen.getByText('发现异常')).toBeInTheDocument();
    expect(screen.getByText('柜门松动')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /删除/ })).not.toBeInTheDocument();
    expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:local-preview');
  });
});

describe('uploaded image boundary', () => {
  it('rejects SVG and oversized images before decode', async () => {
    await expect(validateImage(new NodeBlob(['<svg/>'], { type: 'image/svg+xml' }) as unknown as Blob)).rejects.toThrow('仅支持 JPEG、PNG、WebP');
    await expect(validateImage(new NodeBlob([new Uint8Array(10 * 1024 * 1024 + 1)], { type: 'image/png' }) as unknown as Blob)).rejects.toThrow('不能超过 10MB');
  });
  it('rejects an SVG disguised with an allowed MIME type', async () => {
    await expect(validateImage(new NodeBlob(['<svg xmlns="http://www.w3.org/2000/svg"/>'], { type: 'image/png' }) as unknown as Blob)).rejects.toThrow('内容与格式不符');
  });
});
