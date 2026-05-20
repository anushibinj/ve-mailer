import { useState, useEffect } from 'react';
import {
  adminGetAiPreferences,
  adminUpdateAiPreferences,
} from '../../services/apiService';
import type {
  AiPreferencesResponse,
  AiPreferencesUpdatePayload,
} from '../../services/apiService';
import toast from 'react-hot-toast';

const API_KEY_PLACEHOLDER = '(unchanged)';

export default function AiPreferencesPage() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [configured, setConfigured] = useState(false);
  const [showApiKey, setShowApiKey] = useState(false);

  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('');
  const [chatCompletionsPath, setChatCompletionsPath] = useState('');
  const [model, setModel] = useState('');

  useEffect(() => {
    loadPreferences();
  }, []);

  const loadPreferences = async () => {
    try {
      setLoading(true);
      const data: AiPreferencesResponse = await adminGetAiPreferences();
      setConfigured(data.configured);
      if (data.configured) {
        setApiKey(API_KEY_PLACEHOLDER);
        setBaseUrl(data.baseUrl);
        setChatCompletionsPath(data.chatCompletionsPath);
        setModel(data.model);
      }
    } catch {
      toast.error('Failed to load AI preferences');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!configured && (!apiKey || apiKey === API_KEY_PLACEHOLDER)) {
      toast.error('API key is required for initial configuration');
      return;
    }
    if (!baseUrl.trim()) {
      toast.error('Base URL is required');
      return;
    }
    if (!/^https?:\/\//.test(baseUrl.trim())) {
      toast.error('Base URL must start with http:// or https://');
      return;
    }
    if (!chatCompletionsPath.trim()) {
      toast.error('Chat completions path is required');
      return;
    }
    if (!chatCompletionsPath.trim().startsWith('/')) {
      toast.error('Chat completions path must start with /');
      return;
    }
    if (!model.trim()) {
      toast.error('Model is required');
      return;
    }

    const payload: AiPreferencesUpdatePayload = {
      apiKey,
      baseUrl: baseUrl.trim(),
      chatCompletionsPath: chatCompletionsPath.trim(),
      model: model.trim(),
    };

    try {
      setSaving(true);
      const data = await adminUpdateAiPreferences(payload);
      setConfigured(data.configured);
      setApiKey(API_KEY_PLACEHOLDER);
      setShowApiKey(false);
      toast.success('AI preferences saved successfully');
    } catch {
      toast.error('Failed to save AI preferences');
    } finally {
      setSaving(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center py-12">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
      </div>
    );
  }

  return (
    <div>
      <div className="mb-6">
        <h2 className="text-xl font-semibold text-gray-900">Configure AI Preferences</h2>
        <p className="mt-1 text-sm text-gray-500">
          Configure the AI provider settings used to generate ticket summaries in notification emails.
        </p>
      </div>

      {!configured && (
        <div className="mb-6 p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm text-yellow-800">
            <strong>Not configured.</strong> AI summary generation will not work until these settings are saved.
          </p>
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-5 max-w-lg">
        <div>
          <label htmlFor="ai-api-key" className="block text-sm font-medium text-gray-700">
            API Key
          </label>
          <div className="mt-1 relative">
            <input
              id="ai-api-key"
              type={showApiKey ? 'text' : 'password'}
              value={apiKey}
              onChange={(e) => setApiKey(e.target.value)}
              onFocus={() => {
                if (apiKey === API_KEY_PLACEHOLDER) {
                  setApiKey('');
                }
              }}
              onBlur={() => {
                if (configured && apiKey === '') {
                  setApiKey(API_KEY_PLACEHOLDER);
                }
              }}
              placeholder={configured ? '(unchanged)' : 'Enter your API key'}
              className="block w-full rounded-md border border-gray-300 px-3 py-2 pr-16 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={() => setShowApiKey(!showApiKey)}
              className="absolute inset-y-0 right-0 px-3 flex items-center text-xs text-gray-500 hover:text-gray-700"
            >
              {showApiKey ? 'Hide' : 'Show'}
            </button>
          </div>
          {configured && (
            <p className="mt-1 text-xs text-gray-500">
              Leave as "(unchanged)" to keep the existing API key.
            </p>
          )}
        </div>

        <div>
          <label htmlFor="ai-base-url" className="block text-sm font-medium text-gray-700">
            Base URL
          </label>
          <input
            id="ai-base-url"
            type="text"
            value={baseUrl}
            onChange={(e) => setBaseUrl(e.target.value)}
            placeholder="https://api.openai.com"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
        </div>

        <div>
          <label htmlFor="ai-completions-path" className="block text-sm font-medium text-gray-700">
            Chat Completions Path
          </label>
          <input
            id="ai-completions-path"
            type="text"
            value={chatCompletionsPath}
            onChange={(e) => setChatCompletionsPath(e.target.value)}
            placeholder="/v1/chat/completions"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
          <p className="mt-1 text-xs text-gray-500">
            Path appended to the Base URL for chat requests. Must start with /.
          </p>
        </div>

        <div>
          <label htmlFor="ai-model" className="block text-sm font-medium text-gray-700">
            Model
          </label>
          <input
            id="ai-model"
            type="text"
            value={model}
            onChange={(e) => setModel(e.target.value)}
            placeholder="gpt-4.1-mini"
            className="mt-1 block w-full rounded-md border border-gray-300 px-3 py-2 text-sm shadow-sm focus:border-blue-500 focus:ring-1 focus:ring-blue-500"
            required
          />
        </div>

        <div className="pt-4">
          <button
            type="submit"
            disabled={saving}
            className="inline-flex items-center px-4 py-2 border border-transparent rounded-md shadow-sm text-sm font-medium text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {saving ? 'Saving...' : 'Save Preferences'}
          </button>
        </div>
      </form>
    </div>
  );
}
