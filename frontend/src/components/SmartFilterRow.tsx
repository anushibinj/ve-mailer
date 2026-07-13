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

// Operators available per field type category
const REFERENCE_OPERATORS = [
  { value: 'IN', label: 'is' },
  { value: 'NOT_IN', label: 'is not' },
];
const STRING_OPERATORS = [
  { value: 'IN', label: 'is' },
  { value: 'NOT_IN', label: 'is not' },
];
const LOGICAL_OPERATORS = [
  { value: 'AND', label: 'And', desc: 'All filters must match' },
  { value: 'OR',  label: 'Or',  desc: 'At least one filter must match' },
];

function getOperatorsForField(field: OctaneFieldDto | undefined) {
  if (!field) return REFERENCE_OPERATORS;
  if (field.reference) return REFERENCE_OPERATORS;
  return STRING_OPERATORS;
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
  onSearchMiss: (term: string) => void;
  inputClass: string;
}

const ValuePicker: React.FC<ValuePickerProps> = ({ values, selected, loading, searching, onChange, onSearchMiss, inputClass }) => {
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState('');
  const ref = useRef<HTMLDivElement>(null);
  const requestedTermRef = useRef('');

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
    requestedTermRef.current = normalized.toLowerCase();
    onSearchMiss(normalized);
  }, [search, open, loading, filtered.length, onSearchMiss]);

  useEffect(() => {
    if (search.trim().length === 0) {
      requestedTermRef.current = '';
    }
  }, [search]);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => { setOpen(o => !o); setSearch(''); }}
        className={`${inputClass} flex items-center justify-between gap-2 text-left min-h-[42px]`}
      >
        <div className="flex flex-wrap gap-1 flex-1 min-w-0">
          {loading ? (
            <span className="text-slate-400 dark:text-slate-500 text-sm flex items-center gap-1">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />Loading…
            </span>
          ) : selectedItems.length === 0 ? (
            <span className="text-slate-400 dark:text-slate-500 text-sm">Select value(s)…</span>
          ) : (
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
          )}
        </div>
        <ChevronDown className="h-4 w-4 text-slate-400 flex-shrink-0" />
      </button>

      {open && !loading && (
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
          <div className="overflow-y-auto">
            {filtered.length === 0 ? (
              <p className="p-3 text-sm text-slate-400 text-center">
                {searching ? 'Searching in Octane…' : 'No values found'}
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
 * - An "Operator" dropdown (is / is not) appropriate for the field type
 * - A "Value" control:
 *     - Reference fields → searchable multi-select populated from Octane
 *     - String/memo fields → text input
 *     - Integer/float fields → number input
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
      return;
    }
    setValuesLoading(true);
    setFieldValues([]);
    setValueSearchLoading(false);
    lastSearchKeyRef.current = '';
    fetchFieldValues(workspaceId, clause.field, entityType)
      .then(setFieldValues)
      .catch(() => setFieldValues([]))
      .finally(() => setValuesLoading(false));
  }, [clause.field, selectedFieldMeta?.reference, workspaceId, entityType]);

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

  const handleFieldChange = (fieldName: string) => {
    const selectedMeta = allFields.find(f => f.name === fieldName);
    // Reset values when field changes since the value type may differ
    onChange({ field: fieldName, values: [], referenceValues: selectedMeta?.reference ?? false });
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

  const isReference = selectedFieldMeta?.reference ?? false;
  const isNumeric   = selectedFieldMeta?.fieldType === 'integer' || selectedFieldMeta?.fieldType === 'float';

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
            onChange={e => onChange({ operator: e.target.value })}
            className={`${inputClass} sm:w-32`}
          >
            {operators.map(op => (
              <option key={op.value} value={op.value}>{op.label}</option>
            ))}
          </select>

          {/* Value input */}
          {isReference ? (
            <ValuePicker
              values={fieldValues}
              selected={clause.values}
              loading={valuesLoading}
              searching={valueSearchLoading}
              onChange={ids => onChange({ values: ids, referenceValues: true })}
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
