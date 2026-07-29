import React, { useCallback, useEffect, useRef, useState } from 'react';
import ReactDOM from 'react-dom';
import {
  fetchFilters,
  fetchFilterableFields,
  createFilter,
  updateFilter,
  previewFilter,
  cloneFilter,
  parseFilterQueryString,
  getFilterQueryString,
  deleteFilter,
  type Filter,
  type FilterCriteriaClause,
  type FilterCreatePayload,
  type FilterUpdatePayload,
  type PreviewResponse,
  type OctaneFieldDto
} from '../services/apiService';
import { useAuth } from '../hooks/useAuth';
import { Loader2, ArrowLeft, Plus, Trash2, Eye, Pencil, Copy, ChevronDown, ChevronUp, SlidersHorizontal, Search, X } from 'lucide-react';
import toast from 'react-hot-toast';
import ConfirmDialog from './ConfirmDialog';
import { SmartFilterRow } from './SmartFilterRow';

interface FilterBuilderViewProps {
  workspaceId: string;
  onBack: () => void;
}

type ViewMode = 'list' | 'create' | 'edit';
type FilterCreationMode = 'queryString' | 'manual';
type OrderByDirection = 'ASC' | 'DESC';

const ENTITY_TYPES = [
  { value: 'backlog_items', label: 'Backlog Items (Story/Defect/Quality Story)' },
  { value: 'epic', label: 'Epics' },
  { value: 'feature', label: 'Features' },
];
const AI_SUMMARY_FIELD = '✨ AI Summary';
const TRIAGE_SLA_FIELD = 'Triage SLA';
const CUSTOM_PSEUDO_FIELDS = [AI_SUMMARY_FIELD, TRIAGE_SLA_FIELD];

const emptyCriterion = (): FilterCriteriaClause => ({ field: '', operator: 'IN', values: [], logicalOperator: 'AND' });
const isEmptyOperator = (operator?: string): boolean => operator === 'IS_EMPTY' || operator === 'IS_NOT_EMPTY';
const criterionHasRequiredValue = (criterion: FilterCriteriaClause): boolean => {
  if (isEmptyOperator(criterion.operator)) return true;
  return criterion.values.length > 0 && criterion.values.some(v => v.trim() !== '');
};
const formatCriterionSummary = (criterion: FilterCriteriaClause): string => {
  if (isEmptyOperator(criterion.operator)) {
    return criterion.operator.toLowerCase().replace('_', ' ');
  }
  return `${criterion.operator.toLowerCase().replace('_', ' ')} [${criterion.values.join(', ')}]`;
};
const defaultFields = ['id', 'name', 'phase', 'owner'];
const DEFAULT_ORDER_BY_DIRECTION: OrderByDirection = 'ASC';

const normalizeEntityType = (raw: string): string => {
  if (raw === 'defect' || raw === 'story' || raw === 'quality_story') return 'backlog_items';
  return raw;
};

const toEntityTypeLabel = (raw: string): string => {
  const normalized = normalizeEntityType(raw);
  const fromOptions = ENTITY_TYPES.find(t => t.value === normalized)?.label;
  if (fromOptions) return fromOptions;
  return raw.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
};

/**
 * Octane EntityModel serializes field data as:
 *   { id, type, values: [{ name, value, jsonvalue }, ...] }
 *
 * This helper is kept for any future use of the raw execute endpoint.
 * The preview endpoint now returns pre-flattened records from the server.
 */
const _flattenOctaneItem = (item: Record<string, unknown>): Record<string, string> => {
  type OctaneFieldEntry = { name: string; value: unknown };
  type OctaneNestedEntity = { id?: string; type?: string; values?: OctaneFieldEntry[] };

  const valuesArr = item.values as OctaneFieldEntry[] | undefined;
  if (!Array.isArray(valuesArr)) return {};

  const flat: Record<string, string> = {};
  for (const entry of valuesArr) {
    const { name, value } = entry;
    if (value === null || value === undefined) {
      flat[name] = '—';
    } else if (typeof value === 'object' && Array.isArray((value as OctaneNestedEntity).values)) {
      const nested = value as OctaneNestedEntity;
      const nestedVals = nested.values ?? [];
      const find = (key: string) => nestedVals.find(v => v.name === key)?.value ?? null;
      flat[name] = String(find('name') ?? find('full_name') ?? find('id') ?? nested.id ?? '—');
    } else {
      flat[name] = String(value);
    }
  }
  return flat;
};
void _flattenOctaneItem; // suppress unused-variable lint warning

const inputClass =
  'w-full px-3.5 py-2.5 border border-slate-200 dark:border-slate-600 rounded-xl text-sm ' +
  'text-slate-900 dark:text-slate-100 placeholder:text-slate-400 dark:placeholder:text-slate-500 ' +
  'bg-white dark:bg-slate-800/60 focus:outline-none focus:border-indigo-500 dark:focus:border-indigo-400 ' +
  'focus:ring-2 focus:ring-indigo-500/15 dark:focus:ring-indigo-400/15 transition-all';

type PopoverPlacement = 'top' | 'left' | 'right' | 'bottom';

interface FieldBadgeWithPopoverProps {
  label: string;
  selected: boolean;
  onClick: () => void;
  selectedClassName: string;
  unselectedClassName: string;
  popoverText: string;
}

