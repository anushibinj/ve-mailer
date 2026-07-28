import React, { useEffect, useRef, useState } from 'react';
import { Loader2, Trash2, ChevronDown, Search, Check } from 'lucide-react';
import {
  fetchFilterableFields,
  fetchFieldValues,
  type FilterCriteriaClause,
  type OctaneFieldDto,
  type OctaneFieldValueDto,
} from '../services/apiService';

interface SmartFilterRowProps {
  clause: FilterCriteriaClause;
  index: number;
  entityType: string;
  workspaceId: string;
  onChange: (updates: Partial<FilterCriteriaClause>) => void;
  onRemove: () => void;
  canRemove: boolean;
}

type FieldKind = 'reference' | 'number' | 'date' | 'boolean' | 'text';

const REFERENCE_OPERATORS = [
  { value: 'IN', label: 'is' },
  { value: 'NOT_IN', label: 'is not' },
  { value: 'STARTS_WITH', label: 'starts with' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'NOT_CONTAINS', label: 'does not contain' },
  { value: 'IS_EMPTY', label: 'is empty' },
  { value: 'IS_NOT_EMPTY', label: 'is not empty' },
];
const TEXT_OPERATORS = [
  { value: 'IN', label: 'is' },
  { value: 'NOT_IN', label: 'is not' },
  { value: 'STARTS_WITH', label: 'starts with' },
  { value: 'CONTAINS', label: 'contains' },
  { value: 'NOT_CONTAINS', label: 'does not contain' },
  { value: 'IS_EMPTY', label: 'is empty' },
  { value: 'IS_NOT_EMPTY', label: 'is not empty' },
];
const NUMBER_OPERATORS = [
  { value: 'EQ', label: '=' },
  { value: 'NEQ', label: '≠' },
  { value: 'LT', label: '<' },
  { value: 'LTE', label: '≤' },
  { value: 'GT', label: '>' },
  { value: 'GTE', label: '≥' },
  { value: 'IS_EMPTY', label: 'is empty' },
  { value: 'IS_NOT_EMPTY', label: 'is not empty' },
];
const DATE_OPERATORS = [
  { value: 'LT', label: 'is before' },
  { value: 'LTE', label: 'is on or before' },
  { value: 'GT', label: 'is after' },
  { value: 'GTE', label: 'is on or after' },
  { value: 'IS_EMPTY', label: 'is empty' },
  { value: 'IS_NOT_EMPTY', label: 'is not empty' },
];
const BOOLEAN_OPERATORS = [
  { value: 'EQ', label: 'is' },
  { value: 'NEQ', label: 'is not' },
  { value: 'IS_EMPTY', label: 'is empty' },
  { value: 'IS_NOT_EMPTY', label: 'is not empty' },
];
const DATE_PRESET_OPTIONS = [
  { value: 'TODAY', label: 'Today' },
  { value: 'YESTERDAY', label: 'Yesterday' },
  { value: 'LAST_24_HOURS', label: 'Last 24 hours' },
  { value: 'LAST_7_DAYS', label: 'Last 7 days' },
  { value: 'LAST_30_DAYS', label: 'Last 30 days' },
  { value: 'LAST_X_DAYS', label: 'Last X days' },
  { value: 'CUSTOM', label: 'Custom date/time' },
];
const LOGICAL_OPERATORS = [
  { value: 'AND', label: 'And', desc: 'All filters must match' },
  { value: 'OR',  label: 'Or',  desc: 'At least one filter must match' },
];

function getFieldKind(field: OctaneFieldDto | undefined): FieldKind {
  if (!field) return 'text';
  if (field.reference) return 'reference';
  const normalizedType = (field.fieldType ?? '').trim().toLowerCase().replace('-', '_');
  if (normalizedType === 'integer' || normalizedType === 'float') return 'number';
  // Octane metadata may return date types as "date_time", "datetime", or "date".
  if (normalizedType === 'date_time' || normalizedType === 'datetime' || normalizedType === 'date') return 'date';
  if (normalizedType === 'boolean') return 'boolean';
  return 'text';
}

