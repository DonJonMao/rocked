import templateSeed from '../../seed/inspection-template.json';
import assetSeed from '../../seed/assets.json';
import catalog from '../../seed/building4-catalog.json';
import type { AppState, Template } from '../domain/types';

export const defaultAssetId = assetSeed.defaultAssetId;
export const defaultAssigneeId = assetSeed.defaultAssigneeId;
export const terminalLabel = assetSeed.terminalLabel;

/** Each invocation returns independent seed objects, never shared mutable defaults. */
export function createInitialState(): AppState {
  return {
    template: structuredClone(templateSeed) as Template,
    templates: structuredClone([templateSeed, ...catalog.templates]) as Template[],
    assets: structuredClone([...assetSeed.assets, ...catalog.assets]),
    assignees: structuredClone(assetSeed.assignees),
    tasks: [], photos: [], events: [], processedCommandIds: [],
  };
}

/** Add the catalog to existing browsers without resetting tasks, photos or edited templates. */
export function migrateCatalog(state: AppState): AppState {
  const defaults = createInitialState();
  const templates = state.templates ?? [state.template];
  const missingTemplates = defaults.templates.filter(item => !templates.some(saved => saved.id === item.id));
  const missingAssets = defaults.assets.filter(item => !state.assets.some(saved => saved.id === item.id));
  if (state.templates && !missingTemplates.length && !missingAssets.length) return state;
  return { ...state, templates: [...templates, ...missingTemplates], assets: [...state.assets, ...missingAssets] };
}
