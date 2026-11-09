export interface OfferCondition {
  field: 'OS' | 'COUNTRY';
  operator: 'IS' | 'IS_NOT';
  values: string[];
}

export interface AttributionOffer {
  clientKey: string;
  bindingId?: string;
  offerId?: string;
  sortOrder?: number;
  targetUrl: string;
  weight: number | null;
  matchMode: 'AND' | 'OR';
  conditions: OfferCondition[];
  conditionSummary?: string;
}

export interface ApplicationRow {
  applicationId: string;
  applicationCode: string;
  applicationName: string;
  channelId: string;
  channelCode: string;
  channelName: string;
  fallbackUrl: string;
  firstOfferUrl: string | null;
  offerCount: number;
  configVersion: string;
  editable: boolean;
}

export interface Configuration {
  applicationName: string;
  channelName: string;
  fallbackUrl: string;
  offers: AttributionOffer[];
  managedBindingIds: string[];
  expectedConfigVersion?: string;
}

export interface SavePayload {
  applicationName: string;
  channelName: string;
  fallbackUrl: string;
  offers: AttributionOffer[];
  expectedConfigVersion?: string;
  removedBindingIds?: string[];
}

export interface FieldError {
  path: string;
  message: string;
  clientKey?: string;
}

export interface Capabilities {
  countryEnabled: boolean;
}
