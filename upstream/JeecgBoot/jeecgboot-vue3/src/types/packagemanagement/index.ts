export type PackageId = string | number;
export type PackageRecord = Record<string, any>;
export interface PackagePage { records: PackageRecord[]; total: number }
export interface PackageOption { value: string | number; label: string }
export interface PackageField {
  key: string; label: string; type?: 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'switch' | 'image' | 'dict' | 'parent';
  required?: boolean; dict?: string; multiple?: boolean; immutable?: boolean; readonly?: boolean; max?: number;
}
export interface PackageDefinition {
  domain: string; title: string; key?: string; fields: PackageField[]; filters: string[]; columns?: string[];
  export?: boolean; add?: boolean; edit?: boolean; appType?: number;
}
