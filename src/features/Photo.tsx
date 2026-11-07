import { useEffect, useState } from 'react';
import type { EvidencePhoto } from '../domain/types';

export const sourceLabel = (photo: EvidencePhoto) => photo.isDemo ? '演示素材' : photo.source === 'camera' ? '电脑摄像头' : '本地上传';

export function Photo({ photo, onOpen }: { photo: EvidencePhoto; onOpen?: () => void }) {
  const [url, setUrl] = useState('');
  useEffect(() => {
    const next = URL.createObjectURL(photo.blob);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [photo.blob]);
  const content = <><img src={url || undefined} alt={photo.name} loading="eager"/><span className="photo-caption">{sourceLabel(photo)} · 已保存</span></>;
  return onOpen ? <button type="button" className="photo-preview" onClick={onOpen} aria-label={`查看照片：${photo.name}`}>{content}</button> : <figure className="photo-preview static-photo">{content}</figure>;
}
