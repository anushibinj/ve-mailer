import React, { useEffect, useState } from 'react';
import {
  fetchFilters,
  createFilter,
  type Filter,
  type FilterCriteriaClause,
  type FilterCreatePayload
} from '../services/apiService';
import { Loader2, ArrowLeft, Plus, Trash2 } from 'lucide-react';
import toast from 'react-hot-toast';

interface FilterBuilderViewProps {
  onBack: () => void;
}

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

const FilterBuilderView: React.FC<FilterBuilderViewProps> = ({ onBack }) => {
  const [existingFilters, setExistingFilters] = useState<Filter[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  // Form state
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [entityType, setEntityType] = useState('defect');
  const [selectedFields, setSelectedFields] = useState<string[]>(['id', 'name', 'phase', 'owner']);
  const [criteria, setCriteria] = useState<FilterCriteriaClause[]>([emptyCriterion()]);
  const [isSaving, setIsSaving] = useState(false);

  useEffect(() => {
    loadFilters();
  }, []);

  const loadFilters = async () => {
    setIsLoading(true);
    try {
      const data = await fetchFilters();
      setExistingFilters(data);
    } catch {
      toast.error('Failed to load existing filters.');
    } finally {
      setIsLoading(false);
    }
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
      const payload: FilterCreatePayload = {
        title,
        description,
        entityType,
        fields: selectedFields,
        criteria: criteria.map(c => ({
          ...c,
          values: c.values.map(v => v.trim()),
        })),
      };
      await createFilter(payload);
      toast.success('Filter template saved!');
      // Reset form
      setTitle('');
      setDescription('');
      setEntityType('defect');
      setSelectedFields(['id', 'name', 'phase', 'owner']);
      setCriteria([emptyCriterion()]);
      loadFilters();
    } catch (err: any) {
      toast.error(err.response?.data?.message || 'Failed to save filter template.');
    } finally {
      setIsSaving(false);
    }
  };

  const parseCriteria = (criteriaJson: string): FilterCriteriaClause[] => {
    try {
      return JSON.parse(criteriaJson);
    } catch {
      return [];
    }
  };

  const parseFields = (fieldsJson: string): string[] => {
    try {
      return JSON.parse(fieldsJson);
    } catch {
      return [];
    }
  };

  if (isLoading) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen">
        <Loader2 className="h-8 w-8 animate-spin text-blue-500 mb-4" />
        <p className="text-gray-600">Loading filter templates...</p>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto py-8 px-4 sm:px-6 lg:px-8">
      {/* Header */}
      <div className="mb-8 flex items-center">
        <button
          onClick={onBack}
          className="mr-4 p-2 rounded-full hover:bg-gray-200 transition-colors"
          aria-label="Back"
        >
          <ArrowLeft className="h-6 w-6 text-gray-600" />
        </button>
        <h1 className="text-3xl font-bold text-gray-900">Filter Templates</h1>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">

        {/* Left Column: Existing Templates */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden flex flex-col h-fit">
          <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
            <h2 className="text-lg font-medium text-gray-900">Saved Templates</h2>
          </div>
          <div className="overflow-x-auto">
            {existingFilters.length === 0 ? (
              <div className="p-8 text-center text-gray-500">
                No filter templates saved yet.
              </div>
            ) : (
              <div className="divide-y divide-gray-200">
                {existingFilters.map(f => {
                  const criteriaList = parseCriteria(f.criteria);
                  const fieldsList = parseFields(f.fields);
                  return (
                    <div key={f.id} className="p-4">
                      <div className="flex items-start justify-between">
                        <div>
                          <h3 className="font-medium text-gray-900">{f.title}</h3>
                          {f.description && (
                            <p className="text-sm text-gray-500 mt-1">{f.description}</p>
                          )}
                        </div>
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                          {f.entityType}
                        </span>
                      </div>
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
                  );
                })}
              </div>
            )}
          </div>
        </div>

        {/* Right Column: Create New Template */}
        <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden h-fit">
          <div className="px-6 py-5 border-b border-gray-200 bg-gray-50">
            <h2 className="text-lg font-medium text-gray-900">Create New Template</h2>
          </div>
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
              <div className="pt-4">
                <button
                  type="submit"
                  disabled={!isFormValid || isSaving}
                  className="w-full flex justify-center py-2 px-4 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:bg-gray-400 disabled:cursor-not-allowed transition-colors"
                >
                  {isSaving ? (
                    <Loader2 className="h-5 w-5 animate-spin" />
                  ) : (
                    'Save Template'
                  )}
                </button>
              </div>
            </form>
          </div>
        </div>

      </div>
    </div>
  );
};

export default FilterBuilderView;