function getOperatorsForField(field: OctaneFieldDto | undefined) {
  const kind = getFieldKind(field);
  if (kind === 'reference') return REFERENCE_OPERATORS;
  if (kind === 'number') return NUMBER_OPERATORS;
  if (kind === 'date') return DATE_OPERATORS;
  if (kind === 'boolean') return BOOLEAN_OPERATORS;
  return TEXT_OPERATORS;
}

function getDefaultOperatorForField(field: OctaneFieldDto | undefined): string {
  return getOperatorsForField(field)[0].value;
}

function isDatePresetToken(value: string | undefined): boolean {
  if (!value) return false;
  const upper = value.toUpperCase();
  return upper === 'TODAY'
    || upper === 'YESTERDAY'
    || upper === 'LAST_24_HOURS'
    || upper === 'LAST_7_DAYS'
    || upper === 'LAST_30_DAYS';
}

function parseLastNDaysToken(value: string | undefined): number | null {
  if (!value) return null;
  const normalized = value.trim().toUpperCase();
  const match = normalized.match(/^LAST_(\d+)_DAYS$/) ?? normalized.match(/^LAST_X_DAYS_(\d+)$/);
  if (!match) return null;
  const days = Number.parseInt(match[1], 10);
  return Number.isFinite(days) && days > 0 ? days : null;
}

function getDefaultValueForField(field: OctaneFieldDto | undefined, operator: string): string[] {
  if (isEmptyOperator(operator)) return [];
  const kind = getFieldKind(field);
  if (kind === 'reference') return [];
  if (kind === 'boolean') return ['true'];
  if (kind === 'date') return ['LAST_24_HOURS'];
  return [''];
}

function isEmptyOperator(operator: string | undefined): boolean {
  return operator === 'IS_EMPTY' || operator === 'IS_NOT_EMPTY';
}

function isReferenceTextOperator(operator: string | undefined): boolean {
  return operator === 'STARTS_WITH' || operator === 'CONTAINS' || operator === 'NOT_CONTAINS';
}

// ------------------------------------------------------------------ //
//  Searchable dropdown for field selection                            //
// ------------------------------------------------------------------ //
interface FieldSelectProps {
  fields: OctaneFieldDto[];
  value: string;
  loading: boolean;
  onChange: (fieldName: string) => void;
  inputClass: string;
}

