import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, NetworkError } from '@/api/client'
import { AccountApi } from '@/api/endpoints'
import { isValidEmail, register, signIn } from '@/account/service'
import { Field, PrimaryButton, TextButton } from '@/components/ui'

type Mode = 'LOGIN' | 'SIGN_UP' | 'FORGOT'

const COPY: Record<Mode, { title: string; sub: string; primary: string }> = {
  LOGIN: { title: 'Sign in', sub: 'Sync your bookmarks and categories across every device.', primary: 'Sign in' },
  SIGN_UP: { title: 'Create account', sub: "One account keeps every device's bookmarks in sync.", primary: 'Create account' },
  FORGOT: { title: 'Reset password', sub: "Enter your account email and we'll send a link to reset your password.", primary: 'Send reset link' },
}

export function AccountScreen() {
  const navigate = useNavigate()
  const [mode, setMode] = useState<Mode>('LOGIN')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  function go(next: Mode) {
    setMode(next)
    setError(null)
    setMessage(null)
    setPassword('')
    setConfirm('')
  }

  async function submit() {
    setError(null)
    setMessage(null)
    if (!isValidEmail(email)) return setError('Enter a valid email address.')
    if (mode !== 'FORGOT' && password.length < 8) return setError('Password must be at least 8 characters.')
    if (mode === 'SIGN_UP' && password !== confirm) return setError("Passwords don't match.")
    setLoading(true)
    try {
      if (mode === 'LOGIN') await signIn(email, password)
      else if (mode === 'SIGN_UP') await register(email, password)
      else {
        const res = await AccountApi.forgotPassword(email.trim().toLowerCase())
        setMessage(res.message)
        return
      }
      navigate(-1)
    } catch (e) {
      setError(e instanceof ApiError || e instanceof NetworkError ? e.message : "Couldn't reach the server. Check your connection and try again.")
    } finally {
      setLoading(false)
    }
  }

  const c = COPY[mode]
  return (
    <form className="account" onSubmit={(e) => { e.preventDefault(); void submit() }} noValidate>
      <div>
        <h1 className="t-screen-title" style={{ margin: 0, textTransform: 'none' }}>{c.title}</h1>
        <p className="t-row-sub muted" style={{ margin: '8px 0 0' }}>{c.sub}</p>
      </div>
      <Field label="Email" value={email} onChange={setEmail} type="email" inputMode="email" autoComplete="email" name="email" autoFocus />
      {mode !== 'FORGOT' ? <Field label="Password" value={password} onChange={setPassword} type="password" autoComplete={mode === 'LOGIN' ? 'current-password' : 'new-password'} name="password" /> : null}
      {mode === 'SIGN_UP' ? <Field label="Confirm password" value={confirm} onChange={setConfirm} type="password" autoComplete="new-password" name="confirm" /> : null}
      {error ? <div className="t-row-sub" style={{ color: 'var(--error)' }} role="alert">{error}</div> : null}
      {message ? <div className="t-row-sub" style={{ color: 'var(--accent)' }} role="status">{message}</div> : null}
      {loading ? (
        <div className="spinner" role="progressbar" aria-label="Working" />
      ) : (
        <PrimaryButton block type="submit" onClick={() => undefined}>{c.primary}</PrimaryButton>
      )}
      <div className="account-links">
        {mode === 'LOGIN' ? (
          <>
            <TextButton onClick={() => go('FORGOT')}>Forgot password?</TextButton>
            <TextButton onClick={() => go('SIGN_UP')}>Create an account</TextButton>
          </>
        ) : null}
        {mode === 'SIGN_UP' ? (
          <>
            <TextButton onClick={() => go('FORGOT')}>Forgot password?</TextButton>
            <TextButton onClick={() => go('LOGIN')}>Already have an account? Sign in</TextButton>
          </>
        ) : null}
        {mode === 'FORGOT' ? <TextButton onClick={() => go('LOGIN')}>Back to sign in</TextButton> : null}
        <TextButton onClick={() => navigate(-1)}>Not now</TextButton>
      </div>
    </form>
  )
}
