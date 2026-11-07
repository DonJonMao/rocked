import { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Download, FileCheck2, Printer } from 'lucide-react';
import type { AppState, Task } from '../domain/types';
import { exportRecord, stats } from '../services/report';
import { Photo } from './Photo';
import { ReadingSummary } from './ReadingFields';
import { resultLabel } from '../domain/readings';
import './features.css';

const time = (value?: string) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

export function Report({ state, task, onClose }: { state: AppState; task: Task; onClose: () => void }) {
  const root = useRef<HTMLDivElement>(null);
  const downloads = useRef(new Map<string, ReturnType<typeof setTimeout>>());
  const [printing, setPrinting] = useState(false);
  const [error, setError] = useState('');
  const totals = stats(task);
  const abnormal = task.stepRecords.filter(record => record.status === 'completed' && record.draft.result === 'abnormal');
  const events = state.events.filter(event => event.taskId === task.id);
  useEffect(() => {
    const resources = downloads.current;
    return () => { resources.forEach((timer, url) => { clearTimeout(timer); URL.revokeObjectURL(url); }); resources.clear(); };
  }, []);
  async function print() {
    setPrinting(true); setError('');
    try {
      const images = Array.from(root.current?.querySelectorAll('img') ?? []);
      await Promise.all(images.map(async image => { if (typeof image.decode === 'function') await image.decode(); else if (!image.complete) await new Promise<void>((resolve, reject) => { image.onload = () => resolve(); image.onerror = () => reject(new Error('图片加载失败')); }); if (!image.naturalWidth) throw new Error('图片加载失败'); }));
      window.print();
    } catch { setError('报告图片尚未加载完成，请稍后重试打印。'); }
    finally { setPrinting(false); }
  }
  function download() {
    const blob = new Blob([JSON.stringify(exportRecord(state, task), null, 2)], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = `巡检记录-${task.assetSnapshot.code}-${task.id.slice(0, 8)}.json`;
    anchor.click();
    // The URL belongs only to this download operation and is never exported.
    downloads.current.set(url, setTimeout(() => { URL.revokeObjectURL(url); downloads.current.delete(url); }, 1000));
  }
  return <section className="report-view" data-testid="inspection-report"><div className="report-toolbar no-print"><button type="button" className="button secondary" onClick={onClose}><ArrowLeft size={17}/>返回工作台</button><div className="button-row"><button type="button" className="button secondary" onClick={download} disabled={task.status !== 'completed'}><Download size={17}/>导出 JSON</button><button type="button" className="button primary" onClick={() => void print()} disabled={printing || task.status !== 'completed'}><Printer size={17}/>{printing ? '正在准备图片…' : '打印报告'}</button></div></div>{error && <p className="error no-print" role="alert">{error}</p>}
    <div ref={root} className="report-paper"><header className="report-heading"><div><span className="eyebrow">INSPECTION REPORT · 本地巡检记录</span><h1>设备巡检报告</h1><p>{task.templateSnapshot.name} · 模板 v{task.templateSnapshot.version}</p></div><div className={`report-result ${abnormal.length ? 'has-abnormal' : ''}`}><FileCheck2 size={28}/><strong>{task.status === 'completed' ? '已完成' : '进行中'}</strong><span>{abnormal.length ? '发现异常' : task.status === 'completed' ? '未发现异常' : '待完成检查'}</span></div></header>
      <dl className="report-meta"><div><dt>巡检资产</dt><dd>{task.assetSnapshot.name}</dd></div><div><dt>资产编号</dt><dd>{task.assetSnapshot.code}</dd></div><div><dt>所在位置</dt><dd>{task.assetSnapshot.location}</dd></div><div><dt>执行人员</dt><dd>{task.assigneeSnapshot.name}</dd></div><div><dt>下发时间</dt><dd>{time(task.createdAt)}</dd></div><div><dt>开始时间</dt><dd>{time(task.startedAt)}</dd></div><div><dt>完成时间</dt><dd>{time(task.completedAt)}</dd></div><div><dt>任务编号</dt><dd className="task-id">{task.id}</dd></div></dl>
      {task.schedule && <dl className="report-meta"><div><dt>巡检日期</dt><dd>{task.schedule.date}</dd></div><div><dt>计划时段</dt><dd>{task.schedule.time}</dd></div><div><dt>班次</dt><dd>{task.schedule.shift}</dd></div>{task.templateSnapshot.source?.page === 1 && <div><dt>值班工程师</dt><dd>{task.schedule.engineer || '未填写'}</dd></div>}</dl>}
      {task.templateSnapshot.source && <p className="source-note">依据：{task.templateSnapshot.source.file} · 第 {task.templateSnapshot.source.page} 页 · {task.templateSnapshot.source.section}。本报告为单个对象、单个时段的执行记录；设备码为演示编号。</p>}
      <div className="report-statline"><span>完成步骤 <strong>{totals.completed} / {totals.total}</strong></span><span>现场照片 <strong>{totals.photos} 张</strong></span><span>异常记录 <strong>{totals.abnormal} 项</strong></span><span>任务进度 <strong>{totals.percent}%</strong></span></div>
      <section className={`abnormal-summary ${abnormal.length ? 'has-abnormal' : ''}`}><h2>异常摘要</h2>{abnormal.length ? <ul>{abnormal.map(record => <li key={record.stepId}><strong>{task.templateSnapshot.steps.find(step => step.id === record.stepId)?.title}</strong><p>{record.draft.note}</p></li>)}</ul> : <p>已完成检查项中未登记异常。</p>}</section>
      <h2 className="report-section-title">逐步检查记录</h2><div className="report-steps">{task.templateSnapshot.steps.map((step, index) => {
        const record = task.stepRecords.find(item => item.stepId === step.id)!;
        const photos = state.photos.filter(photo => photo.taskId === task.id && photo.stepId === step.id && record.photoIds.includes(photo.id));
        return <article key={step.id} className="report-step"><header><span className="step-number">{String(index + 1).padStart(2, '0')}</span><h3>{step.title}</h3><span className={`badge ${record.draft.result === 'abnormal' ? 'warning' : record.status === 'completed' ? 'success' : ''}`}>{record.status !== 'completed' ? '未完成' : resultLabel(record.draft.result)}</span></header><p className="muted">{step.instruction}</p>{step.type === 'asset_check' && <p>核对资产码：{record.draft.assetCode || '—'} · 人员确认：{record.draft.confirmed ? '已确认' : '未确认'}</p>}{step.type === 'review_submit' && <p>记录核对：{record.draft.confirmed ? '已核对并提交' : '尚未提交'}</p>}{record.draft.note && <p className={record.draft.result === 'abnormal' ? 'abnormal-note' : ''}>检查说明：{record.draft.note}</p>}<ReadingSummary step={step} draft={record.draft}/>{record.draft.faultTime && <p>故障发现时间：{record.draft.faultTime}</p>}<div className="report-photo-grid">{photos.map(photo => <Photo key={photo.id} photo={photo}/>)}</div><p className="step-time">确认时间：{time(record.confirmedAt)} · 保存照片 {photos.length} 张</p></article>;
      })}</div>
      <section className="report-audit"><h2>操作时间线</h2><ol>{events.map(event => <li key={event.id}><time>{time(event.occurredAt)}</time><span>{event.summary}</span></li>)}</ol></section>
      <footer className="report-footnote">本报告由当前浏览器中的实际巡检记录生成。标注“演示素材”的图片为本地示意图；检查结果由执行人员确认。数据已本地保存。</footer>
    </div>
  </section>;
}
