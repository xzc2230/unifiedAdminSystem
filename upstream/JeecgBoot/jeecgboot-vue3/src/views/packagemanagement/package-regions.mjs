import data from './package-country-regions.json' with { type: 'json' };
export const APP_AREA_OPTIONS = Object.freeze(data.regions.map(row => Object.freeze({...row})));
export function countryCode(value) {
  const raw = String(value ?? '').trim();
  return data.aliases[raw] || (/^[a-z]{2}$/i.test(raw) ? raw.toLowerCase() : raw);
}
export function countryLabel(value) {
  const code = countryCode(value);
  return APP_AREA_OPTIONS.find(row => row.value === code)?.label || String(value ?? '');
}
