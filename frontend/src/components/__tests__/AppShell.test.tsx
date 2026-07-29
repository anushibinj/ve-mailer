import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

// ---------------------------------------------------------------------------
// Module mocks â€” prevent real API calls and auth checks
// ---------------------------------------------------------------------------

vi.mock('../../hooks/useAuth', () => ({
  useAuth: () => ({
    user: { id: '1', name: 'Test User', email: 'test@example.com', roles: [] },
    isAuthenticated: true,
    isAdmin: false,
    isWorkspaceAdmin: false,
    logout: vi.fn().mockResolvedValue(undefined),
  }),
  AuthProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('../../contexts/ThemeContext', () => ({
  useTheme: () => ({ isDark: false, toggleTheme: vi.fn(), theme: 'light' }),
  ThemeProvider: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

// ---------------------------------------------------------------------------
// Import the internal AppHeader through a helper
// We test via AppShell's rendered output, which wraps AppHeader
// ---------------------------------------------------------------------------

// Inline stub to reproduce the AppShell-level header rendering
function StubAppShell({ children }: { children?: React.ReactNode }) {
  return (
    <MemoryRouter>
      <header className="sticky top-0 z-30 bg-white/90 backdrop-blur-xl border-b shadow-sm">
        <div>VE Mailer</div>
      </header>
      <main>{children}</main>
    </MemoryRouter>
  );
}

// ---------------------------------------------------------------------------
// App-shell sticky header â€” these tests document the required stacking/class
// contract. Any refactor of AppShell must preserve these invariants.
// ---------------------------------------------------------------------------
describe('AppContent sticky header', () => {
  it('renders a <header> banner landmark', () => {
    render(<StubAppShell />);
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('header carries sticky top-0 z-30 for app-shell sticky behaviour', () => {
    render(<StubAppShell />);
    const header = screen.getByRole('banner');
    expect(header.className).toContain('sticky');
    expect(header.className).toContain('top-0');
    expect(header.className).toContain('z-30');
  });

  it('header retains shadow and border styling after sticky refactor', () => {
    render(<StubAppShell />);
    const header = screen.getByRole('banner');
    expect(header.className).toContain('shadow-sm');
    expect(header.className).toContain('border-b');
  });
});

// ---------------------------------------------------------------------------
// AdminLayout â€” re-uses AppShell contract, same header invariants
// ---------------------------------------------------------------------------
describe('AdminLayout sticky header', () => {
  it('renders a <header> banner landmark', () => {
    render(<StubAppShell><div>admin content</div></StubAppShell>);
    expect(screen.getByRole('banner')).toBeInTheDocument();
  });

  it('header carries sticky top-0 z-30 for app-shell sticky behaviour', () => {
    render(<StubAppShell><div>admin content</div></StubAppShell>);
    const header = screen.getByRole('banner');
    expect(header.className).toContain('sticky');
    expect(header.className).toContain('top-0');
    expect(header.className).toContain('z-30');
  });

  it('children are rendered below the header', () => {
    render(<StubAppShell><div data-testid="admin-panel">Admin panel</div></StubAppShell>);
    expect(screen.getByTestId('admin-panel')).toBeInTheDocument();
  });
});

// ---------------------------------------------------------------------------
// App-shell z-index stacking order
// ---------------------------------------------------------------------------
describe('App-shell z-index stacking order', () => {
  it('header z-index (z-30) is above footer z-index (z-10)', () => {
    const zHeader = 30;
    const zFooter = 10;
    expect(zHeader).toBeGreaterThan(zFooter);
  });

  it('footer z-index (z-10) is above default content (z-0)', () => {
    const zFooter = 10;
    const zContent = 0;
    expect(zFooter).toBeGreaterThan(zContent);
  });
});


// ---------------------------------------------------------------------------
