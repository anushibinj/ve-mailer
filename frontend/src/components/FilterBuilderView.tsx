import React, { useEffect, useState } from 'react';
import {
  fetchFilters,
  createFilter,
  updateFilter,
  executeFilter,
  type Filter,
  type FilterCriteriaClause,
  type FilterCreatePayload,
  type FilterUpdatePayload
} from '../services/apiService';
import { Loader2, ArrowLeft, Plus, Trash2, Play, Pencil, ChevronDown, ChevronUp } from 'lucide-react';
import toast from 'react-hot-toast';

interface FilterBuilderViewProps {
  workspaceId: string;
  onBack: () => void;
}

type ViewMode = 'list' | 'create' | 'edit';

const ENTITY_TYPES = ['defect', 'story', 'feature', 'quality_story', 'epic'];

const COMMON_FIELDS = [
  'id', 'global_id_udf', 'name', 'phase', 'owner',
  'product_udf', 'customer_udf', 'defect_type', 'severity',
  'priority', 'release', 'team', 'sprint', 'creation_time',
  'last_modified', 'detected_by', 'story_points', 'subtype'
];

const OPERATORS = [
  { value: 'EQUAL_TO', label: 'Equal To' },
  { value: 'IN', label: 'In' },
];

const emptyCriterion = (): FilterCriteriaClause => ({
  field: '',
  operator: 'EQUAL_TO',
  negate: false,
  values: [''],
});

const defaultFields = ['id', 'name', 'phase', 'owner'];

