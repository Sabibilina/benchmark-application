import type { SearchFilters } from "../../api/searchApi";

export function normalizeSearchFilters(values: Record<string, FormDataEntryValue>): SearchFilters {
  return {
    q: stringValue(values.q),
    genre: stringValue(values.genre),
    bpmMin: numberValue(values.bpmMin),
    bpmMax: numberValue(values.bpmMax),
    year: numberValue(values.year),
    page: 0,
    size: 12
  };
}

function stringValue(value: FormDataEntryValue | undefined): string | undefined {
  const text = value?.toString().trim();
  return text ? text : undefined;
}

function numberValue(value: FormDataEntryValue | undefined): number | undefined {
  const text = stringValue(value);
  if (!text) return undefined;
  const parsed = Number(text);
  return Number.isFinite(parsed) ? parsed : undefined;
}
