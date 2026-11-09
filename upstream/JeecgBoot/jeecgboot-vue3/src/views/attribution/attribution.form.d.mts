import type { AttributionOffer, Configuration, FieldError, SavePayload } from './attribution.types';

export const osOptions: { label: string; value: string }[];
export function newOffer(count: number, clientKey: string): AttributionOffer;
export function hydrateForm(source: Configuration, makeKey: () => string): Configuration;
export function buildPayload(form: Configuration): SavePayload;
export function validateForm(form: Configuration, options?: { countryEnabled?: boolean; originalOffers?: AttributionOffer[] }): FieldError[];
export function createSaveAttempt(makeKey: () => string): { prepare(payload: SavePayload): { key: string; payload: SavePayload } };
export function readError(raw: unknown, offers?: AttributionOffer[]): { message: string; conflict: boolean; fieldErrors: FieldError[] };
export function conditionSummary(offer: AttributionOffer): string;