const FilterBuilderView: React.FC<FilterBuilderViewProps> = ({ workspaceId, onBack }) => {
  const [filters, setFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [viewMode, setViewMode] = useState<ViewMode>('list');
  const [editingFilter, setEditingFilter] = useState<Filter | null>(null);

  // Per-card execute state: filterId -> { isExecuting, results }
  const [executeState, setExecuteState] = useState<Record<string, {
    isExecuting: boolean;
    results: any[] | null;
    expanded: boolean;
  }>>({});

  // Form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [entityType, setEntityType] = useState('defect');
  const [selectedFields, setSelectedFields] = useState<string[]>(defaultFields);
  const [criteria, setCriteria] = useState<FilterCriteriaClause[]>([emptyCriterion()]);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    loadFilters();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [workspaceId]);

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

  // ---- Form helpers ----

  const resetForm = () => {
    setTitle('');
    setDescription('');
    setEntityType('defect');
    setSelectedFields(defaultFields);
    setCriteria([emptyCriterion()]);
    setEditingFilter(null);
  };

  const populateFormFromFilter = (f: Filter) => {
    setTitle(f.title);
    setDescription(f.description || '');
    setEntityType(f.entityType);
    try {
      setSelectedFields(JSON.parse(f.fields));
    } catch {
      setSelectedFields(defaultFields);
    }
    try {
      const parsed: FilterCriteriaClause[] = JSON.parse(f.criteria);
      setCriteria(parsed.length > 0 ? parsed : [emptyCriterion()]);
    } catch {
      setCriteria([emptyCriterion()]);
    }
  };

  const handleCreateNew = () => {
    resetForm();
    setViewMode('create');
  };

  const handleEdit = (f: Filter) => {
    setEditingFilter(f);
    populateFormFromFilter(f);
    setViewMode('edit');
  };

  const handleBackToList = () => {
    resetForm();
    setViewMode('list');
  };

  const toggleField = (field: string) => {
    setSelectedFields(prev =>
      prev.includes(field)
        ? prev.filter(f => f !== field)
        : [...prev, field]
    );
  };

  const updateCriterion = (index: number, updates: Partial<FilterCriteriaClause>) => {
    setCriteria(prev => prev.map((c, i) => i === index ? { ...c, ...updates } : c));
  };

  const addCriterion = () => {
    setCriteria(prev => [...prev, emptyCriterion()]);
  };

  const removeCriterion = (index: number) => {
    setCriteria(prev => prev.filter((_, i) => i !== index));
  };

  const updateCriterionValue = (criterionIndex: number, valueIndex: number, newValue: string) => {
    setCriteria(prev => prev.map((c, i) => {
      if (i !== criterionIndex) return c;
      const newValues = [...c.values];
      newValues[valueIndex] = newValue;
      return { ...c, values: newValues };
    }));
  };

  const addValueToCriterion = (criterionIndex: number) => {
    setCriteria(prev => prev.map((c, i) => {
      if (i !== criterionIndex) return c;
      return { ...c, values: [...c.values, ''] };
    }));
  };

  const removeValueFromCriterion = (criterionIndex: number, valueIndex: number) => {
    setCriteria(prev => prev.map((c, i) => {
      if (i !== criterionIndex) return c;
      return { ...c, values: c.values.filter((_, vi) => vi !== valueIndex) };
    }));
  };

  const isFormValid = title.trim() !== '' && selectedFields.length > 0
    && criteria.length > 0
    && criteria.every(c => c.field.trim() !== '' && c.values.length > 0 && c.values.every(v => v.trim() !== ''));

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isFormValid) return;

    setIsSaving(true);
    try {
      const cleanedCriteria = criteria.map(c => ({
        ...c,
        values: c.values.map(v => v.trim()),
      }));

      if (viewMode === 'edit' && editingFilter) {
        const payload: FilterUpdatePayload = {
          title,
          description,
          entityType,
          fields: selectedFields,
          criteria: cleanedCriteria,
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
        };
        await createFilter(workspaceId, payload);
        toast.success('Filter template saved!');
      }

      await loadFilters();
      resetForm();
      setViewMode('list');
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to save filter template.');
    } finally {
      setIsSaving(false);
    }
  };

  // ---- Execute per card ----

  const handleExecute = async (filterId: string) => {
    setExecuteState(prev => ({
      ...prev,
      [filterId]: { isExecuting: true, results: null, expanded: true },
    }));
    try {
      const results = await executeFilter(workspaceId, filterId);
      setExecuteState(prev => ({
        ...prev,
        [filterId]: { isExecuting: false, results, expanded: true },
      }));
      toast.success(`Query returned ${results.length} result(s).`);
    } catch (err: any) {
      setExecuteState(prev => ({
        ...prev,
        [filterId]: { isExecuting: false, results: [], expanded: true },
      }));
      if (err.response?.data?.message) {
        toast.error(`Execute failed: ${err.response.data.message}`);
      } else {
        toast.error('Failed to execute filter. Please check your connection and try again.');
      }
    }
  };

  const toggleResultsPanel = (filterId: string) => {
    setExecuteState(prev => ({
      ...prev,
      [filterId]: {
        ...(prev[filterId] ?? { isExecuting: false, results: null }),
        expanded: !(prev[filterId]?.expanded ?? false),
      },
    }));
  };

  const parseCriteria = (criteriaJson: string): FilterCriteriaClause[] => {
    try { return JSON.parse(criteriaJson); } catch { return []; }
  };

  const parseFields = (fieldsJson: string): string[] => {
    try { return JSON.parse(fieldsJson); } catch { return []; }
  };

  // ---- Render: loading ----

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-4" />
        <p className="text-gray-600">Loading filter templates...</p>
      </div>
    );
  }

  // ---- Render: create / edit form ----

  if (viewMode === 'create' || viewMode === 'edit') {
    return (
      <div className="max-w-3xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
        {/* Header */}
        <div className="mb-8 flex items-center">
          <button
            onClick={handleBackToList}
            className="mr-4 p-2 rounded-full hover:bg-gray-200 transition-colors"
            aria-label="Back to filter list"
          >
            <ArrowLeft className="h-6 w-6 text-gray-600" />
          </button>
          <h1 className="text-3xl font-bold text-gray-900">
            {viewMode === 'edit' ? 'Edit Filter Template' : 'Create Filter Template'}
          </h1>
        </div>

        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
          <div className="p-6">
            <form onSubmit={handleSave} className="space-y-6">

              {/* Title */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Template Name
                </label>
                <input
                  type="text"
                  required
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="e.g. Open Defects — My Product"
                />
              </div>

              {/* Description */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Description
                </label>
                <textarea
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  rows={2}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500"
                  placeholder="Optional description of what this filter returns..."
                />
              </div>

              {/* Entity Type */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-1">
                  Entity Type
                </label>
                <select
                  value={entityType}
                  onChange={e => setEntityType(e.target.value)}
                  className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white"
                >
                  {ENTITY_TYPES.map(t => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase())}
                    </option>
                  ))}
                </select>
              </div>

              {/* Fields to Fetch */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Fields to Fetch
                </label>
                <div className="flex flex-wrap gap-2">
                  {COMMON_FIELDS.map(field => (
                    <button
                      key={field}
                      type="button"
                      onClick={() => toggleField(field)}
                      className={`px-3 py-1 rounded-full text-xs font-medium border transition-colors ${
                        selectedFields.includes(field)
                          ? 'bg-blue-100 text-blue-800 border-blue-300'
                          : 'bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100'
                      }`}
                    >
                      {field}
                    </button>
                  ))}
                </div>
              </div>

              {/* Criteria Builder */}
              <div>
                <label className="block text-sm font-medium text-gray-700 mb-2">
                  Filter Criteria
                </label>
                <div className="space-y-4">
                  {criteria.map((criterion, ci) => (
                    <div key={ci} className="border border-gray-200 rounded-md p-4 bg-gray-50">
                      <div className="flex items-center justify-between mb-3">
                        <span className="text-xs font-medium text-gray-500 uppercase">
                          {ci === 0 ? 'Where' : 'And'}
                        </span>
                        {criteria.length > 1 && (
                          <button
                            type="button"
                            onClick={() => removeCriterion(ci)}
                            className="text-red-400 hover:text-red-600 transition-colors"
                          >
                            <Trash2 className="h-4 w-4" />
                          </button>
                        )}
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-3">
                        {/* Field Name */}
                        <input
                          type="text"
                          value={criterion.field}
                          onChange={e => updateCriterion(ci, { field: e.target.value })}
                          className="px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 text-sm"
                          placeholder="Field name"
                        />

                        {/* Operator */}
                        <select
                          value={criterion.operator}
                          onChange={e => updateCriterion(ci, { operator: e.target.value })}
                          className="px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 bg-white text-sm"
                        >
                          {OPERATORS.map(op => (
                            <option key={op.value} value={op.value}>{op.label}</option>
                          ))}
                        </select>

                        {/* Negate */}
                        <label className="flex items-center text-sm text-gray-700">
                          <input
                            type="checkbox"
                            checked={criterion.negate}
                            onChange={e => updateCriterion(ci, { negate: e.target.checked })}
                            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded mr-2"
                          />
                          Negate (NOT)
                        </label>
                      </div>

                      {/* Values */}
                      <div className="space-y-2">
                        <span className="text-xs font-medium text-gray-500">Values</span>
                        {criterion.values.map((val, vi) => (
                          <div key={vi} className="flex items-center gap-2">
                            <input
                              type="text"
                              value={val}
                              onChange={e => updateCriterionValue(ci, vi, e.target.value)}
                              className="flex-1 px-3 py-1.5 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-blue-500 focus:border-blue-500 text-sm"
                              placeholder="Value or Octane ID"
                            />
                            {criterion.values.length > 1 && (
                              <button
                                type="button"
                                onClick={() => removeValueFromCriterion(ci, vi)}
                                className="text-red-400 hover:text-red-600 transition-colors"
                              >
                                <Trash2 className="h-3.5 w-3.5" />
                              </button>
                            )}
                          </div>
                        ))}
                        <button
                          type="button"
                          onClick={() => addValueToCriterion(ci)}
                          className="text-xs text-blue-600 hover:text-blue-800 font-medium"
                        >
                          + Add value
                        </button>
                      </div>
                    </div>
                  ))}

                  <button
                    type="button"
                    onClick={addCriterion}
                    className="flex items-center text-sm text-blue-600 hover:text-blue-800 font-medium"
                  >
                    <Plus className="h-4 w-4 mr-1" />
                    Add Criterion
                  </button>
                </div>
              </div>

              {/* Submit */}
              <div className="pt-4 flex gap-3">
                <button
                  type="button"
                  onClick={handleBackToList}
                  className="flex-1 py-2 px-4 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-gray-400 transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={!isFormValid || isSaving}
                  className="flex-1 flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                >
                  {isSaving ? (
                    <Loader2 className="h-5 w-5 animate-spin" />
                  ) : viewMode === 'edit' ? (
                    'Update Template'
                  ) : (
                    'Save Template'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>
      </div>
    );
  }

  // ---- Render: list view ----

  return (
    <div className="max-w-4xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <div className="flex items-center">
          <button
            onClick={onBack}
            className="mr-4 p-2 rounded-full hover:bg-gray-200 transition-colors"
            aria-label="Back to workspace"
          >
            <ArrowLeft className="h-6 w-6 text-gray-600" />
          </button>
          <h1 className="text-3xl font-bold text-gray-900">Filter Templates</h1>
        </div>
        <button
          onClick={handleCreateNew}
          className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
        >
          <Plus className="h-4 w-4" />
          Create New Filter
        </button>
      </div>

      {/* Filter list */}
      {filters.length === 0 ? (
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-12 text-center">
          <p className="text-gray-500 mb-4">No filter templates exist for this workspace.</p>
          <button
            onClick={handleCreateNew}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors"
          >
            <Plus className="h-4 w-4" />
            Create your first filter
          </button>
        </div>
      ) : (
        <div className="space-y-4">
          {filters.map(f => {
            const criteriaList = parseCriteria(f.criteria);
            const fieldsList = parseFields(f.fields);
            const exState = executeState[f.id];

            return (
              <div key={f.id} className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
                {/* Card header */}
                <div className="p-5">
                  <div className="flex items-start justify-between gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <h3 className="font-semibold text-gray-900 text-base">{f.title}</h3>
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                          {f.entityType}
                        </span>
                      </div>
                      {f.description && (
                        <p className="text-sm text-gray-500 mt-1">{f.description}</p>
                      )}
                      <div className="mt-2 text-xs text-gray-500">
                        <span className="font-medium">Fields:</span>{' '}
                        {fieldsList.join(', ')}
                      </div>
                      {criteriaList.length > 0 && (
                        <div className="mt-1 text-xs text-gray-500">
                          <span className="font-medium">Criteria:</span>{' '}
                          {criteriaList.map((c, i) => (
                            <span key={i}>
                              {c.negate ? 'NOT ' : ''}{c.field} {c.operator} [{c.values.join(', ')}]
                              {i < criteriaList.length - 1 ? ' AND ' : ''}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>

                    {/* Action buttons */}
                    <div className="flex items-center gap-2 shrink-0">
                      <button
                        onClick={() => handleEdit(f)}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-gray-300 rounded-md text-xs font-medium text-gray-700 bg-white hover:bg-gray-50 hover:border-blue-300 transition-colors"
                      >
                        <Pencil className="h-3.5 w-3.5" />
                        Edit
                      </button>
                      <button
                        onClick={() => handleExecute(f.id)}
                        disabled={exState?.isExecuting}
                        className="inline-flex items-center gap-1.5 px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-green-600 hover:bg-green-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-green-500 transition-colors"
                      >
                        {exState?.isExecuting ? (
                          <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        ) : (
                          <Play className="h-3.5 w-3.5" />
                        )}
                        Execute
                      </button>
                      {exState?.results !== null && (
                        <button
                          onClick={() => toggleResultsPanel(f.id)}
                          className="inline-flex items-center gap-1 px-2 py-1.5 border border-gray-200 rounded-md text-xs text-gray-500 hover:bg-gray-50 transition-colors"
                          aria-label="Toggle results"
                        >
                          {exState?.expanded ? (
                            <ChevronUp className="h-4 w-4" />
                          ) : (
                            <ChevronDown className="h-4 w-4" />
                          )}
                        </button>
                      )}
                    </div>
                  </div>
                </div>

                {/* Results panel */}
                {exState?.results !== null && exState?.expanded && (
                  <div className="border-t border-gray-200 bg-gray-50">
                    <div className="px-5 py-3 border-b border-gray-200">
                      <span className="text-xs font-medium text-gray-600">
                        Results ({exState.results!.length} item{exState.results!.length !== 1 ? 's' : ''})
                      </span>
                    </div>
                    {exState.results!.length === 0 ? (
                      <div className="p-6 text-center text-sm text-gray-500">
                        No results matched the filter criteria.
                      </div>
                    ) : (
                      <div className="overflow-x-auto">
                        <table className="min-w-full divide-y divide-gray-200 text-xs">
                          <thead className="bg-gray-100">
                            <tr>
                              {Object.keys(exState.results![0]).map(col => (
                                <th
                                  key={col}
                                  className="px-4 py-2 text-left font-medium text-gray-500 uppercase tracking-wider whitespace-nowrap"
                                >
                                  {col}
                                </th>
                              ))}
                            </tr>
                          </thead>
                          <tbody className="bg-white divide-y divide-gray-200">
                            {exState.results!.map((row, ri) => (
                              <tr key={ri} className={ri % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                                {Object.keys(exState.results![0]).map(col => {
                                  const value = row[col];
                                  const display = value === null || value === undefined
                                    ? ''
                                    : typeof value === 'object'
                                      ? JSON.stringify(value)
                                      : String(value);
                                  return (
                                    <td
                                      key={col}
                                      className="px-4 py-2 text-gray-700 whitespace-nowrap max-w-xs truncate"
                                      title={display}
                                    >
                                      {display}
                                    </td>
                                  );
                                })}
                              </tr>
                            ))}
                          </tbody>
                        </table>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default FilterBuilderView;
