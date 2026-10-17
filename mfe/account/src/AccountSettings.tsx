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
  deletionApi,
  profileApi,
  type DeleteStatusResponse,
  type MeResponse,
  type MyProviderView,
  type ProviderView,
  type SessionSummary,
} from './api/account';
import ConfirmDialog from './components/ConfirmDialog';
import DeleteAccountDialog, { LITERAL_DELETE } from './components/DeleteAccountDialog';
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
  | { kind: 'confirm-restore' }
  | { kind: 'delete' }
  | { kind: 'step-up'; action: PendingAction }
  | null;

const MAX_CODE_ATTEMPTS = 5;
const DAY_MS = 86_400_000;

function purgeDate(iso: string): string {
  return new Intl.DateTimeFormat('en', { dateStyle: 'long' }).format(new Date(iso));
}

function daysLeft(iso: string): number {
  return Math.max(0, Math.ceil((Date.parse(iso) - Date.now()) / DAY_MS));
}

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

  // Feature 02 deletion lifecycle: `deletion` mirrors GET delete-status;
  // `signedOut` is set only when this tab committed a request (the session is
  // already dead server-side, so the panel renders from the 200 response).
  const [deletion, setDeletion] = useState<DeleteStatusResponse | null>(null);
  const [signedOut, setSignedOut] = useState<DeleteStatusResponse | null>(null);
  const [expected, setExpected] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);

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

  /**
   * BR-01 typed gate: resolve the expected confirmation on open - the profile
   * username exactly, else the literal `DELETE`. Lookup failure degrades to
   * `DELETE` (the typed value is a UX guard; step-up + owner session are the
   * security boundary). The list's first profile matches the mesh handoff read.
   */
  const openDeleteDialog = useCallback(() => {
    setDeleteError(null);
    setExpected(null);
    setDialog({ kind: 'delete' });
    profileApi
      .mine()
      .then((profiles) => setExpected(profiles[0]?.username ?? LITERAL_DELETE))
      .catch(() => setExpected(LITERAL_DELETE));
  }, []);

  const runDelete = useCallback(
    async (confirmation: string) => {
      setDeleteBusy(true);
      setDeleteError(null);
      try {
        const res = await deletionApi.request(confirmation);
        setDeletion(res);
        setSignedOut(res);
        setDialog(null);
      } catch (err) {
        if (err instanceof ApiError && err.code === 'step_up_required') {
          const action: PendingAction = { kind: 'delete', confirmation };
          setPendingAction(action);
          setDialog({ kind: 'step-up', action });
          return;
        }
        if (isUnauthenticated(err)) {
          toLogin();
          return;
        }
        setDeleteError(describeError(err));
      } finally {
        setDeleteBusy(false);
      }
    },
    [toLogin],
  );

  /** BR-03 restore: login + one click, no step-up; sessions survive the cancel. */
  const restoreAccount = useCallback(async () => {
    try {
      const res = await deletionApi.cancel();
      setDeletion(res);
      setNotice(t('settings.deletion.cancelledNotice'));
    } catch (err) {
      if (err instanceof ApiError && err.code === 'delete_not_requested') {
        setNotice(describeError(err));
        const fresh = await deletionApi.status().catch(() => null);
        setDeletion(fresh);
        return;
      }
      refuse(err);
    } finally {
      setDialog(null);
    }
  }, [refuse, t]);

  const bootstrap = useCallback(async (): Promise<boolean> => {
    try {
      const [m, ps, ss] = await Promise.all([accountApi.me(), accountApi.providers(), accountApi.sessions()]);
      setMe(m);
      setProviders(ps.providers);
      setSessions(ss.sessions);
      // Purge-completed rows answer 410; no session can exist post-purge, so a
      // stale tab simply renders without a panel (BA 05).
      const del = await deletionApi.status().catch((err) => {
        if (err instanceof ApiError && err.code === 'purge_completed') return null;
        throw err;
      });
      setDeletion(del);
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
      if (pending.kind === 'delete') await runDelete(pending.confirmation);
      else if (pending.kind === 'unlink') await runUnlink(pending.provider, pending.label);
      else await startLink(pending.provider, pending.label);
    })();
  }, [bootstrap, runDelete, runUnlink, startLink]);

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
    action.kind === 'delete'
      ? t('actions.delete')
      : t(action.kind === 'link' ? 'actions.link' : 'actions.unlink', { provider: action.label });
  const daysLeftText = (iso: string) => {
    const days = daysLeft(iso);
    return days === 1 ? t('settings.deletion.dayLeft') : t('settings.deletion.daysLeft', { count: days });
  };

  // BR-02: the request commit revoked every session, this tab included - the
  // panel renders from the 200 response and offers the sign-in path to restore.
  if (signedOut?.purgeDueAt) {
    return (
      <main className="mx-auto w-full max-w-2xl space-y-6">
        <h1 className="font-heading text-2xl font-semibold">{t('settings.title')}</h1>
        <Card>
          <CardHeader>
            <CardTitle>{t('settings.deletion.scheduledTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-2">
            <p className="text-sm">
              {t('settings.deletion.scheduledFor', { date: purgeDate(signedOut.purgeDueAt) })}
            </p>
            <p className="text-sm text-muted-foreground">
              {daysLeftText(signedOut.purgeDueAt)} {t('settings.deletion.signInAgain')}
            </p>
          </CardContent>
        </Card>
        <Notice tone="info">{t('settings.deletion.signedOut')}</Notice>
        <Button type="button" variant="outline" onClick={() => navigate('/login')}>
          {t('settings.deletion.signIn')}
        </Button>
      </main>
    );
  }

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

      {deletion?.status === 'grace' && deletion.purgeDueAt ? (
        <Card>
          <CardHeader>
            <CardTitle>{t('settings.deletion.scheduledTitle')}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            <p className="text-sm">{t('settings.deletion.scheduledFor', { date: purgeDate(deletion.purgeDueAt) })}</p>
            <p className="text-sm text-muted-foreground">{daysLeftText(deletion.purgeDueAt)}</p>
            <Button type="button" onClick={() => setDialog({ kind: 'confirm-restore' })}>
              {t('settings.deletion.restore')}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardHeader>
            <CardTitle>{t('settings.deletion.title')}</CardTitle>
            <CardDescription>{t('settings.deletion.description')}</CardDescription>
            {deletion?.status === 'cancelled' && (
              <CardAction>
                <Badge variant="outline">{t('settings.deletion.cancelledChip')}</Badge>
              </CardAction>
            )}
          </CardHeader>
          <CardContent className="flex justify-end">
            <Button type="button" variant="destructive" onClick={openDeleteDialog}>
              {t('settings.deletion.entry')}
            </Button>
          </CardContent>
        </Card>
      )}

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
      {dialog?.kind === 'confirm-restore' && (
        <ConfirmDialog
          title={t('settings.deletion.restoreTitle')}
          body={t('settings.deletion.restoreBody')}
          confirmLabel={t('settings.deletion.restore')}
          onCancel={() => setDialog(null)}
          onConfirm={() => void restoreAccount()}
        />
      )}
      {dialog?.kind === 'delete' && (
        <DeleteAccountDialog
          expected={expected}
          error={deleteError}
          busy={deleteBusy}
          onCancel={() => setDialog(null)}
          onConfirm={(confirmation) => void runDelete(confirmation)}
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
