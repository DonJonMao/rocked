import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import { createInitialState, migrateCatalog } from '../data/seed';
import type { AppState, AuditEvent, EvidencePhoto, Task } from '../domain/types';

type Configuration = Omit<AppState, 'tasks' | 'photos' | 'events'>;
interface InspectionDatabase extends DBSchema {
  configuration: { key: string; value: Configuration };
  tasks: { key: string; value: Task };
  photos: { key: string; value: EvidencePhoto; indexes: { taskId: string } };
  events: { key: string; value: AuditEvent; indexes: { taskId: string } };
}
const stores = ['configuration', 'tasks', 'photos', 'events'] as const;
export const DATABASE_NAME = 'wearable-inspection-demo';

export interface StateRepository {
  load(): Promise<AppState>;
  mutate(transition: (state: AppState) => AppState): Promise<AppState>;
}

export class StorageError extends Error {
  constructor(cause: unknown) {
    super(`保存失败，当前步骤未推进。请检查浏览器存储空间后重试。${cause instanceof Error ? `（${cause.message}）` : ''}`);
    this.name = 'StorageError';
  }
}

/** Data-access boundary: replace this adapter with an HTTP implementation in a future deployment. */
export class IndexedDbRepository implements StateRepository {
  private opening?: Promise<IDBPDatabase<InspectionDatabase>>;

  constructor(private readonly name = DATABASE_NAME) {}

  private database(): Promise<IDBPDatabase<InspectionDatabase>> {
    if (!this.opening) {
      this.opening = openDB<InspectionDatabase>(this.name, 1, {
        upgrade(db) {
          db.createObjectStore('configuration');
          db.createObjectStore('tasks', { keyPath: 'id' });
          db.createObjectStore('photos', { keyPath: 'id' }).createIndex('taskId', 'taskId');
          db.createObjectStore('events', { keyPath: 'id' }).createIndex('taskId', 'taskId');
        },
        blocking: () => { void this.close(); },
      }).catch((error: unknown) => { this.opening = undefined; throw error; });
    }
    return this.opening;
  }

  async load(): Promise<AppState> {
    return this.mutate((state) => state);
  }

  async mutate(transition: (state: AppState) => AppState): Promise<AppState> {
    const db = await this.database();
    // Reading, checking guards, updating associations and writing events share one transaction.
    // IndexedDB also serializes this across tabs connected to the same local database.
    const tx = db.transaction(stores, 'readwrite');
    const done = tx.done;
    // An early handler prevents an aborted transaction from becoming an unhandled rejection.
    void done.catch(() => undefined);
    let previous: AppState;
    let isNew: boolean;
    try {
      const [configuration, tasks, photos, events] = await Promise.all([
        tx.objectStore('configuration').get('state'),
        tx.objectStore('tasks').getAll(),
        tx.objectStore('photos').getAll(),
        tx.objectStore('events').getAll(),
      ]);
      isNew = !configuration;
      previous = configuration ? { ...configuration, tasks, photos, events } : createInitialState();
      // getAll follows primary-key order; presentation and persisted arrays use chronological order.
      previous.tasks.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
      previous.events.sort((a, b) => (a.sequence ?? 0) - (b.sequence ?? 0) || a.occurredAt.localeCompare(b.occurredAt));
      previous.photos.sort((a, b) => a.addedAt.localeCompare(b.addedAt));
    } catch (error) {
      try { tx.abort(); } catch { /* The browser may already have aborted. */ }
      await done.catch(() => undefined);
      throw new StorageError(error);
    }
    let next: AppState;
    try { next = transition(migrateCatalog(previous)); }
    catch (error) {
      tx.abort();
      await done.catch(() => undefined);
      throw error;
    }
    try {
      if (next !== previous || isNew) {
        const { tasks, photos, events, ...configuration } = next;
        await tx.objectStore('configuration').put(configuration, 'state');
        // Unchanged Blob values need no rewrites. Deletions and associated task changes stay atomic.
        for (const [storeName, entries, oldEntries] of [
          ['tasks', tasks, previous.tasks],
          ['photos', photos, previous.photos],
          ['events', events, previous.events],
        ] as const) {
          const currentIds = new Set(entries.map((entry) => entry.id));
          for (const entry of oldEntries) if (!currentIds.has(entry.id)) await tx.objectStore(storeName).delete(entry.id);
          const oldById = new Map<string, Task | EvidencePhoto | AuditEvent>(oldEntries.map((entry) => [entry.id, entry]));
          for (const entry of entries) if (oldById.get(entry.id) !== entry) await tx.objectStore(storeName).put(entry);
        }
      }
      await done;
      return next;
    } catch (error) {
      try { tx.abort(); } catch { /* Preserve the first write failure. */ }
      await done.catch(() => undefined);
      throw new StorageError(error);
    }
  }

  async close(): Promise<void> {
    const opening = this.opening;
    this.opening = undefined;
    if (opening) (await opening).close();
  }
}
