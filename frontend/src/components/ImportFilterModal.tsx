import React, { useState } from 'react';
import { X, Loader2, Upload, CheckCircle2 } from 'lucide-react';
import toast from 'react-hot-toast';
import { importFilter, type FilterImportPayload, type FilterCriteriaClause } from '../services/apiService';

interface ImportFilterModalProps {
  workspaceId: string;
  onClose: () => void;
  onImported: () => void;
}

const ENTITY_TYPES = ['defect', 'story', 'feature', 'quality_story', 'epic'];

// ---------------------------------------------------------------------------
// Client-side preview parser
// Mirrors the backend VeFilterImportParser — used only for showing a preview
// before submitting. The backend is authoritative; this is best-effort.
// ---------------------------------------------------------------------------

interface ParsedPreview {
  fields: string[];
  criteria: FilterCriteriaClause[];
  error?: string;
}

function previewParse(rawJson: string): ParsedPreview {
  try {
    const root = JSON.parse(rawJson);
    const params = root?.params;
    if (!params) return { fields: [], criteria: [], error: "Missing top-level 'params' object." };

    // fields
    const fields: string[] = [];
    try {
      const columnsRaw: string = params.columns ?? '[]';
      const cols: string[] = JSON.parse(columnsRaw);
      fields.push(...cols.filter((c: string) => c && c.trim()));
    } catch {
      // ignore column parse errors — just show empty
    }

    // criteria
    const criteria: FilterCriteriaClause[] = [];
    const contentFilter: any[] = params.contentFilter ?? [];
    const filters: any[] = contentFilter[0]?.filters ?? [];
    for (const f of filters) {
      if (f.isIncomplete) continue;
      const field: string = f.lExpression?.value ?? '';
      const operator: string = f.operator ?? '';
      const rExp: any[] = f.rExpression ?? [];
      const values: string[] = rExp
        .map((r: any) => {
          const v = r?.value;
          if (v && typeof v === 'object') return v.id as string;
          if (typeof v === 'string') return v;
          return null;
        })
        .filter((v: string | null): v is string => !!v && v.trim() !== '');
      if (field && operator && values.length > 0) {
        criteria.push({ field, operator, values });
      }
    }

    return { fields, criteria };
  } catch {
    return { fields: [], criteria: [], error: 'Invalid JSON — could not parse.' };
  }
}

// ---------------------------------------------------------------------------

const ImportFilterModal: React.FC<ImportFilterModalProps> = ({ workspaceId, onClose, onImported }) => {
  const [rawJson, setRawJson] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [entityType, setEntityType] = useState('defect');
  const [isSaving, setIsSaving] = useState(false);

  const preview: ParsedPreview = rawJson.trim() ? previewParse(rawJson) : { fields: [], criteria: [] };
  const hasPreviewContent = preview.fields.length > 0 || preview.criteria.length > 0;

  const isValid =
    rawJson.trim() !== '' &&
    title.trim() !== '' &&
    entityType !== '' &&
    !preview.error;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isValid) return;

    setIsSaving(true);
    try {
      const payload: FilterImportPayload = {
        rawJson: rawJson.trim(),
        title: title.trim(),
        description: description.trim(),
        entityType,
      };
      await importFilter(workspaceId, payload);
      toast.success('Filter imported successfully!');
      onImported();
      onClose();
    } catch (err: any) {
      const msg = err.response?.data?.message ?? err.response?.data ?? 'Failed to import filter.';
      toast.error(String(msg));
    } finally {
      setIsSaving(false);
    }
  };

  return (
    /* Backdrop */
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      onClick={(e) => { if (e.target === e.currentTarget) onClose(); }}
    >
      <div className="relative bg-white rounded-xl shadow-2xl w-full max-w-2xl mx-4 max-h-[90vh] flex flex-col">

        {/* Modal header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-200">
          <div className="flex items-center gap-2">
            <Upload className="h-5 w-5 text-blue-600" />
            <h2 className="text-lg font-semibold text-gray-900">Import ValueEdge Filter</h2>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-full hover:bg-gray-100 transition-colors"
            aria-label="Close"
          >
            <X className="h-5 w-5 text-gray-500" />
          </button>
        </div>

        {/* Scrollable body */}
        <div className="overflow-y-auto flex-1">
          <form id="import-form" onSubmit={handleSubmit} className="p-6 space-y-5">

            {/* JSON input */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                ValueEdge Export JSON <span className="text-red-500">*</span>
              </label>
              <textarea
                rows={7}
                value={rawJson}
                onChange={e => setRawJson(e.target.value)}
                className="w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm text-xs font-mono focus:outline-none focus:ring-blue-500 focus:border-blue-500 resize-y"
                placeholder={'Paste the JSON exported from ValueEdge here…\n\n{\n  "params": {\n    "contentFilter": [...],\n    "columns": "[\\"id\\",\\"name\\"]"\n  }\n}'}
                spellCheck={false}
              />
              {rawJson.trim() && preview.error && (
                <p className="mt-1 text-xs text-red-600">{preview.error}</p>
              )}
            </div>

            {/* Live preview */}
            {hasPreviewContent && (
              <div className="bg-blue-50 border border-blue-100 rounded-md p-4 space-y-2">
                <div className="flex items-center gap-1.5 text-blue-700 text-xs font-semibold uppercase tracking-wide mb-1">
                  <CheckCircle2 className="h-3.5 w-3.5" />
                  Parsed Preview
                </div>
                {preview.fields.length > 0 && (
                  <div className="text-xs text-gray-700">
                    <span className="font-medium">Fields:</span>{' '}
                    {preview.fields.join(', ')}
                  </div>
                )}
                {preview.criteria.length > 0 && (
                  <div className="text-xs text-gray-700">
                    <span className="font-medium">Criteria:</span>{' '}
                    {preview.criteria.map((c, i) => (
                      <span key={i}>
                        {c.field} <span className="font-mono">{c.operator}</span> [{c.values.join(', ')}]
                        {i < preview.criteria.length - 1 ? ' AND ' : ''}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* Title */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Template Name <span className="text-red-500">*</span>
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
                placeholder="Optional description…"
              />
            </div>

            {/* Entity type */}
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">
                Entity Type <span className="text-red-500">*</span>
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

          </form>
        </div>

        {/* Footer */}
        <div className="px-6 py-4 border-t border-gray-200 flex gap-3 justify-end bg-gray-50 rounded-b-xl">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded-md text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 focus:outline-none transition-colors"
          >
            Cancel
          </button>
          <button
            type="submit"
            form="import-form"
            disabled={!isValid || isSaving}
            className="inline-flex items-center gap-2 px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 disabled:bg-gray-400 disabled:cursor-not-allowed focus:outline-none transition-colors"
          >
            {isSaving ? (
              <Loader2 className="h-4 w-4 animate-spin" />
            ) : (
              <Upload className="h-4 w-4" />
            )}
            Import Filter
          </button>
        </div>
      </div>
    </div>
  );
};

export default ImportFilterModal;