const FieldBadgeWithPopover: React.FC<FieldBadgeWithPopoverProps> = ({
  label,
  selected,
  onClick,
  selectedClassName,
  unselectedClassName,
  popoverText,
}) => {
  const buttonRef = useRef<HTMLButtonElement | null>(null);
  const popoverRef = useRef<HTMLDivElement | null>(null);
  const [isOpen, setIsOpen] = useState(false);
  const [placement, setPlacement] = useState<PopoverPlacement>('top');
  const [coords, setCoords] = useState({ top: 0, left: 0 });

  const updatePopoverPosition = useCallback(() => {
    const buttonEl = buttonRef.current;
    const popoverEl = popoverRef.current;
    if (!buttonEl || !popoverEl) return;

    const triggerRect = buttonEl.getBoundingClientRect();
    const popoverRect = popoverEl.getBoundingClientRect();
    const popoverWidth = popoverRect.width;
    const popoverHeight = popoverRect.height;
    const viewportWidth = window.innerWidth;
    const viewportHeight = window.innerHeight;
    const gap = 10;
    const edgePadding = 8;

    const topSpace = triggerRect.top;
    const leftSpace = triggerRect.left;
    const rightSpace = viewportWidth - triggerRect.right;

    let nextPlacement: PopoverPlacement;
    if (topSpace >= popoverHeight + gap) {
      nextPlacement = 'top';
    } else if (leftSpace >= popoverWidth + gap) {
      nextPlacement = 'left';
    } else if (rightSpace >= popoverWidth + gap) {
      nextPlacement = 'right';
    } else {
      nextPlacement = 'bottom';
    }

    let top = 0;
    let left = 0;
    if (nextPlacement === 'top') {
      top = triggerRect.top - popoverHeight - gap;
      left = triggerRect.left + (triggerRect.width / 2) - (popoverWidth / 2);
    } else if (nextPlacement === 'left') {
      top = triggerRect.top + (triggerRect.height / 2) - (popoverHeight / 2);
      left = triggerRect.left - popoverWidth - gap;
    } else if (nextPlacement === 'right') {
      top = triggerRect.top + (triggerRect.height / 2) - (popoverHeight / 2);
      left = triggerRect.right + gap;
    } else {
      top = triggerRect.bottom + gap;
      left = triggerRect.left + (triggerRect.width / 2) - (popoverWidth / 2);
    }

    top = Math.max(edgePadding, Math.min(top, viewportHeight - popoverHeight - edgePadding));
    left = Math.max(edgePadding, Math.min(left, viewportWidth - popoverWidth - edgePadding));

    setPlacement(nextPlacement);
    setCoords({ top, left });
  }, []);

  useEffect(() => {
    if (!isOpen) return;
    const rafId = window.requestAnimationFrame(() => updatePopoverPosition());
    const reposition = () => updatePopoverPosition();
    window.addEventListener('resize', reposition);
    window.addEventListener('scroll', reposition, true);
    return () => {
      window.cancelAnimationFrame(rafId);
      window.removeEventListener('resize', reposition);
      window.removeEventListener('scroll', reposition, true);
    };
  }, [isOpen, updatePopoverPosition]);

  return (
    <div
      className="inline-flex"
      onMouseEnter={() => setIsOpen(true)}
      onMouseLeave={() => setIsOpen(false)}
      onFocusCapture={() => setIsOpen(true)}
      onBlurCapture={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) {
          setIsOpen(false);
        }
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        onClick={onClick}
        aria-describedby={isOpen ? `${label}-popover` : undefined}
        className={`px-3 py-1 rounded-full text-xs font-medium border transition-all ${
          selected ? selectedClassName : unselectedClassName
        }`}
      >
        {label}
      </button>
      {isOpen && ReactDOM.createPortal(
        <div
          id={`${label}-popover`}
          ref={popoverRef}
          role="tooltip"
          data-placement={placement}
          style={{ top: `${coords.top}px`, left: `${coords.left}px` }}
          className="fixed z-50 w-[min(22rem,calc(100vw-16px))] rounded-lg border border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-900 px-3 py-2 text-xs leading-relaxed text-slate-700 dark:text-slate-200 shadow-xl whitespace-normal break-words"
        >
          {popoverText}
        </div>,
        document.body
      )}
    </div>
  );
};

