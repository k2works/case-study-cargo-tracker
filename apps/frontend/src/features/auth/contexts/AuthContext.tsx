import { createContext, useContext, useState, ReactNode } from 'react';
import { login as apiLogin, logout as apiLogout } from '../api/authApi';
import { TOKEN_STORAGE_KEY } from '../../../shared/api/auth';

interface AuthState {
  token: string | null;
  role: string | null;
  username: string | null;
}

interface AuthContextValue extends AuthState {
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  isAuthenticated: boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

const TOKEN_KEY = TOKEN_STORAGE_KEY;
const ROLE_KEY = 'auth_role';
const USERNAME_KEY = 'auth_username';

function loadState(): AuthState {
  return {
    token: localStorage.getItem(TOKEN_KEY),
    role: localStorage.getItem(ROLE_KEY),
    username: localStorage.getItem(USERNAME_KEY),
  };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(loadState);

  async function login(username: string, password: string) {
    const res = await apiLogin({ username, password });
    localStorage.setItem(TOKEN_KEY, res.token);
    localStorage.setItem(ROLE_KEY, res.role);
    localStorage.setItem(USERNAME_KEY, username);
    setState({ token: res.token, role: res.role, username });
  }

  async function logout() {
    await apiLogout();
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(USERNAME_KEY);
    setState({ token: null, role: null, username: null });
  }

  return (
    <AuthContext.Provider
      value={{ ...state, login, logout, isAuthenticated: !!state.token }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
