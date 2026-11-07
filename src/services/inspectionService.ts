import { applyCommand } from '../domain/commands';
import type { AppState, Command } from '../domain/types';
import { IndexedDbRepository, type StateRepository } from '../repositories/indexedDb';

/** Serial command boundary shared by all three UI regions. State is published only after commit. */
export class InspectionService {
  private tail: Promise<void> = Promise.resolve();

  constructor(private readonly repository: StateRepository = new IndexedDbRepository()) {}

  init(): Promise<AppState> {
    return this.queue(() => this.repository.load());
  }

  execute(command: Command): Promise<AppState> {
    // Capture caller-owned objects so later editing cannot mutate an enqueued command.
    const captured: Command = command.type === 'addPhoto'
      ? { ...command, photo: { ...command.photo } }
      : structuredClone(command);
    return this.queue(() => this.repository.mutate((state) => applyCommand(state, captured)));
  }

  private queue(operation: () => Promise<AppState>): Promise<AppState> {
    const result = this.tail.then(operation);
    this.tail = result.then(() => undefined, () => undefined);
    return result;
  }
}

export const inspectionService = new InspectionService();