const FilterBuilderView: React.FC<FilterBuilderViewProps> = ({ workspaceId, onBack }) => {
  const allowCustomQueryString = String(import.meta.env.VITE_ALLOW_CUSTOM_QUERY_STRING ?? 'false').toLowerCase() === 'true';
  const { isAdmin, isWorkspaceAdmin } = useAuth();
  const canManageAllFilters = isAdmin || isWorkspaceAdmin;
  const canCreateFilters = true;
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [editingFilter, setEditingFilter] = useState<Filter | null>(null);

  const [executeState, setExecuteState] = useState<Record<string, {
    isExecuting: boolean;
    results: Record<string, string>[] | null;
    expanded: boolean;
    aiSummaryGenerated: boolean;
  }>>({});
  const filterCardRefs = useRef<Record<string, HTMLDivElement | null>>({});

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [entityType, setEntityType] = useState('backlog_items');
  const [isPublicFilter, setIsPublicFilter] = useState(false);
  const [availableFields, setAvailableFields] = useState<OctaneFieldDto[]>([]);
  const [availableFieldsLoading, setAvailableFieldsLoading] = useState(false);
  const [creationMode, setCreationMode] = useState<FilterCreationMode | null>(allowCustomQueryString ? null : 'manual');
  const [selectedFields, setSelectedFields] = useState<string[]>(defaultFields);
  const [orderByField, setOrderByField] = useState('');
  const [orderByDirection, setOrderByDirection] = useState<OrderByDirection>(DEFAULT_ORDER_BY_DIRECTION);
  const [fieldSearch, setFieldSearch] = useState('');
  const [criteria, setCriteria] = useState<FilterCriteriaClause[]>([emptyCriterion()]);
  const [filterQueryString, setFilterQueryString] = useState('');
  const [queryStringApplied, setQueryStringApplied] = useState(false);
  const [isApplyingQueryString, setIsApplyingQueryString] = useState(false);
  const [draggedField, setDraggedField] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [filterToDelete, setFilterToDelete] = useState<Filter | null>(null);
  const [isDeleting, setIsDeleting] = useState(false);

  const loadFilters = async () => {
    setIsLoading(true);
    try {
      const data = await fetchFilters(workspaceId);
      setFilters(data);
    } catch {
      toast.error('Failed to load filter templates.');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadFilters();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

  useEffect(() => {
    if (!workspaceId || !entityType) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setAvailableFieldsLoading(true);
    setAvailableFields([]);
    fetchFilterableFields(workspaceId, entityType)
      .then(setAvailableFields)
      .catch(() => setAvailableFields([]))
      .finally(() => setAvailableFieldsLoading(false));
  }, [workspaceId, entityType]);

  const resetForm = () => {
    setTitle(''); setDescription(''); setEntityType('backlog_items');
    setIsPublicFilter(false);
    setCreationMode(allowCustomQueryString ? null : 'manual');
    setSelectedFields(defaultFields);
    setOrderByField('');
    setOrderByDirection(DEFAULT_ORDER_BY_DIRECTION);
    setFieldSearch('');
    setCriteria([emptyCriterion()]);
    setFilterQueryString('');
    setQueryStringApplied(false);
    setEditingFilter(null);
  };

  const populateFormFromFilter = (f: Filter) => {
    setTitle(f.title); setDescription(f.description || ''); setEntityType(normalizeEntityType(f.entityType));
    setIsPublicFilter(resolvePublicTemplate(f));
    setCreationMode('manual');
    setFieldSearch('');
    try { setSelectedFields(JSON.parse(f.fields)); } catch { setSelectedFields(defaultFields); }
    setOrderByField((f.orderBy ?? '').trim());
    setOrderByDirection(f.orderByDirection === 'DESC' ? 'DESC' : DEFAULT_ORDER_BY_DIRECTION);
    try {
      const parsed: FilterCriteriaClause[] = JSON.parse(f.criteria);
      // Backfill logicalOperator for criteria saved before AND/OR support was added
      const normalised = parsed.map(c => ({ ...c, logicalOperator: c.logicalOperator ?? 'AND' }));
      setCriteria(normalised.length > 0 ? normalised : [emptyCriterion()]);
    } catch { setCriteria([emptyCriterion()]); }
    setFilterQueryString('');
    setQueryStringApplied(false);
  };

  const handleCreateNew = () => { resetForm(); setViewMode('create'); };
  const handleEdit = (f: Filter) => { setEditingFilter(f); populateFormFromFilter(f); setViewMode('edit'); };
  const handleClone = async (f: Filter) => {
    try {
      const cloned = await cloneFilter(workspaceId, f.id);
      setEditingFilter(null);
      setTitle(cloned.title); setDescription(cloned.description || '');
      setIsPublicFilter(Boolean(cloned.isPublic));
      setEntityType(normalizeEntityType(cloned.entityType)); setSelectedFields(cloned.fields);
      setOrderByField((cloned.orderBy ?? '').trim());
      setOrderByDirection(cloned.orderByDirection === 'DESC' ? 'DESC' : DEFAULT_ORDER_BY_DIRECTION);
      setCriteria(cloned.criteria.length > 0 ? cloned.criteria : [emptyCriterion()]);
      const canUseQueryString = allowCustomQueryString && !!cloned.filterQueryString;
      setFilterQueryString(canUseQueryString ? (cloned.filterQueryString || '') : '');
      setCreationMode(canUseQueryString ? 'queryString' : 'manual');
      setQueryStringApplied(canUseQueryString);
      setViewMode('create');
    } catch { toast.error('Failed to load filter for cloning.'); }
  };

  const handleApplyQueryString = async () => {
    if (!allowCustomQueryString) return;
    if (!filterQueryString.trim()) {
      toast.error('Please enter a filter query string first.');
      return;
    }
    setIsApplyingQueryString(true);
    try {
      const parsed = await parseFilterQueryString(workspaceId, { filterQueryString: filterQueryString.trim() });
      setSelectedFields(parsed.fields);
      setOrderByField((parsed.orderBy ?? '').trim());
      setOrderByDirection(parsed.orderByDirection === 'DESC' ? 'DESC' : DEFAULT_ORDER_BY_DIRECTION);
      setCriteria(parsed.criteria.length > 0 ? parsed.criteria : [emptyCriterion()]);
      setFilterQueryString(parsed.filterQueryString);
      setQueryStringApplied(true);
      toast.success('Query string parsed and applied.');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message || 'Failed to parse query string.');
    } finally {
      setIsApplyingQueryString(false);
    }
  };

  const handleCopyAsString = async (f: Filter) => {
    try {
      const serialized = await getFilterQueryString(workspaceId, f.id);
      await navigator.clipboard.writeText(serialized);
      toast.success('Filter query string copied.');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message || 'Failed to copy filter query string.');
    }
  };

  const handleDeleteRequest = (f: Filter) => setFilterToDelete(f);
  const handleDeleteConfirm = async () => {
    if (!filterToDelete) return;
    setIsDeleting(true);
    try {
      await deleteFilter(workspaceId, filterToDelete.id);
      setFilters(prev => prev.filter(f => f.id !== filterToDelete.id));
      toast.success(`Filter template "${filterToDelete.title}" deleted.`);
      setFilterToDelete(null);
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message || 'Failed to delete filter template.');
    } finally { setIsDeleting(false); }
  };

  const toggleField = (field: string) => {
    setSelectedFields(prev => prev.includes(field) ? prev.filter(f => f !== field) : [...prev, field]);
  };
  const addField = (field: string) => {
    setSelectedFields(prev => prev.includes(field) ? prev : [...prev, field]);
  };
  const moveField = (sourceField: string, targetField: string) => {
    if (!sourceField || !targetField || sourceField === targetField) return;
    setSelectedFields(prev => {
      const sourceIndex = prev.indexOf(sourceField);
      const targetIndex = prev.indexOf(targetField);
      if (sourceIndex < 0 || targetIndex < 0) return prev;
      const next = [...prev];
      const [moved] = next.splice(sourceIndex, 1);
      next.splice(targetIndex, 0, moved);
      return next;
    });
  };
  const moveFieldToEnd = (sourceField: string) => {
    if (!sourceField) return;
    setSelectedFields(prev => {
      const sourceIndex = prev.indexOf(sourceField);
      if (sourceIndex < 0 || sourceIndex === prev.length - 1) return prev;
      const next = [...prev];
      const [moved] = next.splice(sourceIndex, 1);
      next.push(moved);
      return next;
    });
  };

  const updateCriterion = (index: number, updates: Partial<FilterCriteriaClause>) => {
    setCriteria(prev => prev.map((c, i) => i === index ? { ...c, ...updates } : c));
  };

  const isQueryStringMode = allowCustomQueryString && creationMode === 'queryString';
  const dynamicFieldOptions = [
    ...availableFields.map(field => ({ name: field.name, label: field.label || field.name, fromMetadata: true })),
    ...selectedFields
      .filter(field => !CUSTOM_PSEUDO_FIELDS.includes(field))
      .filter(field => !availableFields.some(option => option.name === field))
      .map(field => ({ name: field, label: field, fromMetadata: false })),
  ];
  const selectableFieldOptions = dynamicFieldOptions.filter(field => !selectedFields.includes(field.name));
  const filteredFieldOptions = selectableFieldOptions.filter(field =>
    field.label.toLowerCase().includes(fieldSearch.toLowerCase()) ||
    field.name.toLowerCase().includes(fieldSearch.toLowerCase())
  );
  const orderByFieldOptions = orderByField && !dynamicFieldOptions.some(field => field.name === orderByField)
    ? [...dynamicFieldOptions, { name: orderByField, label: orderByField, fromMetadata: false }]
    : dynamicFieldOptions;

  const getSelectedFieldLabel = (fieldName: string): string => {
    if (fieldName === TRIAGE_SLA_FIELD) return `🚦 ${TRIAGE_SLA_FIELD}`;
    const fieldMeta = dynamicFieldOptions.find(option => option.name === fieldName);
    return fieldMeta?.label || fieldName;
  };

  const isFormValid = title.trim() !== '' && creationMode !== null && selectedFields.length > 0
    && criteria.length > 0
    && criteria.every(c => c.field.trim() !== '' && criterionHasRequiredValue(c))
    && (!isQueryStringMode || (filterQueryString.trim() !== '' && queryStringApplied));

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;
    setIsSaving(true);
    try {
      // Filter out empty values; keep IDs as-is (they may look like "123456789" or "phase.defect.new")
      const cleanedCriteria = criteria.map(c => ({
        ...c,
        values: isEmptyOperator(c.operator)
          ? []
          : c.values.map(v => v.trim()).filter(v => v !== '')
      }));
      const normalizedFilterQueryString = isQueryStringMode ? filterQueryString.trim() : '';
      if (viewMode === 'edit' && editingFilter) {
        const payload: FilterUpdatePayload = {
          title,
          description,
          entityType,
          fields: selectedFields,
          criteria: cleanedCriteria,
          isPublic: isPublicFilter,
          orderBy: orderByField.trim() || undefined,
          orderByDirection: orderByField.trim() ? orderByDirection : undefined,
          filterQueryString: normalizedFilterQueryString || undefined
        };
        await updateFilter(workspaceId, editingFilter.id, payload);
        toast.success('Filter template updated!');
      } else {
        const payload: FilterCreatePayload = {
          title,
          description,
          entityType,
          fields: selectedFields,
          criteria: cleanedCriteria,
          isPublic: isPublicFilter,
          orderBy: orderByField.trim() || undefined,
          orderByDirection: orderByField.trim() ? orderByDirection : undefined,
          filterQueryString: normalizedFilterQueryString || undefined
        };
        await createFilter(workspaceId, payload);
        toast.success('Filter template saved!');
      }
      await loadFilters(); resetForm(); setViewMode('list');
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message || 'Failed to save filter template.');
    } finally { setIsSaving(false); }
  };

  const handleExecute = async (filterId: string) => {
    const scrollToExecutedFilter = () => {
      requestAnimationFrame(() => {
        filterCardRefs.current[filterId]?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    };

    setExecuteState(prev => ({ ...prev, [filterId]: { isExecuting: true, results: null, expanded: true, aiSummaryGenerated: false } }));
    try {
      const response: PreviewResponse = await previewFilter(workspaceId, filterId, 10);
      setExecuteState(prev => ({ ...prev, [filterId]: { isExecuting: false, results: response.records, expanded: true, aiSummaryGenerated: response.aiSummaryGenerated } }));
      toast.success(`Preview returned ${response.records.length} result(s).`);
      scrollToExecutedFilter();
    } catch (err: unknown) {
      setExecuteState(prev => ({ ...prev, [filterId]: { isExecuting: false, results: [], expanded: true, aiSummaryGenerated: false } }));
      const axiosErr = err as { response?: { data?: { message?: string } } };
      toast.error(axiosErr.response?.data?.message ? `Preview failed: ${axiosErr.response.data.message}` : 'Failed to preview filter.');
      scrollToExecutedFilter();
    }
  };

  const toggleResultsPanel = (filterId: string) => {
    setExecuteState(prev => ({
      ...prev,
      [filterId]: { ...(prev[filterId] ?? { isExecuting: false, results: null, aiSummaryGenerated: false }), expanded: !(prev[filterId]?.expanded ?? false) },
    }));
  };

  const parseCriteria = (criteriaJson: string): FilterCriteriaClause[] => {
    try { return JSON.parse(criteriaJson); } catch { return []; }
  };
  const parseFields = (fieldsJson: string): string[] => {
    try { return JSON.parse(fieldsJson); } catch { return []; }
  };
  const parseOrderBy = (field?: string | null, direction?: string | null): string => {
    const normalizedField = field?.trim();
    if (!normalizedField) return '';
    const normalizedDirection = direction === 'DESC' ? 'DESC' : 'ASC';
    return `${normalizedField} (${normalizedDirection})`;
  };

  const canEditFilter = (filter: Filter): boolean => {
    if (typeof filter.editable === 'boolean') {
      return filter.editable;
    }
    return canManageAllFilters;
  };

  const resolvePublicTemplate = (filter: Filter): boolean => {
    if (typeof filter.publicTemplate === 'boolean') {
      return filter.publicTemplate;
    }
    if (typeof filter.isPublic === 'boolean') {
      return filter.isPublic;
    }
    return !filter.ownerEmail;
  };

  const creatorLabel = (filter: Filter): string => {
    if (filter.ownerEmail && filter.ownerEmail.trim() !== '') {
      return filter.ownerEmail;
    }
    return 'Legacy admin template';
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-[60vh]">
        <div className="h-14 w-14 rounded-2xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shadow-xl shadow-indigo-500/30 mb-5 animate-pulse-glow">
          <Loader2 className="h-7 w-7 animate-spin text-white" />
        </div>
        <p className="text-slate-500 dark:text-slate-400 text-sm font-medium">Loading filter templates…</p>
      </div>
    );
  }

  /* ---- Create / Edit form ---- */
  if (viewMode === 'create' || viewMode === 'edit') {
    return (
      <div className="max-w-3xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        <div className="mb-6 flex items-center gap-3 animate-fade-in">
          <button
            onClick={() => { resetForm(); setViewMode('list'); }}
            className="p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer"
          >
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">
              {viewMode === 'edit' ? 'Edit Filter Template' : 'Create Filter Template'}
            </h1>
            <p className="text-slate-400 dark:text-slate-500 text-sm mt-0.5">Define query criteria and fields to fetch</p>
          </div>
        </div>

        <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm animate-slide-up">
          <div className="p-6">
            <form onSubmit={handleSave} className="space-y-6">

              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Template Name</label>
                <input
                  type="text" required value={title}
                  onChange={e => setTitle(e.target.value)}
                  className={inputClass} placeholder="e.g. Open Defects — My Product"
                />
              </div>

              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Description</label>
                <textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  rows={2}
                  className={inputClass}
                  placeholder="Optional description of what this filter returns…"
                />
              </div>

              <div className="space-y-1.5">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Entity Type</label>
                <select
                  value={entityType}
                  onChange={e => setEntityType(e.target.value)}
                  className={inputClass}
                >
                  {ENTITY_TYPES.map(t => (
                    <option key={t.value} value={t.value}>{t.label}</option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Visibility</label>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  <button
                    type="button"
                    onClick={() => setIsPublicFilter(false)}
                    className={`text-left rounded-xl border p-4 transition-colors cursor-pointer ${
                      !isPublicFilter
                        ? 'border-indigo-400 bg-indigo-50 dark:bg-indigo-500/10'
                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800'
                    }`}
                  >
                    <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Private</p>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Only you can see and subscribe to this filter.
                    </p>
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsPublicFilter(true)}
                    className={`text-left rounded-xl border p-4 transition-colors cursor-pointer ${
                      isPublicFilter
                        ? 'border-indigo-400 bg-indigo-50 dark:bg-indigo-500/10'
                        : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800'
                    }`}
                  >
                    <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Public</p>
                    <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                      Everyone in this workspace can see and subscribe to this filter.
                    </p>
                  </button>
                </div>
              </div>

              {allowCustomQueryString && (
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Step 1: Choose Creation Workflow</label>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                    <button
                      type="button"
                      onClick={() => setCreationMode('queryString')}
                      className={`text-left rounded-xl border p-4 transition-colors cursor-pointer ${
                        creationMode === 'queryString'
                          ? 'border-indigo-400 bg-indigo-50 dark:bg-indigo-500/10'
                          : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800'
                      }`}
                    >
                      <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Generate filter from query string</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                        Paste `fields=...&query=...`, validate it, and auto-generate fields + criteria.
                      </p>
                    </button>
                    <button
                      type="button"
                      onClick={() => setCreationMode('manual')}
                      className={`text-left rounded-xl border p-4 transition-colors cursor-pointer ${
                        creationMode === 'manual'
                          ? 'border-indigo-400 bg-indigo-50 dark:bg-indigo-500/10'
                          : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800'
                      }`}
                    >
                      <p className="text-sm font-semibold text-slate-800 dark:text-slate-100">Manually create a filter</p>
                      <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                        Pick fields and define criteria clause-by-clause.
                      </p>
                    </button>
                  </div>
                </div>
              )}

              {allowCustomQueryString && creationMode === null ? (
                <div className="rounded-xl border border-dashed border-slate-300 dark:border-slate-700 bg-slate-50/60 dark:bg-slate-800/30 p-4">
                  <p className="text-sm text-slate-600 dark:text-slate-300">
                    Select a workflow above to continue creating this filter.
                  </p>
                </div>
              ) : isQueryStringMode ? (
                <div className="space-y-2">
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Step 2: Generate Filter From Query String</label>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Filter Query String</label>
                  <div className="border border-slate-200 dark:border-slate-700 rounded-xl p-4 bg-slate-50/70 dark:bg-slate-800/40 space-y-2">
                    <textarea
                      value={filterQueryString}
                      onChange={e => {
                        setFilterQueryString(e.target.value);
                        setQueryStringApplied(false);
                      }}
                      rows={2}
                      className={inputClass}
                      placeholder="fields=id,name&query=name EQ ^*Case360*^"
                    />
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-xs text-slate-400 dark:text-slate-500">
                        Format: fields=field1,field2&query=field EQ ^value^ [AND ...]
                      </p>
                      <button
                        type="button"
                        onClick={handleApplyQueryString}
                        disabled={isApplyingQueryString}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-indigo-700 dark:text-indigo-300 bg-white dark:bg-slate-900 border border-indigo-200 dark:border-indigo-700 hover:bg-indigo-50 dark:hover:bg-indigo-500/10 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
                      >
                        {isApplyingQueryString ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : null}
                        Apply String
                      </button>
                    </div>
                    {!queryStringApplied && filterQueryString.trim() && (
                      <p className="text-xs text-amber-600 dark:text-amber-400">
                        Apply the string to validate and generate the filter before saving.
                      </p>
                    )}
                  </div>
                  {queryStringApplied && (
                    <div className="space-y-3">
                      <div className="border border-slate-200 dark:border-slate-700 rounded-xl p-4 bg-slate-50/70 dark:bg-slate-800/40">
                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400 mb-2">
                          Generated Fields
                        </p>
                        <p className="text-[11px] text-slate-400 dark:text-slate-500 mb-2">
                          Drag chips to set the final column order for preview and email output.
                        </p>
                        <div
                          className="flex flex-wrap gap-1.5 min-h-8"
                          onDragOver={e => e.preventDefault()}
                          onDrop={e => {
                            e.preventDefault();
                            const sourceField = draggedField ?? e.dataTransfer.getData('text/plain');
                            moveFieldToEnd(sourceField);
                            setDraggedField(null);
                          }}
                        >
                          {selectedFields.map(field => (
                            <div
                              key={field}
                              draggable
                              onDragStart={e => {
                                setDraggedField(field);
                                e.dataTransfer.effectAllowed = 'move';
                                e.dataTransfer.setData('text/plain', field);
                              }}
                              onDragEnd={() => setDraggedField(null)}
                              onDragOver={e => e.preventDefault()}
                              onDrop={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                const sourceField = draggedField ?? e.dataTransfer.getData('text/plain');
                                moveField(sourceField, field);
                                setDraggedField(null);
                              }}
                              className="inline-flex items-center gap-1 px-2 py-0.5 rounded-md bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 text-xs border border-slate-200 dark:border-slate-700 cursor-move"
                            >
                              <span className="text-slate-400 dark:text-slate-500">⋮⋮</span>
                              <span>{getSelectedFieldLabel(field)}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                      <div className="border border-slate-200 dark:border-slate-700 rounded-xl p-4 bg-slate-50/70 dark:bg-slate-800/40">
                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400 mb-2">
                          Generated Criteria
                        </p>
                        <div className="text-xs text-slate-500 dark:text-slate-400 space-y-1">
                          {criteria.map((criterion, ci) => (
                            <p key={`${criterion.field}-${ci}`}>
                              {ci > 0 ? 'AND ' : ''}
                              <span className="font-semibold text-slate-700 dark:text-slate-200">{criterion.field}</span>{' '}
                              {formatCriterionSummary(criterion)}
                            </p>
                          ))}
                        </div>
                      </div>
                    </div>
                  )}
                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Order by</label>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <select
                        value={orderByField}
                        onChange={e => setOrderByField(e.target.value)}
                        className={inputClass}
                      >
                        <option value="">None</option>
                        {orderByFieldOptions.map(field => (
                          <option key={field.name} value={field.name}>
                            {field.label}
                          </option>
                        ))}
                      </select>
                      <select
                        value={orderByDirection}
                        onChange={e => setOrderByDirection(e.target.value as OrderByDirection)}
                        className={inputClass}
                        disabled={!orderByField}
                      >
                        <option value="ASC">Ascending</option>
                        <option value="DESC">Descending</option>
                      </select>
                    </div>
                  </div>
                </div>
              ) : (
                <>
                  <div className="space-y-2">
                    <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">{allowCustomQueryString ? 'Step 2: Choose Fields to Fetch' : 'Fields to Fetch'}</label>
                    <div className="rounded-xl border border-slate-200 dark:border-slate-700 bg-slate-50/70 dark:bg-slate-800/40 p-3 space-y-2">
                      <div className="relative">
                        <Search className="h-3.5 w-3.5 absolute left-3 top-1/2 -translate-y-1/2 text-slate-400" />
                        <input
                          type="text"
                          value={fieldSearch}
                          onChange={e => setFieldSearch(e.target.value)}
                          placeholder="Search fields and click to add…"
                          className={`${inputClass} pl-8 pr-9`}
                        />
                        {fieldSearch.trim() !== '' && (
                          <button
                            type="button"
                            onClick={() => setFieldSearch('')}
                            className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700/60 transition-colors"
                            aria-label="Clear field search"
                          >
                            <X className="h-3.5 w-3.5" />
                          </button>
                        )}
                      </div>
                      {fieldSearch.trim() !== '' && (
                        <div className="max-h-40 overflow-y-auto flex flex-wrap gap-1.5">
                          {filteredFieldOptions.length === 0 ? (
                            <p className="text-xs text-slate-400 dark:text-slate-500">No matching fields.</p>
                          ) : (
                            filteredFieldOptions.slice(0, 60).map(field => (
                              <button
                                key={field.name}
                                type="button"
                                title={field.label === field.name ? field.name : `${field.label} (${field.name})`}
                                onClick={() => addField(field.name)}
                                className="px-2.5 py-1 rounded-full text-xs font-medium border bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-300 border-slate-200 dark:border-slate-700 hover:border-indigo-300 dark:hover:border-indigo-600 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                              >
                                {field.label}
                                {!field.fromMetadata && (
                                  <span className="ml-1 text-[10px] align-middle opacity-70">(saved)</span>
                                )}
                              </button>
                            ))
                          )}
                        </div>
                      )}
                      <div
                        className="flex flex-wrap gap-2 min-h-10 pt-1"
                        onDragOver={e => e.preventDefault()}
                        onDrop={e => {
                          e.preventDefault();
                          const sourceField = draggedField ?? e.dataTransfer.getData('text/plain');
                          moveFieldToEnd(sourceField);
                          setDraggedField(null);
                        }}
                      >
                        {!selectedFields.includes(AI_SUMMARY_FIELD) && (
                          <FieldBadgeWithPopover
                            label={AI_SUMMARY_FIELD}
                            selected={false}
                            onClick={() => toggleField(AI_SUMMARY_FIELD)}
                            selectedClassName="bg-slate-200 dark:bg-slate-600/70 text-slate-700 dark:text-slate-100 border-slate-300 dark:border-slate-500"
                            unselectedClassName="bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-600 hover:border-violet-300 dark:hover:border-violet-600 hover:text-violet-600 dark:hover:text-violet-400"
                            popoverText="An AI summary of the current progress in the ticket and potential next items"
                          />
                        )}
                        {!selectedFields.includes(TRIAGE_SLA_FIELD) && (
                          <FieldBadgeWithPopover
                            label={`🚦 ${TRIAGE_SLA_FIELD}`}
                            selected={false}
                            onClick={() => toggleField(TRIAGE_SLA_FIELD)}
                            selectedClassName="bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-300 border-amber-300 dark:border-amber-500/40"
                            unselectedClassName="bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 border-slate-200 dark:border-slate-600 hover:border-amber-300 dark:hover:border-amber-600 hover:text-amber-600 dark:hover:text-amber-400"
                            popoverText="A traffic light for SLA compliance of Customer tickets. Make sure that you adjust your filter to only show tickets that need the Triage SLA to be applied."
                          />
                        )}
                        {selectedFields.map(fieldName => {
                          const fieldMeta = dynamicFieldOptions.find(option => option.name === fieldName);
                          const isSavedOnly = fieldMeta?.fromMetadata === false;
                          const isAiSummary = fieldName === AI_SUMMARY_FIELD;
                          const isTriageSla = fieldName === TRIAGE_SLA_FIELD;
                          const selectedChipClass = isAiSummary
                            ? 'bg-slate-200 dark:bg-slate-600/70 text-slate-700 dark:text-slate-100 border-slate-300 dark:border-slate-500'
                            : isTriageSla
                            ? 'bg-amber-100 dark:bg-amber-500/20 text-amber-700 dark:text-amber-300 border-amber-300 dark:border-amber-500/40'
                            : 'bg-indigo-100 dark:bg-indigo-500/20 text-indigo-700 dark:text-indigo-300 border-indigo-200 dark:border-indigo-500/30';
                          const handleClass = isAiSummary
                            ? 'text-slate-400 dark:text-slate-300'
                            : isTriageSla
                            ? 'text-amber-500 dark:text-amber-300'
                            : 'text-indigo-400 dark:text-indigo-300';
                          const removeClass = isAiSummary
                            ? 'text-slate-600 dark:text-slate-200 hover:text-slate-800 dark:hover:text-white'
                            : isTriageSla
                            ? 'text-amber-600 dark:text-amber-300 hover:text-amber-800 dark:hover:text-amber-100'
                            : 'text-indigo-600 dark:text-indigo-300 hover:text-indigo-800 dark:hover:text-indigo-100';
                          return (
                            <div
                              key={fieldName}
                              draggable
                              onDragStart={e => {
                                setDraggedField(fieldName);
                                e.dataTransfer.effectAllowed = 'move';
                                e.dataTransfer.setData('text/plain', fieldName);
                              }}
                              onDragEnd={() => setDraggedField(null)}
                              onDragOver={e => e.preventDefault()}
                              onDrop={e => {
                                e.preventDefault();
                                e.stopPropagation();
                                const sourceField = draggedField ?? e.dataTransfer.getData('text/plain');
                                moveField(sourceField, fieldName);
                                setDraggedField(null);
                              }}
                              className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border cursor-move ${selectedChipClass}`}
                            >
                              <span className={handleClass}>⋮⋮</span>
                              <span>{getSelectedFieldLabel(fieldName)}</span>
                              {isSavedOnly && <span className="text-[10px] align-middle opacity-70">(saved)</span>}
                              <button
                                type="button"
                                onClick={() => toggleField(fieldName)}
                                className={`ml-1 transition-colors ${removeClass}`}
                                title="Remove field"
                                aria-label={`Remove ${fieldName}`}
                              >
                                ×
                              </button>
                            </div>
                          );
                        })}
                      </div>
                    </div>
                    {availableFieldsLoading && (
                      <p className="text-xs text-slate-400 dark:text-slate-500">Loading fields from Octane…</p>
                    )}
                  </div>

                  <div className="space-y-1.5">
                    <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Order by</label>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                      <select
                        value={orderByField}
                        onChange={e => setOrderByField(e.target.value)}
                        className={inputClass}
                      >
                        <option value="">None</option>
                        {orderByFieldOptions.map(field => (
                          <option key={field.name} value={field.name}>
                            {field.label}
                          </option>
                        ))}
                      </select>
                      <select
                        value={orderByDirection}
                        onChange={e => setOrderByDirection(e.target.value as OrderByDirection)}
                        className={inputClass}
                        disabled={!orderByField}
                      >
                        <option value="ASC">Ascending</option>
                        <option value="DESC">Descending</option>
                      </select>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">Filter Criteria</label>
                    <p className="text-xs text-slate-400 dark:text-slate-500">
                      Pick fields from the dropdown — values are automatically loaded from your Octane workspace.
                    </p>
                    <div className="space-y-3">
                      {criteria.map((criterion, ci) => (
                        <SmartFilterRow
                          key={ci}
                          clause={criterion}
                          index={ci}
                          entityType={entityType}
                          workspaceId={workspaceId}
                          onChange={updates => updateCriterion(ci, updates)}
                          onRemove={() => setCriteria(prev => prev.filter((_, i) => i !== ci))}
                          canRemove={criteria.length > 1}
                        />
                      ))}
                      <button type="button" onClick={() => setCriteria(prev => [...prev, emptyCriterion()])}
                        className="flex items-center gap-1.5 text-sm text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium transition-colors cursor-pointer">
                        <Plus className="h-4 w-4" />
                        Add Filter Row
                      </button>
                    </div>
                  </div>
                </>
              )}

              <div className="pt-2 flex gap-3">
                <button
                  type="button"
                  onClick={() => { resetForm(); setViewMode('list'); }}
                  className="flex-1 py-2.5 px-4 border border-slate-200 dark:border-slate-700 rounded-xl text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700/60 transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!isFormValid || isSaving}
                  className="flex-1 flex justify-center items-center gap-2 py-2.5 px-4 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all cursor-pointer"
                >
                  {isSaving ? <Loader2 className="h-4 w-4 animate-spin" /> : viewMode === 'edit' ? 'Update Template' : 'Save Template'}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    );
  }

  /* ---- List view ---- */
  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      <div className="mb-8 flex items-center justify-between animate-fade-in">
        <div className="flex items-center gap-3">
          <button onClick={onBack}
            className="p-2 rounded-xl hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200 cursor-pointer">
            <ArrowLeft className="h-5 w-5" />
          </button>
          <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">Filter Templates</h1>
            <p className="text-slate-400 dark:text-slate-500 text-sm mt-0.5">Pre-built queries for your subscriptions</p>
          </div>
        </div>
        {canCreateFilters && (
          <button
            onClick={handleCreateNew}
            className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl shadow-sm shadow-indigo-500/20 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 dark:focus:ring-offset-slate-950 transition-all cursor-pointer"
          >
            <Plus className="h-4 w-4" />
            New Filter
          </button>
        )}
      </div>

      {filters.length === 0 ? (
        <div className="bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm p-14 text-center animate-scale-in">
          <div className="h-14 w-14 rounded-2xl bg-slate-50 dark:bg-slate-800 flex items-center justify-center mx-auto mb-5">
            <SlidersHorizontal className="h-7 w-7 text-slate-300 dark:text-slate-600" />
          </div>
          <p className="text-slate-500 dark:text-slate-400 text-sm mb-1 font-medium">No filter templates yet</p>
          {canCreateFilters && (
            <>
              <p className="text-slate-400 dark:text-slate-500 text-xs mb-6">Create your first filter template to get started.</p>
              <button onClick={handleCreateNew}
                className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 dark:bg-indigo-500 dark:hover:bg-indigo-400 text-white text-sm font-semibold rounded-xl transition-all cursor-pointer">
                <Plus className="h-4 w-4" />
                Create first filter
              </button>
            </>
          )}
        </div>
      ) : (
        <div className="space-y-3">
          {filters.map((f, idx) => {
            const criteriaList = parseCriteria(f.criteria);
            const fieldsList = parseFields(f.fields);
            const orderBy = parseOrderBy(f.orderBy, f.orderByDirection);
            const exState = executeState[f.id];
            const hasResults = !exState?.isExecuting && exState?.results != null && exState.results.length > 0;

            return (
              <div
                key={f.id}
                ref={(el) => { filterCardRefs.current[f.id] = el; }}
                style={{ animationDelay: `${idx * 50}ms` }}
                className="animate-slide-up bg-white dark:bg-slate-900 rounded-2xl border border-slate-100 dark:border-slate-800 shadow-sm hover:shadow-md dark:hover:shadow-slate-900/50 hover:border-slate-200 dark:hover:border-slate-700 transition-all overflow-hidden"
              >
                <div className="p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap mb-1">
                        <h3 className="font-semibold text-slate-900 dark:text-white text-sm">{f.title}</h3>
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-indigo-50 dark:bg-indigo-500/10 text-indigo-700 dark:text-indigo-300 border border-indigo-100 dark:border-indigo-500/20">
                          {toEntityTypeLabel(f.entityType)}
                        </span>
                        {resolvePublicTemplate(f) ? (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700">
                            Public template
                          </span>
                        ) : (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold uppercase tracking-wide bg-violet-100 dark:bg-violet-500/20 text-violet-700 dark:text-violet-300 border border-violet-200 dark:border-violet-500/30">
                            Private template
                          </span>
                        )}
                      </div>
                      {f.description && (
                        <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5 mb-2">{f.description}</p>
                      )}
                      {canManageAllFilters && (
                        <p className="text-[11px] text-slate-500 dark:text-slate-400 mb-2">
                          Created by: <span className="font-medium text-slate-600 dark:text-slate-300">{creatorLabel(f)}</span>
                        </p>
                      )}
                      <div className="mt-1.5 flex flex-wrap gap-1">
                        {fieldsList.slice(0, 8).map(field => (
                          <span key={field} className="inline-block px-2 py-0.5 rounded-md bg-slate-50 dark:bg-slate-800 text-slate-500 dark:text-slate-400 text-xs border border-slate-100 dark:border-slate-700">
                            {field}
                          </span>
                        ))}
                        {fieldsList.length > 8 && (
                          <span className="inline-block px-2 py-0.5 rounded-md bg-slate-50 dark:bg-slate-800 text-slate-400 dark:text-slate-500 text-xs border border-slate-100 dark:border-slate-700">
                            +{fieldsList.length - 8} more
                          </span>
                        )}
                        {orderBy && (
                          <span className="inline-block px-2 py-0.5 rounded-md bg-emerald-50 dark:bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 text-xs border border-emerald-100 dark:border-emerald-500/20">
                            Order by: {orderBy}
                          </span>
                        )}
                      </div>
                      {criteriaList.length > 0 && (
                        <div className="mt-2 text-xs text-slate-400 dark:text-slate-500">
                          {criteriaList.map((c, i) => (
                            <span key={i}>
                              {i > 0 && (
                                <span className="text-indigo-400 dark:text-indigo-500 mx-1 font-medium uppercase">
                                  {c.logicalOperator ?? 'AND'}
                                </span>
                              )}
                              <span className="font-medium text-slate-500 dark:text-slate-400">{c.field}</span>{' '}
                              <span className="text-slate-400 dark:text-slate-500">{formatCriterionSummary(c)}</span>
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    <div className="flex items-center gap-1.5 shrink-0 flex-wrap justify-end">
                      {canEditFilter(f) && (
                        <>
                          <button onClick={() => handleEdit(f)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-indigo-200 dark:hover:border-indigo-700 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors cursor-pointer">
                            <Pencil className="h-3.5 w-3.5" />Edit
                          </button>
                          <button onClick={() => handleClone(f)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-violet-200 dark:hover:border-violet-700 hover:text-violet-600 dark:hover:text-violet-400 transition-colors cursor-pointer">
                            <Copy className="h-3.5 w-3.5" />Clone
                          </button>
                          {allowCustomQueryString && (
                            <button onClick={() => handleCopyAsString(f)}
                              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700/60 hover:border-cyan-200 dark:hover:border-cyan-700 hover:text-cyan-600 dark:hover:text-cyan-400 transition-colors cursor-pointer">
                              <Copy className="h-3.5 w-3.5" />Copy String
                            </button>
                          )}
                          <button onClick={() => handleDeleteRequest(f)}
                            disabled={isDeleting && filterToDelete?.id === f.id}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-rose-600 dark:text-rose-400 bg-white dark:bg-slate-800 border border-rose-100 dark:border-rose-500/20 hover:bg-rose-50 dark:hover:bg-rose-500/10 hover:border-rose-200 dark:hover:border-rose-500/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer">
                            {isDeleting && filterToDelete?.id === f.id
                              ? <Loader2 className="h-3.5 w-3.5 animate-spin" />
                              : <Trash2 className="h-3.5 w-3.5" />
                            }Delete
                          </button>
                        </>
                      )}
                      <button
                        onClick={() => handleExecute(f.id)}
                        disabled={exState?.isExecuting}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium text-emerald-700 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-500/10 border border-emerald-100 dark:border-emerald-500/20 hover:bg-emerald-100 dark:hover:bg-emerald-500/20 disabled:opacity-50 disabled:cursor-not-allowed transition-colors cursor-pointer"
                      >
                        {exState?.isExecuting
                          ? <><Loader2 className="h-3.5 w-3.5 animate-spin" />{fieldsList.includes(AI_SUMMARY_FIELD) ? 'Generating…' : 'Loading…'}</>
                          : <><Eye className="h-3.5 w-3.5" />Preview</>
                        }
                      </button>
                    </div>
                  </div>
                  {/* Show/hide toggle — bottom-right, only when preview returned results */}
                  {hasResults && (
                    <div className="flex justify-end pt-2">
                      <button
                        onClick={() => toggleResultsPanel(f.id)}
                        aria-label={exState?.expanded ? 'Hide preview results' : 'Show preview results'}
                        className="inline-flex items-center gap-1.5 text-xs text-slate-400 dark:text-slate-500 hover:text-slate-600 dark:hover:text-slate-300 transition-colors cursor-pointer select-none"
                      >
                        {exState?.expanded
                          ? <><ChevronUp className="h-3.5 w-3.5" />Hide results</>
                          : <><ChevronDown className="h-3.5 w-3.5" />Show results</>
                        }
                      </button>
                    </div>
                  )}
                </div>

                {/* Empty state — always visible after a preview that returned no results */}
                {!exState?.isExecuting && exState?.results != null && exState.results.length === 0 && (
                  <div className="border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/30">
                    <div className="p-6 text-center text-sm text-slate-400 dark:text-slate-500">No matching results found.</div>
                  </div>
                )}
                {/* Results panel — only when results exist and the toggle is expanded */}
                {hasResults && exState?.expanded && (
                  <div className="border-t border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/30">
                    <div className="px-5 py-2.5 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
                      <span className="text-xs font-semibold text-slate-500 dark:text-slate-400">
                        Preview — {exState.results!.length} item{exState.results!.length !== 1 ? 's' : ''} (max 10)
                        {exState.aiSummaryGenerated && (
                          <span className="ml-2 font-normal text-violet-500 dark:text-violet-400">· includes AI summaries</span>
                        )}
                      </span>
                    </div>
                    <div className="overflow-x-auto max-h-[32rem]">
                      <table className="min-w-full divide-y divide-slate-100 dark:divide-slate-800 text-xs table-auto">
                        <thead className="bg-slate-100/70 dark:bg-slate-800/60 sticky top-0 z-10">
                          <tr>
                            {fieldsList.map(col => (
                              <th
                                key={col}
                                className={`px-4 py-2.5 text-left font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider whitespace-nowrap ${
                                  col === AI_SUMMARY_FIELD || col === 'name' || col === 'description'
                                    ? 'min-w-[220px]'
                                    : col === TRIAGE_SLA_FIELD
                                    ? 'min-w-[140px]'
                                    : col === 'id'
                                    ? 'min-w-[60px]'
                                    : 'min-w-[90px]'
                                }`}
                              >
                                {col === AI_SUMMARY_FIELD
                                  ? '✨ AI Summary'
                                  : col === TRIAGE_SLA_FIELD
                                  ? '🚦 Triage SLA'
                                  : col.replace(/_/g, ' ')}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody className="divide-y divide-slate-100 dark:divide-slate-800 bg-white dark:bg-slate-900">
                          {exState.results!.map((row, ri) => (
                            <tr key={ri} className="hover:bg-slate-50 dark:hover:bg-slate-800/50 transition-colors align-top">
                              {fieldsList.map(col => {
                                const display = row[col] ?? '—';
                                // Render global_id_udf as a clickable hyperlink to the ValueEdge ticket
                                if (col === 'global_id_udf' && display !== '—') {
                                  const href = `https://rdapps.otxlab.net/value-edge-api/forwardTo?id=${display}`;
                                  return (
                                    <td
                                      key={col}
                                      className="px-4 py-2 text-slate-600 dark:text-slate-300 whitespace-normal break-words"
                                      style={{ overflowWrap: 'anywhere' }}
                                    >
                                      <a
                                        href={href}
                                        target="_blank"
                                        rel="noopener noreferrer"
                                        className="text-indigo-600 dark:text-indigo-400 hover:underline"
                                      >
                                        {String(display)}
                                      </a>
                                    </td>
                                  );
                                }
                                return (
                                  <td
                                    key={col}
                                    className="px-4 py-2 text-slate-600 dark:text-slate-300 whitespace-normal break-words"
                                    style={{ overflowWrap: 'anywhere' }}
                                    dangerouslySetInnerHTML={{ __html: String(display).replace(/\n/g, '<br>') }}
                                  >
                                  </td>
                                );
                              })}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}

      <ConfirmDialog
        isOpen={filterToDelete !== null}
        title="Delete Filter Template"
        message={filterToDelete ? `Are you sure you want to delete "${filterToDelete.title}"? This cannot be undone.` : ''}
        confirmLabel="Delete"
        onConfirm={handleDeleteConfirm}
        onCancel={() => setFilterToDelete(null)}
        isLoading={isDeleting}
        variant="danger"
      />
    </div>
  );
};

export default FilterBuilderView;
