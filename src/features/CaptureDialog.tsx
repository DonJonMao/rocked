import { useEffect, useRef, useState } from 'react';
import { Camera, ImagePlus, Upload, VideoOff } from 'lucide-react';
import type { EvidencePhoto, StepDefinition, Task } from '../domain/types';
import { Modal } from './Modal';
import { canvasBlob, fixtures, loadFixture, validateImage } from './captureImage';

export function CaptureDialog({ task, step, onClose, onSave }: { task: Task; step: StepDefinition; onClose: () => void; onSave: (photo: EvidencePhoto) => Promise<void> }) {
  const initial = step.id.includes('indicator') ? 'indicators' : step.id.includes('panel') ? 'panel' : 'cabinet';
  const [selected, setSelected] = useState<string>(initial);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const [retryPhoto, setRetryPhoto] = useState<EvidencePhoto | null>(null);
  const [camera, setCamera] = useState<'off' | 'requesting' | 'ready'>('off');
  const video = useRef<HTMLVideoElement>(null);
  const stream = useRef<MediaStream | null>(null);
  const alive = useRef(true);
  const request = useRef({ generation: 0 });
  const saveLock = useRef(false);
  const timeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  function stopCamera() {
    request.current.generation++;
    clearTimeout(timeout.current);
    stream.current?.getTracks().forEach(track => track.stop());
    stream.current = null;
    if (video.current) video.current.srcObject = null;
    if (alive.current) setCamera('off');
  }
  useEffect(() => {
    alive.current = true;
    const requestToken = request.current;
    return () => {
      alive.current = false;
      requestToken.generation++;
      clearTimeout(timeout.current);
      stream.current?.getTracks().forEach(track => track.stop());
      stream.current = null;
    };
  }, []);

  async function save(photo: EvidencePhoto) {
    if (!alive.current) return;
    try {
      await onSave(photo);
      if (alive.current) { stopCamera(); onClose(); }
    } catch (reason) {
      if (alive.current) { setRetryPhoto(photo); setError(reason instanceof Error ? reason.message : '照片保存失败，请重试。'); }
    }
  }
  async function run(operation: () => Promise<EvidencePhoto>) {
    if (saveLock.current) return;
    saveLock.current = true;
    setSaving(true); setError(''); setRetryPhoto(null);
    try { const photo = await operation(); await save(photo); }
    catch (reason) { if (alive.current) setError(reason instanceof Error ? reason.message : '图片处理失败，请重试。'); }
    finally { saveLock.current = false; if (alive.current) setSaving(false); }
  }
  function evidence(data: { blob: Blob; width: number; height: number; name: string }, source: EvidencePhoto['source']): EvidencePhoto {
    return { ...data, id: crypto.randomUUID(), taskId: task.id, stepId: step.id, source, isDemo: source === 'fixture', addedAt: new Date().toISOString() };
  }
  async function startCamera() {
    stopCamera();
    if (!navigator.mediaDevices?.getUserMedia) { setError('摄像头不可用，请使用演示图片或选择本地图片。'); return; }
    const token = ++request.current.generation;
    setCamera('requesting'); setError('');
    timeout.current = setTimeout(() => {
      if (!alive.current || request.current.generation !== token) return;
      request.current.generation++;
      setCamera('off');
      setError('摄像头暂未响应。您可以使用演示图片或选择本地图片。');
    }, 12000);
    try {
      const media = await navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' }, audio: false });
      if (!alive.current || request.current.generation !== token) { media.getTracks().forEach(track => track.stop()); return; }
      clearTimeout(timeout.current);
      stream.current = media;
      setCamera('ready');
      if (video.current) { video.current.srcObject = media; await video.current.play(); }
    } catch {
      if (!alive.current || request.current.generation !== token) return;
      stopCamera();
      setError('无法访问摄像头，可能未授权或未连接设备。请使用演示图片或选择本地图片。');
    }
  }
  return <Modal title="采集巡检照片" onClose={onClose} wide>
    <p className="capture-context">{task.assetSnapshot.name} · {step.title}<span className="muted">照片将保存到当前任务的本步骤</span></p>
    <section className="capture-section"><h3><ImagePlus size={18}/>本地演示素材</h3><p className="muted">本地示意图，用于演示操作流程，不代表真实设备状态。</p>
      <div className="fixture-grid">{fixtures.map(item => <button key={item.id} type="button" className={`fixture-option ${selected === item.id ? 'selected' : ''}`} aria-label={item.name} aria-pressed={selected === item.id} onClick={() => setSelected(item.id)} disabled={saving}><img src={item.url} alt={`${item.name}演示素材`}/><span>{item.name}</span></button>)}</div>
      <button type="button" className="primary button" disabled={saving} onClick={() => void run(async () => evidence(await loadFixture(selected), 'fixture'))}><ImagePlus size={17}/>{saving ? '正在保存…' : '使用演示图片'}</button>
    </section>
    <div className="capture-alternatives"><section className="capture-section"><h3><Upload size={18}/>上传图片</h3><p className="muted">JPEG / PNG / WebP，单张不超过 10MB。</p><label className={`button secondary upload-label ${saving ? 'disabled' : ''}`}><Upload size={17}/>选择本地图片<input type="file" aria-label="选择本地图片" accept="image/jpeg,image/png,image/webp" disabled={saving} onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) void run(async () => evidence({ blob: file, ...await validateImage(file), name: file.name }, 'upload')); }}/></label></section>
      <section className="capture-section"><h3><Camera size={18}/>电脑摄像头</h3><p className="muted">点击后请求权限，仅用于本次拍照，可随时关闭。</p><button type="button" className="button secondary" disabled={saving || camera !== 'off'} onClick={() => void startCamera()}><Camera size={17}/>{camera === 'requesting' ? '等待摄像头授权…' : camera === 'ready' ? '摄像头已开启' : '开启摄像头'}</button></section></div>
    <div className={`camera-preview ${camera === 'ready' ? 'visible' : ''}`}><span>电脑摄像头预览</span><video ref={video} muted playsInline autoPlay/><div className="button-row"><button type="button" className="button primary" disabled={saving || camera !== 'ready'} onClick={() => void run(async () => { const element = video.current; if (!element?.videoWidth || !element.videoHeight) throw new Error('摄像头画面尚未就绪，请稍后再拍。'); const canvas = document.createElement('canvas'); const scale = Math.min(1, 1600 / Math.max(element.videoWidth, element.videoHeight)); canvas.width = Math.round(element.videoWidth * scale); canvas.height = Math.round(element.videoHeight * scale); const context = canvas.getContext('2d'); if (!context) throw new Error('浏览器无法处理相机画面。'); context.drawImage(element, 0, 0, canvas.width, canvas.height); return evidence({ blob: await canvasBlob(canvas, 'image/jpeg'), width: canvas.width, height: canvas.height, name: '现场拍照.jpg' }, 'camera'); })}><Camera size={17}/>拍照并保存</button><button type="button" className="button secondary" onClick={stopCamera}><VideoOff size={17}/>关闭摄像头</button></div></div>
    {error && <div className="error capture-error" role="alert">{error}{retryPhoto && <button type="button" className="button secondary" disabled={saving} onClick={() => void run(async () => retryPhoto)}>重试保存照片</button>}</div>}
  </Modal>;
}
