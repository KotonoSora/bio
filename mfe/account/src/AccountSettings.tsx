import { useCallback, useEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { REGEXP_ONLY_DIGITS } from 'input-otp';
import { LogOutIcon } from 'lucide-react';
import { ApiError, csrfToken, isRelayEmail, toApiError } from '@resume-builder/mfe-kit';
import {
  Badge,
  Button,
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  InputOTP,
  InputOTPGroup,
  InputOTPSlot,
  Label,
  Notice,
} from '@resume-builder/ui';
import {
  accountApi,
  authApi,
  type MeResponse,
  type MyProviderView,
  type ProviderView,
  type SessionSummary,
} from './api/account';
import ConfirmDialog from './components/ConfirmDialog';
import StepUpDialog from './components/StepUpDialog';
import { describeError, isUnauthenticated } from './lib/errors';
import { lastActiveBucket } from './lib/format';
import { currentReturnTo, postForm } from './lib/forms';
import { useMfeContext } from './mfe-context';
import {
  clearPendingAction,
  conflictBannerDismissed,
  dismissConflictBanner,
  setPendingAction,
  takePendingAction,
  type PendingAction,
} from './lib/stepup';

type DialogState =
  | { kind: 'confirm-unlink'; provider: MyProviderView }
  | { kind: 'confirm-revoke'; session: SessionSummary }
  | { kind: 'confirm-revoke-others' }
  | { kind: 'step-up'; action: PendingAction }
  | null;

const MAX_CODE_ATTEMPTS = 5;

export default function AccountSettings() {
  const { navigate } = useMfeContext();
  const { t } = useTranslation();
  const [me, setMe] = useState<MeResponse | null>(null);
  const [providers, setProviders] = useState<MyProviderView[]>([]);
  const [sessions, setSessions] = useState<SessionSummary[]>([]);
  const [loginProviders, setLoginProviders] = useState<ProviderView[]>([]);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [conflictDismissed, setConflictDismissed] = useState(conflictBannerDismissed());

  const [email, setEmail] = useState('');
  const [emailSyntaxOk, setEmailSyntaxOk] = useState(true);
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [attempts, setAttempts] = useState(0);
  const [resendIn, setResendIn] = useState(0);
  const [contactError, setContactError] = useState<string | null>(null);
  const [codeMessage, setCodeMessage] = useState<string | null>(null);

  // BR-04: session expiry returns to this mount path via the `next` allowlist.
  const toLogin = useCallback(
    () => navigate(`/login?notice=expired&next=${encodeURIComponent(currentReturnTo())}`),
    [navigate],
  );

  const refuse = useCallback(
    (err: unknown) => {
      if (isUnauthenticated(err)) {
        toLogin();
        return;
      }
      setNotice(describeError(err));
    },
    [toLogin],
  );

  const refreshProviders = useCallback(async () => {
    const r = await accountApi.providers();
    setProviders(r.providers);
  }, []);

  const refreshSessions = useCallback(async () => {
    const r = await accountApi.sessions();
    setSessions(r.sessions);
  }, []);

  const runUnlink = useCallback(
    async (provider: string, label: string) => {
      try {
        await authApi.unlink(provider);
        await refreshProviders();
      } catch (err) {
        if (err instanceof ApiError && err.code === 'step_up_required') {
          const action: PendingAction = { kind: 'unlink', provider, label };
          setPendingAction(action);
          setDialog({ kind: 'step-up', action });
          return;
        }
        if (err instanceof ApiError && err.code === 'not_linked') {
          await refreshProviders();
          return;
        }
        refuse(err);
      }
    },
    [refreshProviders, refuse],
  );

  /**
   * Link start needs fresh step-up (BR-05/BR-07). The dry-run POST with
   * `redirect: 'manual'` tells `opaqueredirect` (fresh; re-POST as a real form
   * navigation so the IdP redirect governs) apart from `403 step_up_required`
   * (park the action, show the dialog; the orphan state key TTLs out).
   */
  const startLink = useCallback(
    async (provider: string, label: string) => {
      try {
        const res = await fetch(`/api/auth/link/${encodeURIComponent(provider)}`, {
          method: 'POST',
          redirect: 'manual',
          credentials: 'same-origin',
          headers: { 'X-CSRF-Token': csrfToken() },
        });
        if (res.type === 'opaqueredirect' || res.status === 0) {
          postForm(`/api/auth/link/${encodeURIComponent(provider)}`, {
            returnTo: currentReturnTo(),
            csrf_token: csrfToken(),
          });
          return;
        }
        if (res.status === 403) {
          const problem = await toApiError(res);
          if (problem.code === 'step_up_required') {
            const action: PendingAction = { kind: 'link', provider, label };
            setPendingAction(action);
            setDialog({ kind: 'step-up', action });
            return;
          }
          setNotice(describeError(problem));
          return;
        }
        if (res.status === 409) {
          await refreshProviders();
          return;
        }
        if (res.status === 401) {
          toLogin();
          return;
        }
        setNotice(describeError(await toApiError(res)));
      } catch {
        setNotice(t('errors.generic'));
      }
    },
    [refreshProviders, toLogin],
  );

  const bootstrap = useCallback(async (): Promise<boolean> => {
    try {
      const [m, ps, ss] = await Promise.all([accountApi.me(), accountApi.providers(), accountApi.sessions()]);
      setMe(m);
      setProviders(ps.providers);
      setSessions(ss.sessions);
      return true;
    } catch (err) {
      if (isUnauthenticated(err)) {
        toLogin();
        return false;
      }
      setNotice(describeError(err));
      return false;
    }
  }, [toLogin]);

  const booted = useRef(false);
  useEffect(() => {
    if (booted.current) return; // StrictMode double-invoke guard
    booted.current = true;
    authApi
      .providers()
      .then((r) => setLoginProviders(r.providers))
      .catch(() => setLoginProviders([]));
    void (async () => {
      const ready = await bootstrap();
      if (!ready) return;
      const pending = takePendingAction();
      if (!pending) return;
      if (pending.kind === 'unlink') await runUnlink(pending.provider, pending.label);
      else await startLink(pending.provider, pending.label);
    })();
  }, [bootstrap, runUnlink, startLink]);

  const countdownActive = resendIn > 0;
  useEffect(() => {
    if (!countdownActive) return;
    const timer = window.setInterval(() => setResendIn((s) => (s <= 1 ? 0 : s - 1)), 1000);
    return () => window.clearInterval(timer);
  }, [countdownActive]);

  async function sendCode() {
    setContactError(null);
    setCodeMessage(null);
    try {
      await accountApi.sendContactEmail(email.trim());
      setCodeSent(true);
      setAttempts(0);
      setResendIn(60);
      setCodeMessage(t('settings.codeSentTo', { email: email.trim() }));
    } catch (err) {
      if (err instanceof ApiError && err.code === 'throttled') {
        setResendIn(err.retryAfterSeconds ?? 60);
        setContactError(describeError(err));
        return;
      }
      if (isUnauthenticated(err)) {
        toLogin();
        return;
      }
      setContactError(describeError(err));
    }
  }

  async function verifyCode() {
    setContactError(null);
    try {
      const updated = await accountApi.verifyContactEmail(code.trim());
      setMe(updated);
      setCodeSent(false);
      setCode('');
      setCodeMessage(null);
      setAttempts(0);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'email_code_invalid') {
        setAttempts((n) => n + 1);
        setCode('');
        setContactError(describeError(err));
        return;
      }
      if (isUnauthenticated(err)) {
        toLogin();
        return;
      }
      setContactError(describeError(err));
    }
  }

  async function revokeSession(sid: string) {
    try {
      await accountApi.revokeSession(sid);
      await refreshSessions();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'session_not_found') {
        setNotice(describeError(err));
        await refreshSessions();
        return;
      }
      refuse(err);
    }
  }

  async function revokeOthers() {
    try {
      await accountApi.revokeOtherSessions();
      await refreshSessions();
    } catch (err) {
      refuse(err);
    }
  }

  async function signOut() {
    try {
      await accountApi.signOut();
    } catch {
      // Cookie clearing is best effort; the login redirect still wins.
    }
    navigate('/login');
  }

  if (!me) {
    return (
      <main className="mx-auto w-full max-w-2xl">
        <h1 className="font-heading text-2xl font-semibold">{t('settings.title')}</h1>
        {notice ? <Notice>{notice}</Notice> : <p className="mt-4 text-sm text-muted-foreground">{t('common.loading')}</p>}
      </main>
    );
  }

  // Deliverable-email signal (BR-03): IdP-verified email or a verified contact
  // email satisfies it; conflict/relay variants pair with the banner + prompt.
  const relayEmail = me.email ? isRelayEmail(me.email) : false;
  const deliverable = me.emailVerified || me.contactVerified;
  const chip = me.emailConflict
    ? t('settings.status.conflict')
    : !deliverable && relayEmail
      ? t('settings.status.relay')
      : deliverable
        ? t('settings.status.verified')
        : t('settings.status.notVerified');
  const promptAdd = !deliverable && (relayEmail || !me.email);
  const linked = providers.filter((p) => p.linked);
  const dialogActionLabel = (action: PendingAction) =>
    t(action.kind === 'link' ? 'actions.link' : 'actions.unlink', { provider: action.label });

  return (
    <main className="mx-auto w-full max-w-2xl space-y-6">
      <div className="flex items-center justify-between gap-3">
        <h1 className="font-heading text-2xl font-semibold">{t('settings.title')}</h1>
        <Button type="button" variant="outline" onClick={() => void signOut()}>
          <LogOutIcon />
          {t('settings.signOut')}
        </Button>
      </div>

      {notice && <Notice onDismiss={() => setNotice(null)} dismissLabel={t('common.dismiss')}>{notice}</Notice>}
      {me.emailConflict && !conflictDismissed && (
        <Notice
          tone="warn"
          dismissLabel={t('common.dismiss')}
          onDismiss={() => {
            dismissConflictBanner();
            setConflictDismissed(true);
          }}
        >
          {t('settings.conflictNotice')}
        </Notice>
      )}

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.accountEmail')}</CardTitle>
          <CardDescription>{t('settings.idpSource')}</CardDescription>
          <CardAction>
            <Badge variant={me.emailConflict ? 'destructive' : deliverable ? 'default' : 'outline'}>{chip}</Badge>
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm font-medium">{me.email ?? t('common.dash')}</p>
          {promptAdd && <p className="text-sm text-amber-700">{t('settings.promptAdd')}</p>}

          <form
            className="space-y-2"
            onSubmit={(e) => {
              e.preventDefault();
              if (email.trim()) void sendCode();
            }}
          >
            <Label htmlFor="contact-email">{t('settings.contactEmail')}</Label>
            <Input
              id="contact-email"
              type="email"
              required
              value={email}
              placeholder={t('settings.contactPlaceholder')}
              onChange={(e) => {
                setEmail(e.target.value);
                setEmailSyntaxOk(e.target.checkValidity());
              }}
            />
            <div className="flex items-center gap-2">
              <Button type="submit" disabled={!emailSyntaxOk || !email.trim() || resendIn > 0}>
                {t('settings.sendCode')}
              </Button>
              {resendIn > 0 && (
                <span className="text-xs text-muted-foreground">{t('settings.resendIn', { seconds: resendIn })}</span>
              )}
            </div>
          </form>

          {contactError && <Notice tone="error">{contactError}</Notice>}
          {codeSent && (
            <form
              className="space-y-3"
              onSubmit={(e) => {
                e.preventDefault();
                if (code.length === 6) void verifyCode();
              }}
            >
              <p className="text-sm text-muted-foreground">{codeMessage}</p>
              <div className="space-y-2">
                <Label htmlFor="contact-code">{t('settings.codeLabel')}</Label>
                <InputOTP
                  id="contact-code"
                  maxLength={6}
                  pattern={REGEXP_ONLY_DIGITS}
                  inputMode="numeric"
                  required
                  value={code}
                  onChange={(value) => setCode(value.replace(/\D/g, '').slice(0, 6))}
                  containerClassName="w-fit"
                >
                  <InputOTPGroup>
                    {Array.from({ length: 6 }, (_, i) => (
                      <InputOTPSlot key={i} index={i} />
                    ))}
                  </InputOTPGroup>
                </InputOTP>
              </div>
              <div className="flex items-center gap-2">
                <Button type="submit" disabled={code.length !== 6}>
                  {t('settings.verifyCode')}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={resendIn > 0}
                  onClick={() => void sendCode()}
                >
                  {resendIn > 0 ? t('settings.resendIn', { seconds: resendIn }) : t('settings.resend')}
                </Button>
              </div>
              {attempts >= 3 && (
                <p className="text-xs text-muted-foreground">
                  {t('settings.attemptsLeft', { count: Math.max(0, MAX_CODE_ATTEMPTS - attempts) })}
                </p>
              )}
            </form>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.providers')}</CardTitle>
        </CardHeader>
        <CardContent className="px-0">
          <ul className="divide-y">
            {providers.map((p) => {
              const lastLinked = p.linked && linked.length === 1;
              return (
                <li key={p.id} className="flex items-center justify-between gap-3 px-(--card-spacing) py-3 text-sm">
                  <span className="flex items-center gap-2">
                    <span className="font-medium">{p.label}</span>
                    <Badge variant={p.linked ? 'secondary' : 'outline'}>
                      {p.linked ? t('settings.linked') : t('settings.notLinked')}
                    </Badge>
                  </span>
                  {p.linked ? (
                    <span className="flex items-center gap-3">
                      {lastLinked && <span className="max-w-xs text-right text-xs text-muted-foreground">{t('settings.lastLinkHint')}</span>}
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        disabled={lastLinked}
                        onClick={() => setDialog({ kind: 'confirm-unlink', provider: p })}
                      >
                        {t('settings.unlink')}
                      </Button>
                    </span>
                  ) : (
                    <Button type="button" size="sm" onClick={() => void startLink(p.id, p.label)}>
                      {t('settings.link')}
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>{t('settings.sessions')}</CardTitle>
          <CardAction>
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={sessions.filter((s) => !s.current).length === 0}
              onClick={() => setDialog({ kind: 'confirm-revoke-others' })}
            >
              {t('settings.revokeAll')}
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent className="px-0">
          <ul className="divide-y">
            {sessions.map((s) => (
              <li key={s.sid} className="flex items-center justify-between gap-3 px-(--card-spacing) py-3 text-sm">
                <span>
                  <span className="font-medium">{s.deviceLabel || t('settings.unknownDevice')}</span>{' '}
                  {s.current && <Badge variant="outline">{t('settings.current')}</Badge>}
                  <span className="block text-xs text-muted-foreground">
                    {t(`buckets.${lastActiveBucket(s.lastActiveAt)}`)}
                    {s.ipClass ? ` · ${t('settings.network')} ${s.ipClass}` : ''}
                  </span>
                </span>
                {!s.current && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setDialog({ kind: 'confirm-revoke', session: s })}
                  >
                    {t('settings.revoke')}
                  </Button>
                )}
              </li>
            ))}
          </ul>
        </CardContent>
      </Card>

      {dialog?.kind === 'confirm-unlink' && (
        <ConfirmDialog
          title={t('settings.unlinkTitle', { provider: dialog.provider.label })}
          body={t('settings.unlinkBody')}
          confirmLabel={t('settings.unlink')}
          danger
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            const { provider } = dialog;
            setDialog(null);
            void runUnlink(provider.id, provider.label);
          }}
        />
      )}
      {dialog?.kind === 'confirm-revoke' && (
        <ConfirmDialog
          title={t('settings.revokeTitle')}
          body={t('settings.revokeBody')}
          confirmLabel={t('settings.revoke')}
          danger
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            const { session } = dialog;
            setDialog(null);
            void revokeSession(session.sid);
          }}
        />
      )}
      {dialog?.kind === 'confirm-revoke-others' && (
        <ConfirmDialog
          title={t('settings.revokeAllTitle')}
          body={t('settings.revokeAllBody')}
          confirmLabel={t('settings.revokeAll')}
          onCancel={() => setDialog(null)}
          onConfirm={() => {
            setDialog(null);
            void revokeOthers();
          }}
        />
      )}
      {dialog?.kind === 'step-up' && (
        <StepUpDialog
          providers={loginProviders}
          actionLabel={dialogActionLabel(dialog.action)}
          returnTo={currentReturnTo()}
          onCancel={() => {
            clearPendingAction();
            setDialog(null);
          }}
        />
      )}
    </main>
  );
}
