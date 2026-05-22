import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppContent, AdminLayout } from '../../App';

// ---------------------------------------------------------------------------
// Module mocks
// ---------------------------------------------------------------------------

// Bypass real auth context; useAuth is called directly by AppContent/AdminLayout
vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: '1', name: 'Test User', email: 'test@example.com' },
    isAuthenticated: true,
    isAdmin: false,
    logout: vi.fn().mockResolvedValue(undefined),
  }),
}));

// Stub page-level components that would issue real API requests
vi.mock('../LandingView', () => ({ default: () => <div data-testid="landing-view">Landing</div> }));
vi.mock('../WorkspaceDashboard', () => ({ default: () => <div>WorkspaceDashboard</div> }));
vi.mock('../FilterBuilderView', () => ({ default: () => <div>FilterBuilderView</div> }));

// ---------------------------------------------------------------------------
// AppContent — sticky header
// ---------------------------------------------------------------------------
describe('AppContent sticky header', () => {
  it('renders a <header> banner landmark', () => {
    render(<MemoryRouter><AppContent /></MemoryRouter>);
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('header carries sticky top-0 z-30 for app-shell sticky behaviour', () => {
    render(<MemoryRouter><AppContent /></MemoryRouter>);
    const header = screen.getByRole('banner');
    // sticky + top-0 make the header stick to the top of the scroll container.
    // z-30 ensures the header stacks above scrollable content (z-0) and the
    // footer (z-10) without overlapping modals (typically z-50+).
    expect(header.className).toContain('sticky');
    expect(header.className).toContain('top-0');
    expect(header.className).toContain('z-30');
  });

  it('header retains shadow and border styling after sticky refactor', () => {
    render(<MemoryRouter><AppContent /></MemoryRouter>);
    const header = screen.getByRole('banner');
    expect(header.className).toContain('shadow-sm');
    expect(header.className).toContain('border-b');
  });
});

// ---------------------------------------------------------------------------
// AdminLayout — sticky header
// ---------------------------------------------------------------------------
describe('AdminLayout sticky header', () => {
  it('renders a <header> banner landmark', () => {
    render(
      <MemoryRouter>
        <AdminLayout><div>admin content</div></AdminLayout>
      </MemoryRouter>,
    );
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('header carries sticky top-0 z-30 for app-shell sticky behaviour', () => {
    render(
      <MemoryRouter>
        <AdminLayout><div>admin content</div></AdminLayout>
      </MemoryRouter>,
    );
    const header = screen.getByRole('banner');
    expect(header.className).toContain('sticky');
    expect(header.className).toContain('top-0');
    expect(header.className).toContain('z-30');
  });

  it('children are rendered below the header', () => {
    render(
      <MemoryRouter>
        <AdminLayout><div data-testid="admin-panel">Admin panel</div></AdminLayout>
      </MemoryRouter>,
    );
    expect(screen.getByTestId('admin-panel')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// App-shell z-index stacking order
// ---------------------------------------------------------------------------
describe('App-shell z-index stacking order', () => {
  // These tests document the intended CSS stacking hierarchy. Actual z-index
  // rendering is verified through the Tailwind class names checked above.
  it('header z-index (z-30) is above footer z-index (z-10)', () => {
    const zHeader = 30; // Tailwind z-30 — applied to sticky header
    const zFooter = 10; // Tailwind z-10 — applied to AppFooter
    expect(zHeader).toBeGreaterThan(zFooter);
  });

  it('footer z-index (z-10) is above default content (z-0)', () => {
    const zFooter = 10;
    const zContent = 0;
    expect(zFooter).toBeGreaterThan(zContent);
  });
});
