import { createContext, useCallback, useContext, useEffect, useReducer, type ReactNode } from 'react';
import type { AppState, Command } from '../domain/types';
import { inspectionService } from '../services/inspectionService';

interface ViewState { data: AppState | null; pending: number; error: string }
type Action = { type: 'loaded'; data: AppState } | { type: 'pending'; delta: number } | { type: 'error'; error: string };
function reducer(state: ViewState, action: Action): ViewState {
  if (action.type === 'loaded') return { ...state, data: action.data, error: '' };
  if (action.type === 'pending') return { ...state, pending: Math.max(0, state.pending + action.delta) };
  return { ...state, error: action.error };
}
const Context = createContext<{ state: AppState; pending: number; execute: (command: Command) => Promise<AppState> } | null>(null);
export function InspectionProvider({ children }: { children: ReactNode }) {
  const [view, dispatch] = useReducer(reducer, { data: null, pending: 0, error: '' });
  const initialize = useCallback(() => {
    dispatch({ type: 'error', error: '' });
    void inspectionService.init().then(data => dispatch({ type: 'loaded', data })).catch((error: Error) => dispatch({ type: 'error', error: error.message }));
  }, []);
  useEffect(initialize, [initialize]);
  const execute = useCallback(async (command: Command) => {
    dispatch({ type: 'pending', delta: 1 });
    try {
      const data = await inspectionService.execute(command);
      dispatch({ type: 'loaded', data });
      return data;
    } finally { dispatch({ type: 'pending', delta: -1 }); }
  }, []);
  if (!view.data) return <main className="loading-page"><span className="brand-mark">巡</span><h1>智能穿戴巡检工作台</h1>{view.error ? <><p role="alert">{view.error}</p><button className="primary" onClick={initialize}>重试加载</button></> : <p>正在恢复本地任务与照片…</p>}</main>;
  return <Context.Provider value={{ state: view.data, pending: view.pending, execute }}>{children}</Context.Provider>;
}
export function useInspection() {
  const context = useContext(Context);
  if (!context) throw new Error('InspectionProvider is required');
  return context;
}