const FieldSelect: React.FC<FieldSelectProps> = ({ fields, value, loading, onChange, inputClass }) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);

  const selected = fields.find(f => f.name === value);
  const filtered = fields.filter(f =>
    f.label.toLowerCase().includes(search.toLowerCase()) ||
    f.name.toLowerCase().includes(search.toLowerCase())
  );

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => { setOpen(o => !o); setSearch(''); }}
        className={`${inputClass} flex items-center justify-between gap-2 text-left`}
      >
        <span className={selected ? 'text-slate-900 dark:text-slate-100' : 'text-slate-400 dark:text-slate-500'}>
          {loading ? 'Loading fields…' : (selected ? selected.label : 'Select field…')}
        </span>
        {loading
          ? <Loader2 className="h-4 w-4 animate-spin text-slate-400 flex-shrink-0" />
          : <ChevronDown className="h-4 w-4 text-slate-400 flex-shrink-0" />}
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg max-h-64 flex flex-col overflow-hidden">
          <div className="p-2 border-b border-slate-100 dark:border-slate-800 flex items-center gap-2">
            <Search className="h-3.5 w-3.5 text-slate-400 flex-shrink-0" />
            <input
              autoFocus
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Type to search…"
              className="flex-1 text-sm bg-transparent outline-none text-slate-900 dark:text-slate-100 placeholder:text-slate-400"
            />
          </div>
          <div className="overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="p-3 text-sm text-slate-400 text-center">No fields found</p>
            ) : (
              filtered.map(f => (
                <button
                  key={f.name}
                  type="button"
                  onClick={() => { onChange(f.name); setOpen(false); }}
                  className={`w-full text-left px-3 py-2 text-sm flex items-center justify-between gap-2 transition-colors
                    ${f.name === value
                      ? 'bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300'
                      : 'hover:bg-slate-50 dark:hover:bg-slate-800 text-slate-700 dark:text-slate-300'}`}
                >
                  <span>{f.label}</span>
                  <span className="text-xs text-slate-400 font-mono truncate max-w-[120px]">{f.name}</span>
                </button>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
};

// ------------------------------------------------------------------ //
//  Multi-select value picker for reference fields                     //
// ------------------------------------------------------------------ //
interface ValuePickerProps {
  values: OctaneFieldValueDto[];
  selected: string[];  // selected IDs
  loading: boolean;
  searching: boolean;
  onChange: (ids: string[]) => void;
  onOpen: () => void;
  onSearchMiss: (term: string) => void;
  inputClass: string;
}

const ValuePicker: React.FC<ValuePickerProps> = ({ values, selected, loading, searching, onChange, onOpen, onSearchMiss, inputClass }) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const requestedTermRef = useRef('');
  const searchDebounceRef = useRef<number | null>(null);

  const filtered = values.filter(v =>
    v.name.toLowerCase().includes(search.toLowerCase()) ||
    v.id.toLowerCase().includes(search.toLowerCase())
  );
  const selectedItems = selected
    .map(id => values.find(v => v.id === id))
    .filter(Boolean) as OctaneFieldValueDto[];

  const toggle = (id: string) => {
    onChange(selected.includes(id) ? selected.filter(s => s !== id) : [...selected, id]);
  };

  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  useEffect(() => {
    const normalized = search.trim();
    if (!open || loading || normalized.length < 2 || filtered.length > 0) return;
    if (requestedTermRef.current === normalized.toLowerCase()) return;
    if (searchDebounceRef.current !== null) {
      window.clearTimeout(searchDebounceRef.current);
    }
    searchDebounceRef.current = window.setTimeout(() => {
      requestedTermRef.current = normalized.toLowerCase();
      onSearchMiss(normalized);
    }, 300);
  }, [search, open, loading, filtered.length, onSearchMiss]);

  useEffect(() => {
    if (search.trim().length === 0) {
      requestedTermRef.current = '';
    }
    return () => {
      if (searchDebounceRef.current !== null) {
        window.clearTimeout(searchDebounceRef.current);
        searchDebounceRef.current = null;
      }
    };
  }, [search]);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => {
          setOpen(prev => {
            const next = !prev;
            if (next) {
              onOpen();
            }
            return next;
          });
          setSearch('');
        }}
        className={`${inputClass} flex items-center justify-between gap-2 text-left min-h-[42px]`}
      >
        <div className="flex flex-wrap gap-1 flex-1 min-w-0">
          {selectedItems.length > 0 ? (
            selectedItems.map(item => (
              <span
                key={item.id}
                className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-indigo-50 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300 text-xs font-medium border border-indigo-100 dark:border-indigo-500/30"
              >
                {item.name}
                <span
                  role="button"
                  tabIndex={0}
                  onClick={e => { e.stopPropagation(); toggle(item.id); }}
                  onKeyDown={e => e.key === 'Enter' && toggle(item.id)}
                  className="ml-0.5 text-indigo-400 hover:text-indigo-600 cursor-pointer leading-none"
                >×</span>
              </span>
            ))
          ) : loading ? (
            <span className="text-slate-400 dark:text-slate-500 text-sm flex items-center gap-1">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />Loading…
            </span>
          ) : (
            <span className="text-slate-400 dark:text-slate-500 text-sm">Select value(s)…</span>
          )}
        </div>
        {loading ? (
          <Loader2 className="h-4 w-4 animate-spin text-slate-400 flex-shrink-0" />
        ) : (
          <ChevronDown className="h-4 w-4 text-slate-400 flex-shrink-0" />
        )}
      </button>

      {open && (
        <div className="absolute z-50 mt-1 w-full bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg max-h-72 flex flex-col overflow-hidden">
          <div className="p-2 border-b border-slate-100 dark:border-slate-800 flex items-center gap-2">
            <Search className="h-3.5 w-3.5 text-slate-400 flex-shrink-0" />
            <input
              autoFocus
              type="text"
              value={search}
              onChange={e => setSearch(e.target.value)}
              placeholder="Type to search…"
              className="flex-1 text-sm bg-transparent outline-none text-slate-900 dark:text-slate-100 placeholder:text-slate-400"
            />
          </div>
          {loading && (
            <div className="px-3 py-2 border-b border-slate-100 dark:border-slate-800 text-sm text-slate-500 flex items-center gap-2">
              <Loader2 className="h-3.5 w-3.5 animate-spin text-slate-400" />
              <span>Fetching fields…</span>
            </div>
          )}
          <div className="overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="p-3 text-sm text-slate-400 text-center">
                {loading ? 'Loading values…' : (searching ? 'Searching in Octane…' : 'No values found')}
              </p>
            ) : (
              filtered.map(v => {
                const isSelected = selected.includes(v.id);
                return (
                  <button
                    key={v.id}
                    type="button"
                    onClick={() => toggle(v.id)}
                    className={`w-full text-left px-3 py-2 text-sm flex items-center gap-3 transition-colors
                      ${isSelected
                        ? 'bg-indigo-50 dark:bg-indigo-500/10'
                        : 'hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                  >
                    <span className={`h-4 w-4 rounded flex-shrink-0 border flex items-center justify-center transition-colors ${
                      isSelected
                        ? 'bg-indigo-600 border-indigo-600'
                        : 'border-slate-300 dark:border-slate-600'}`}>
                      {isSelected && <Check className="h-2.5 w-2.5 text-white" />}
                    </span>
                    <span className={isSelected ? 'text-indigo-700 dark:text-indigo-300 font-medium' : 'text-slate-700 dark:text-slate-300'}>
                      {v.name}
                    </span>
                  </button>
                );
              })
            )}
          </div>
          {selected.length > 0 && (
            <div className="p-2 border-t border-slate-100 dark:border-slate-800 flex justify-between items-center">
              <span className="text-xs text-slate-500">{selected.length} selected</span>
              <button
                type="button"
                onClick={() => onChange([])}
                className="text-xs text-red-500 hover:text-red-600 font-medium"
              >Clear all</button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

// ------------------------------------------------------------------ //
//  Main SmartFilterRow component                                      //
// ------------------------------------------------------------------ //

/**
 * A single filter condition row in the Easy Filter Builder.
 *
 * Shows:
 * - AND/OR connector for rows after the first
 * - A searchable "Field" dropdown populated from Octane metadata
 * - An "Operator" dropdown adapted from the runtime field type
 * - A "Value" control:
 *     - Reference fields → searchable multi-select populated from Octane
 *     - String/memo fields → free-text input
 *     - Integer/float fields → number input with comparison operators
 *     - Date fields → preset date windows + custom date/time
 *     - Boolean fields → true/false dropdown
 */
export const SmartFilterRow: React.FC<SmartFilterRowProps> = ({
  clause,
  index,
  entityType,
  workspaceId,
  onChange,
  onRemove,
  canRemove,
}) => {
  const [allFields, setAllFields] = useState<OctaneFieldDto[]>([]);
  const [fieldsLoading, setFieldsLoading] = useState(false);
  const [fieldValues, setFieldValues] = useState<OctaneFieldValueDto[]>([]);
  const [valuesLoading, setValuesLoading] = useState(false);
  const [valueSearchLoading, setValueSearchLoading] = useState(false);
  const [logicalOpen, setLogicalOpen] = useState(false);
  const logicalRef = useRef<HTMLDivElement>(null);
  const lastSearchKeyRef = useRef('');
  const didLoadDefaultValuesRef = useRef(false);
  const previousFieldRef = useRef('');

  const inputClass =
    'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
    'text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
    'bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
    'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all';

  // Load field definitions when entity type changes
  useEffect(() => {
    if (!workspaceId || !entityType) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setFieldsLoading(true);
    setAllFields([]);
    fetchFilterableFields(workspaceId, entityType)
      .then(setAllFields)
      .catch(() => {/* silently ignore — user can still type manually if needed */})
      .finally(() => setFieldsLoading(false));
  }, [workspaceId, entityType]);

  // Load field values when selected field changes (reference fields only)
  const selectedFieldMeta = allFields.find(f => f.name === clause.field);
  useEffect(() => {
    if (!selectedFieldMeta?.reference || !workspaceId || !entityType) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setFieldValues([]);
      setValueSearchLoading(false);
      lastSearchKeyRef.current = '';
      didLoadDefaultValuesRef.current = false;
      previousFieldRef.current = '';
      return;
    }
    const fieldChanged = previousFieldRef.current !== clause.field;
    if (fieldChanged) {
      setFieldValues([]);
      setValueSearchLoading(false);
      lastSearchKeyRef.current = '';
      didLoadDefaultValuesRef.current = false;
      previousFieldRef.current = clause.field;
    }

    const selectedIds = clause.values.map(v => v.trim()).filter(Boolean);
    if (selectedIds.length === 0) {
      setValuesLoading(false);
      return;
    }

    const knownIds = new Set(fieldValues.map(v => v.id));
    const missingIds = selectedIds.filter(id => !knownIds.has(id));
    if (missingIds.length === 0) {
      setValuesLoading(false);
      return;
    }

    setValuesLoading(true);
    fetchFieldValues(workspaceId, clause.field, entityType, undefined, missingIds)
      .then(remoteValues => {
        if (remoteValues.length === 0) {
          return;
        }
        setFieldValues(current => {
          const merged = new Map(current.map(v => [v.id, v]));
          remoteValues.forEach(v => merged.set(v.id, v));
          return Array.from(merged.values()).sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
          );
        });
      })
      .finally(() => setValuesLoading(false));
  }, [clause.field, clause.values, fieldValues, selectedFieldMeta?.reference, workspaceId, entityType]);

  // Close logical operator dropdown on outside click
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (logicalRef.current && !logicalRef.current.contains(e.target as Node)) setLogicalOpen(false);
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const currentLogical = clause.logicalOperator || 'AND';
  const logicalLabel = LOGICAL_OPERATORS.find(l => l.value === currentLogical)?.label ?? 'And';
  const operators = getOperatorsForField(selectedFieldMeta);
  const selectedFieldKind = getFieldKind(selectedFieldMeta);

  const handleFieldChange = (fieldName: string) => {
    const selectedMeta = allFields.find(f => f.name === fieldName);
    const nextOperator = getDefaultOperatorForField(selectedMeta);
    onChange({
      field: fieldName,
      operator: nextOperator,
      values: getDefaultValueForField(selectedMeta, nextOperator),
      referenceValues: selectedMeta?.reference ?? false,
    });
  };

  const handleTextValueChange = (val: string) => {
    onChange({ values: val ? [val] : [], referenceValues: false });
  };

  const handleSearchMiss = (term: string) => {
    const normalized = term.trim();
    if (!normalized || !workspaceId || !entityType || !clause.field || !selectedFieldMeta?.reference) {
      return;
    }

    const searchKey = `${clause.field}:${normalized.toLowerCase()}`;
    if (lastSearchKeyRef.current === searchKey) {
      return;
    }
    lastSearchKeyRef.current = searchKey;
    setValueSearchLoading(true);
    fetchFieldValues(workspaceId, clause.field, entityType, normalized)
      .then(remoteValues => {
        if (remoteValues.length === 0) {
          return;
        }
        setFieldValues(current => {
          if (current.length === 0) {
            return remoteValues;
          }
          const merged = new Map(current.map(v => [v.id, v]));
          remoteValues.forEach(v => merged.set(v.id, v));
          return Array.from(merged.values()).sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
          );
        });
      })
      .finally(() => setValueSearchLoading(false));
  };

  const handleValuePickerOpen = () => {
    if (!workspaceId || !entityType || !clause.field || !selectedFieldMeta?.reference) {
      return;
    }
    if (didLoadDefaultValuesRef.current || valuesLoading) {
      return;
    }

    didLoadDefaultValuesRef.current = true;
    setValuesLoading(true);
    fetchFieldValues(workspaceId, clause.field, entityType)
      .then(remoteValues => {
        setFieldValues(current => {
          if (current.length === 0) {
            return remoteValues;
          }
          const merged = new Map(current.map(v => [v.id, v]));
          remoteValues.forEach(v => merged.set(v.id, v));
          return Array.from(merged.values()).sort((a, b) =>
            a.name.localeCompare(b.name, undefined, { sensitivity: 'base' })
          );
        });
      })
      .catch(() => {
        // keep pre-resolved selected values even if default list fetch fails
      })
      .finally(() => setValuesLoading(false));
  };

  useEffect(() => {
    if (!selectedFieldMeta) return;
    if (!operators.some(op => op.value === clause.operator)) {
      const nextOperator = getDefaultOperatorForField(selectedFieldMeta);
      onChange({
        operator: nextOperator,
        values: getDefaultValueForField(selectedFieldMeta, nextOperator),
        referenceValues: selectedFieldMeta.reference,
      });
    }
  // Intentionally do not add `onChange` to avoid recreating this synchronizer every render.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedFieldMeta, operators, clause.operator]);

  const isReference = selectedFieldKind === 'reference';
  const isNumeric = selectedFieldKind === 'number';
  const isDate = selectedFieldKind === 'date';
  const isBoolean = selectedFieldKind === 'boolean';
  const showValueInput = !isEmptyOperator(clause.operator);

  const selectedDateValue = clause.values[0] ?? '';
  const parsedLastNDays = parseLastNDaysToken(selectedDateValue);
  const selectedDatePreset = (() => {
    const upper = selectedDateValue.toUpperCase();
    if (upper.startsWith('LAST_X_DAYS_')) {
      return 'LAST_X_DAYS';
    }
    if (upper === 'TODAY'
      || upper === 'YESTERDAY'
      || upper === 'LAST_24_HOURS'
      || upper === 'LAST_7_DAYS'
      || upper === 'LAST_30_DAYS') {
      return upper;
    }
    if (parsedLastNDays !== null) {
      return upper === 'LAST_7_DAYS' || upper === 'LAST_30_DAYS' ? upper : 'LAST_X_DAYS';
    }
    return 'CUSTOM';
  })();
  const lastXDaysValue = selectedDatePreset === 'LAST_X_DAYS'
    ? String(parsedLastNDays ?? 7)
    : '7';
  const dateInputFromIso = (iso: string): string => {
    const parsed = new Date(iso);
    if (Number.isNaN(parsed.getTime())) return '';
    const localMillis = parsed.getTime() - parsed.getTimezoneOffset() * 60000;
    return new Date(localMillis).toISOString().slice(0, 16);
  };
  const isoFromDateInput = (value: string): string => {
    const parsed = new Date(value);
    return Number.isNaN(parsed.getTime()) ? '' : parsed.toISOString();
  };

  return (
    <div className="border border-slate-200 dark:border-slate-700 rounded-xl overflow-visible bg-white dark:bg-slate-900">
      {/* AND/OR connector (shown for rows after the first) */}
      {index > 0 && (
        <div className="px-4 pt-3 flex items-center gap-2" ref={logicalRef}>
          <div className="relative">
            <button
              type="button"
              onClick={() => setLogicalOpen(o => !o)}
              className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg border border-slate-200 dark:border-slate-700 text-sm font-medium text-slate-700 dark:text-slate-300 bg-slate-50 dark:bg-slate-800 hover:bg-white dark:hover:bg-slate-700 transition-colors"
            >
              {logicalLabel}
              <ChevronDown className="h-3.5 w-3.5 text-slate-400" />
            </button>

            {logicalOpen && (
              <div className="absolute z-50 mt-1 w-56 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700 rounded-xl shadow-lg overflow-hidden">
                {LOGICAL_OPERATORS.map(opt => (
                  <button
                    key={opt.value}
                    type="button"
                    onClick={() => { onChange({ logicalOperator: opt.value }); setLogicalOpen(false); }}
                    className={`w-full text-left px-4 py-3 transition-colors
                      ${opt.value === currentLogical
                        ? 'bg-indigo-50 dark:bg-indigo-500/10'
                        : 'hover:bg-slate-50 dark:hover:bg-slate-800'}`}
                  >
                    <p className={`text-sm font-semibold ${opt.value === currentLogical ? 'text-indigo-700 dark:text-indigo-300' : 'text-slate-800 dark:text-slate-100'}`}>
                      {opt.label}
                    </p>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">{opt.desc}</p>
                  </button>
                ))}
              </div>
            )}
          </div>
          <span className="text-xs text-slate-400 dark:text-slate-500">connector to next filter</span>
        </div>
      )}

      {/* Row content */}
      <div className="p-4">
        <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto_1fr_auto] gap-3 items-start">
          {/* Field selector */}
          <FieldSelect
            fields={allFields}
            value={clause.field}
            loading={fieldsLoading}
            onChange={handleFieldChange}
            inputClass={inputClass}
          />

          {/* Operator */}
          <select
            value={clause.operator}
            onChange={e => {
              const nextOperator = e.target.value;
              if (isEmptyOperator(nextOperator)) {
                onChange({ operator: nextOperator, values: [], referenceValues: selectedFieldMeta?.reference ?? false });
                return;
              }
              if (clause.values.length === 0 || (clause.values.length === 1 && clause.values[0] === '')) {
                onChange({
                  operator: nextOperator,
                  values: getDefaultValueForField(selectedFieldMeta, nextOperator),
                });
                return;
              }
              onChange({ operator: nextOperator });
            }}
            className={`${inputClass} sm:w-32`}
          >
            {operators.map(op => (
              <option key={op.value} value={op.value}>{op.label}</option>
            ))}
          </select>

          {/* Value input */}
          {!showValueInput ? (
            <div className={`${inputClass} flex items-center text-slate-400 dark:text-slate-500`}>
              No value required
            </div>
          ) : isReference && !isReferenceTextOperator(clause.operator) ? (
            <ValuePicker
              values={fieldValues}
              selected={clause.values}
              loading={valuesLoading}
              searching={valueSearchLoading}
              onChange={ids => onChange({ values: ids, referenceValues: true })}
              onOpen={handleValuePickerOpen}
              onSearchMiss={handleSearchMiss}
              inputClass={inputClass}
            />
          ) : isNumeric ? (
            <input
              type="number"
              value={clause.values[0] ?? ''}
              onChange={e => handleTextValueChange(e.target.value)}
              className={inputClass}
              placeholder="Enter number…"
            />
          ) : isDate ? (
            <div className="space-y-2">
              <select
                value={selectedDatePreset}
                onChange={e => {
                  const selected = e.target.value;
                  if (selected === 'CUSTOM') {
                    const current = clause.values[0] ?? '';
                    onChange({ values: [isDatePresetToken(current) ? '' : current] });
                    return;
                  }
                  if (selected === 'LAST_X_DAYS') {
                    onChange({ values: ['LAST_X_DAYS_7'] });
                    return;
                  }
                  onChange({ values: [selected] });
                }}
                className={inputClass}
              >
                {DATE_PRESET_OPTIONS.map(option => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {selectedDatePreset === 'LAST_X_DAYS' && (
                <div className="flex items-center gap-2">
                  <input
                    type="number"
                    min={1}
                    step={1}
                    value={lastXDaysValue}
                    onChange={e => {
                      const raw = e.target.value.trim();
                      if (raw === '') {
                        return;
                      }
                      const days = Number.parseInt(raw, 10);
                      if (!Number.isFinite(days) || days < 1) {
                        return;
                      }
                      onChange({ values: [`LAST_X_DAYS_${days}`] });
                    }}
                    className={inputClass}
                  />
                  <span className="text-xs text-slate-500 dark:text-slate-400 whitespace-nowrap">days</span>
                </div>
              )}
              {selectedDatePreset === 'CUSTOM' && (
                <input
                  type="datetime-local"
                  value={dateInputFromIso(clause.values[0] ?? '')}
                  onChange={e => onChange({ values: [isoFromDateInput(e.target.value)] })}
                  className={inputClass}
                />
              )}
            </div>
          ) : isBoolean ? (
            <select
              value={(clause.values[0] ?? 'true').toLowerCase()}
              onChange={e => handleTextValueChange(e.target.value)}
              className={inputClass}
            >
              <option value="true">True</option>
              <option value="false">False</option>
            </select>
          ) : (
            <input
              type="text"
              value={clause.values[0] ?? ''}
              onChange={e => handleTextValueChange(e.target.value)}
              className={inputClass}
              placeholder="Enter value…"
            />
          )}

          {/* Remove button */}
          {canRemove && (
            <button
              type="button"
              onClick={onRemove}
              className="self-center p-2 text-slate-400 dark:text-slate-500 hover:text-red-500 dark:hover:text-red-400 transition-colors rounded-lg hover:bg-red-50 dark:hover:bg-red-500/10 cursor-pointer"
              aria-label="Remove filter row"
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default SmartFilterRow;
