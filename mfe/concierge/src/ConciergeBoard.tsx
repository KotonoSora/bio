import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  Badge,
  Button,
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Notice,
} from '@resume-builder/ui';
import {
  conciergeApi,
  type ContractResponse,
  type TaskResponse,
} from './api/concierge';
import { describeError, isUnauthenticated } from './lib/errors';
import { stamp } from './lib/dates';
import { refusalKey } from './lib/refusals';
import { useMfeContext } from './mfe-context';

type DialogState =
  | { kind: 'approve-setup' }
  | { kind: 'approve-task'; task: TaskResponse }
  | { kind: 'pause' }
  | { kind: 'exit'; needsDecision: boolean }
  | { kind: 'cancel-request' }
  | null;

const HANDOFF_ACK = 'HANDOFF';

/**
 * Concierge board (feature 05, BA 04). Server truth only: the contract, its
 * SOW, the approval records, and the task board come from
 * `/api/v1/concierge/**`; no client-side state machine invents a status. Codes
 * drive every message (BA 05) and a 401 sends the visitor to sign-in.
 */
export default function ConciergeBoard() {
  const { navigate } = useMfeContext();
  const { t } = useTranslation();
  const [contract, setContract] = useState<ContractResponse | null>(null);
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [scope, setScope] = useState('');
  const [kind, setKind] = useState('');
  const [notes, setNotes] = useState('');
  const [handoffChoice, setHandoffChoice] = useState<'transfer' | 'cancel'>('cancel');
  const [ack, setAck] = useState('');

  const toLogin = useCallback(
    () => navigate('/login?notice=expired&next=' + encodeURIComponent('/app/concierge')),
    [navigate],
  );

  const load = useCallback(async () => {
    try {
      const [current, rows] = await Promise.all([conciergeApi.contract(), conciergeApi.tasks()]);
      setContract(current);
      setTasks(rows);
      setError(null);
    } catch (err) {
      if (isUnauthenticated(err)) {
        toLogin();
        return;
      }
      setError(describeError(err));
    } finally {
      setLoaded(true);
    }
  }, [toLogin]);

  const booted = useRef(false);
  useEffect(() => {
    if (booted.current) return;
    booted.current = true;
    void load();
  }, [load]);

  const run = useCallback(
    async (action: () => Promise<unknown>, done?: () => void) => {
      setBusy(true);
      setError(null);
      try {
        await action();
        done?.();
        await load();
      } catch (err) {
        if (isUnauthenticated(err)) {
          toLogin();
          return;
        }
        setError(describeError(err));
      } finally {
        setBusy(false);
      }
    },
    [load, toLogin],
  );

  const canRequest = contract?.status === 'active' && !contract.pendingTransition && !contract.handoff;
  const setupPending = contract?.status === 'draft' && !contract.setupApproval;

  /** Latest recorded miss on the board (completion outranks response; BA 05). */
  const boardMiss = useMemo(() => {
    if (tasks.some((row) => row.slaMiss === 'completion')) return 'completion' as const;
    if (tasks.some((row) => row.slaMiss === 'response')) return 'response' as const;
    return null;
  }, [tasks]);

  if (!loaded) {
    return (
      <div className="mx-auto w-full max-w-3xl space-y-6" aria-busy="true">
        {[0, 1].map((i) => (
          <div key={i} className="h-32 animate-pulse rounded-xl bg-muted" />
        ))}
      </div>
    );
  }

  return (
    <div className="mx-auto w-full max-w-3xl space-y-6" data-testid="concierge-board">
      <header className="space-y-1">
        <h1 className="font-heading text-2xl font-semibold">{t('title')}</h1>
        <p className="text-sm text-muted-foreground">{t('subtitle')}</p>
      </header>

      {error && <Notice tone="error">{error}</Notice>}
      {notice && <Notice tone="info">{notice}</Notice>}

      {!contract ? (
        <Card data-testid="concierge-empty">
          <CardHeader>
            <CardTitle>{t('empty.title')}</CardTitle>
            <CardDescription>{t('empty.body')}</CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" variant="outline" onClick={() => navigate('/pricing')}>
              {t('empty.cta')}
            </Button>
          </CardContent>
        </Card>
      ) : (
        <>
          <ContractCard
            contract={contract}
            busy={busy}
            onPause={() => setDialog({ kind: 'pause' })}
            onExit={() => setDialog({ kind: 'exit', needsDecision: false })}
            onConfirmPause={() => run(() => conciergeApi.pause())}
            onConfirmExit={() => setDialog({ kind: 'exit', needsDecision: true })}
            onCancelRequest={() => setDialog({ kind: 'cancel-request' })}
          />
          <SowPanel contract={contract} />
          <SlaRow contract={contract} boardMiss={boardMiss} />
          {setupPending && (
            <Card>
              <CardHeader>
                <CardTitle>{t('setup.heading')}</CardTitle>
                <CardDescription>{t('setup.body')}</CardDescription>
              </CardHeader>
              <CardContent>
                <Button type="button" onClick={() => setDialog({ kind: 'approve-setup' })} disabled={busy}>
                  {t('setup.approve')}
                </Button>
              </CardContent>
            </Card>
          )}
          {contract.setupApproval && (
            <Card>
              <CardHeader>
                <CardTitle>{t('setup.recorded')}</CardTitle>
                <CardDescription>
                  {contract.setupApproval.scope} · {t('setup.channel', { channel: contract.setupApproval.channel })} ·{' '}
                  {stamp(contract.setupApproval.approvedAt)}
                </CardDescription>
              </CardHeader>
            </Card>
          )}
          <Board
            contract={contract}
            tasks={tasks}
            busy={busy}
            kind={kind}
            notes={notes}
            onKind={setKind}
            onNotes={setNotes}
            canRequest={canRequest}
            onRequest={() =>
              run(
                () => conciergeApi.requestTask(kind, notes.trim() === '' ? null : notes.trim()),
                () => setNotes(''),
              )
            }
            onApprove={(task) => setDialog({ kind: 'approve-task', task })}
          />
          {contract.handoff && <HandoffPanel contract={contract} />}
        </>
      )}

      <ApproveSetupDialog
        open={dialog?.kind === 'approve-setup'}
        busy={busy}
        scope={scope}
        onScope={setScope}
        onSubmit={() =>
          run(
            () => conciergeApi.approveSetupPlan(scope.trim()),
            () => {
              setScope('');
              setNotice(null);
            },
          )
        }
        onClose={() => setDialog(null)}
      />

      <ApproveTaskDialog
        task={dialog?.kind === 'approve-task' ? dialog.task : null}
        busy={busy}
        scope={scope}
        onScope={setScope}
        onSubmit={(task) =>
          run(
            () => conciergeApi.approveTask(task.id, scope.trim()),
            () => setScope(''),
          )
        }
        onClose={() => setDialog(null)}
      />

      <PauseDialog
        open={dialog?.kind === 'pause'}
        busy={busy}
        onConfirm={() => run(() => conciergeApi.pause())}
        onClose={() => setDialog(null)}
      />

      <ExitDialog
        open={dialog?.kind === 'exit'}
        needsDecision={dialog?.kind === 'exit' && dialog.needsDecision}
        busy={busy}
        choice={handoffChoice}
        ack={ack}
        onChoice={setHandoffChoice}
        onAck={setAck}
        onConfirm={() =>
          run(
            () =>
              dialog?.kind === 'exit' && dialog.needsDecision
                ? conciergeApi.exit({ scheduledWork: handoffChoice, acknowledge: ack })
                : conciergeApi.exit(),
            () => {
              setAck('');
              setNotice(null);
            },
          )
        }
        onClose={() => setDialog(null)}
      />

      <CancelRequestDialog
        open={dialog?.kind === 'cancel-request'}
        busy={busy}
        onConfirm={() => run(() => conciergeApi.cancelTransition())}
        onClose={() => setDialog(null)}
      />
    </div>
  );
}

function statusBadge(contract: ContractResponse) {
  switch (contract.status) {
    case 'draft':
      return 'secondary';
    case 'active':
      return 'default';
    case 'paused':
      return 'outline';
    default:
      return 'destructive';
  }
}

function ContractCard({
  contract,
  busy,
  onPause,
  onExit,
  onConfirmPause,
  onConfirmExit,
  onCancelRequest,
}: {
  contract: ContractResponse;
  busy: boolean;
  onPause: () => void;
  onExit: () => void;
  onConfirmPause: () => void;
  onConfirmExit: () => void;
  onCancelRequest: () => void;
}) {
  const { t } = useTranslation();
  const pending = contract.pendingTransition;
  const agencyAsked = pending !== null && contract.pendingBy === 'agency';
  return (
    <Card data-testid="concierge-contract-card">
      <CardHeader>
        <CardTitle>{t('card.heading')}</CardTitle>
        <CardDescription>
          {t('card.tier')}: <span className="font-medium text-foreground">{contract.slaTier}</span>
          {contract.activatedAt && ` · ${t('card.activated')} ${stamp(contract.activatedAt)}`}
          {contract.exitedAt && ` · ${t('card.exitedAt')} ${stamp(contract.exitedAt)}`}
        </CardDescription>
        <CardAction>
          <Badge variant={statusBadge(contract)}>{t(`states.${contract.status}`)}</Badge>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-sm text-muted-foreground">
          {t('sow.version', { version: contract.sowVersion ?? 1 })}
        </p>
        {pending && <Notice tone="info">{agencyAsked ? t('card.agencyAsked') : t('card.waitingCounterpart')}</Notice>}
        {contract.status === 'paused' && <p className="text-sm text-muted-foreground">{t('card.pausedNote')}</p>}
        <div className="flex flex-wrap gap-2">
          {contract.status === 'active' && !pending && !contract.handoff && (
            <>
              <Button type="button" variant="outline" disabled={busy} onClick={onPause}>
                {t('card.pause')}
              </Button>
              <Button type="button" variant="outline" disabled={busy} onClick={onExit}>
                {t('card.exit')}
              </Button>
            </>
          )}
          {agencyAsked && contract.pendingTransition === 'pause' && (
            <Button type="button" disabled={busy} onClick={onConfirmPause}>
              {t('card.confirmPause')}
            </Button>
          )}
          {agencyAsked && contract.pendingTransition === 'exit' && (
            <Button type="button" disabled={busy} onClick={onConfirmExit}>
              {t('card.confirmExit')}
            </Button>
          )}
          {pending && contract.pendingBy !== 'agency' && (
            <Button type="button" variant="ghost" disabled={busy} onClick={onCancelRequest}>
              {t('card.cancelRequest')}
            </Button>
          )}
        </div>
      </CardContent>
    </Card>
  );
}

function SowPanel({ contract }: { contract: ContractResponse }) {
  const { t } = useTranslation();
  return (
    <Card data-testid="concierge-sow">
      <CardHeader>
        <CardTitle>{t('sow.heading')}</CardTitle>
      </CardHeader>
      <CardContent>
        <ul className="divide-y">
          {contract.sow.map((item) => (
            <li key={item.kind} className="flex items-center justify-between py-2 text-sm">
              <span>{item.kind}</span>
              <span className="text-muted-foreground">
                {t('sow.volume')}: {item.volume ?? t('sow.unlimited')}
              </span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function SlaRow({
  contract,
  boardMiss,
}: {
  contract: ContractResponse;
  boardMiss: 'response' | 'completion' | null;
}) {
  const { t } = useTranslation();
  const sla = contract.sla;
  const window = (minutes: number | null | undefined) =>
    minutes === null || minutes === undefined ? t('sla.unset') : t('sla.minutes', { minutes });
  return (
    <Card data-testid="concierge-sla">
      <CardHeader>
        <CardTitle>{t('sla.heading')}</CardTitle>
        <CardDescription>
          {t('sla.tier')}: {sla?.tier ?? contract.slaTier} · {t('sla.response')}:{' '}
          {window(sla?.responseWindowMinutes)} · {t('sla.completion')}: {window(sla?.completionWindowMinutes)}
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-2 text-sm">
        <p className="text-muted-foreground">
          {t('sla.creditRule')}: {sla?.creditRule ?? '—'}
        </p>
        {boardMiss ? (
          <p className="font-medium text-amber-900">{t('sla.missed', { window: boardMiss })}</p>
        ) : (
          <p className="text-muted-foreground">{t('sla.within')}</p>
        )}
      </CardContent>
    </Card>
  );
}

function Board({
  contract,
  tasks,
  busy,
  kind,
  notes,
  onKind,
  onNotes,
  canRequest,
  onRequest,
  onApprove,
}: {
  contract: ContractResponse;
  tasks: TaskResponse[];
  busy: boolean;
  kind: string;
  notes: string;
  onKind: (value: string) => void;
  onNotes: (value: string) => void;
  canRequest: boolean;
  onRequest: () => void;
  onApprove: (task: TaskResponse) => void;
}) {
  const { t } = useTranslation();
  return (
    <Card>
      <CardHeader>
        <CardTitle>{t('board.heading')}</CardTitle>
        {!canRequest && <CardDescription>{t('board.hidden')}</CardDescription>}
      </CardHeader>
      <CardContent className="space-y-4">
        {canRequest && (
          <div className="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)_auto] sm:items-end">
            <div className="space-y-1">
              <Label htmlFor="concierge-kind">{t('board.kind')}</Label>
              <select
                id="concierge-kind"
                className="h-9 w-full rounded-md border bg-transparent px-2 text-sm"
                value={kind}
                onChange={(event) => onKind(event.target.value)}
              >
                <option value="">{t('board.kindPlaceholder')}</option>
                {contract.sow.map((item) => (
                  <option key={item.kind} value={item.kind}>
                    {item.kind}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="concierge-notes">{t('board.notes')}</Label>
              <Input
                id="concierge-notes"
                value={notes}
                onChange={(event) => onNotes(event.target.value)}
                maxLength={500}
              />
            </div>
            <Button type="button" disabled={busy || kind === ''} onClick={onRequest}>
              {t('board.submit')}
            </Button>
          </div>
        )}
        <div data-testid="concierge-work-log" className="space-y-3">
          {tasks.length === 0 && <p className="text-sm text-muted-foreground">{t('board.empty')}</p>}
          {[...tasks].reverse().map((task) => (
            <TaskRow key={task.id} task={task} busy={busy} onApprove={onApprove} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function TaskRow({
  task,
  busy,
  onApprove,
}: {
  task: TaskResponse;
  busy: boolean;
  onApprove: (task: TaskResponse) => void;
}) {
  const { t } = useTranslation();
  const refusals = t('refusals', { returnObjects: true }) as Record<string, string>;
  return (
    <div className="rounded-lg border p-3 text-sm">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-medium">{task.kind}</span>
        <Badge variant={task.status === 'refused' ? 'destructive' : 'outline'}>
          {t(`taskStates.${task.status}`)}
        </Badge>
        <span className="text-xs text-muted-foreground">{t(`origin.${task.origin}` as 'origin.client')}</span>
        {task.origin === 'client' && task.status === 'requested' && (
          <Button type="button" size="xs" variant="outline" className="ml-auto" disabled={busy} onClick={() => onApprove(task)}>
            {t('task.approve')}
          </Button>
        )}
      </div>
      <dl className="mt-2 grid gap-1 text-xs text-muted-foreground">
        <div>{t('task.requested', { date: stamp(task.requestedAt) })}</div>
        {task.approvedAt && <div>{t('task.approved', { date: stamp(task.approvedAt) })}</div>}
        {task.completedAt && <div>{t('task.completed', { date: stamp(task.completedAt) })}</div>}
        {task.notes && <div>{t('task.notes', { notes: task.notes })}</div>}
        {task.operatorName && <div>{t('task.operator', { name: task.operatorName })}</div>}
        {task.workRefs.length > 0 && <div>{task.workRefs.join(', ')}</div>}
        {task.slaMiss && <div className="text-amber-900">{t('sla.missed', { window: task.slaMiss })}</div>}
      </dl>
      {task.status === 'refused' && (
        <div data-testid="concierge-refusal-notice" className="mt-2 rounded-md border border-destructive/25 bg-destructive/5 p-2 text-destructive">
          <p className="font-medium">{t('refusals.heading')}</p>
          <p>{refusals[refusalKey(task.refusalReason)]}</p>
          {task.referral && (
            <p className="mt-1 text-xs">
              {t('refusals.referral')}: <span className="underline underline-offset-2">{task.referral}</span>
            </p>
          )}
        </div>
      )}
      {(task.approvals ?? []).length > 0 && (
        <div className="mt-2 text-xs text-muted-foreground">
          <p className="font-medium text-foreground">{t('task.approvalsHeading')}</p>
          <ul className="mt-1 space-y-0.5">
            {(task.approvals ?? []).map((approval) => (
              <li key={approval.id}>
                {approval.scope} · {approval.channel === 'dashboard' ? t('task.channel.dashboard') : t('task.channel.signed_email')} ·{' '}
                {stamp(approval.approvedAt)}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

function HandoffPanel({ contract }: { contract: ContractResponse }) {
  const { t } = useTranslation();
  const handoff = contract.handoff;
  if (!handoff) return null;
  const labels = t('handoff.itemLabels', { returnObjects: true }) as Record<string, string>;
  return (
    <Card data-testid="concierge-handoff">
      <CardHeader>
        <CardTitle>{t('handoff.heading')}</CardTitle>
        <CardDescription>
          {t('handoff.started', { date: stamp(handoff.startedAt) })}
          {handoff.scheduledWork && ` · ${t(`handoff.choice.${handoff.scheduledWork}`)}`}
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ul className="divide-y">
          {handoff.items.map((item) => (
            <li key={item.key} className="flex items-center justify-between py-2 text-sm">
              <span>{labels[item.key] ?? item.key}</span>
              <span className="text-xs text-muted-foreground">
                {item.at ? t('handoff.done', { date: stamp(item.at) }) : t('handoff.pending')}
              </span>
            </li>
          ))}
        </ul>
      </CardContent>
    </Card>
  );
}

function ApproveSetupDialog({
  open,
  busy,
  scope,
  onScope,
  onSubmit,
  onClose,
}: {
  open: boolean;
  busy: boolean;
  scope: string;
  onScope: (value: string) => void;
  onSubmit: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('setup.approve')}</DialogTitle>
          <DialogDescription>{t('setup.body')}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="setup-scope">{t('setup.scope')}</Label>
          <Input
            id="setup-scope"
            value={scope}
            onChange={(event) => onScope(event.target.value)}
            placeholder={t('setup.scopePlaceholder')}
            maxLength={500}
          />
          <p className="text-xs text-muted-foreground">{t('task.approveNote')}</p>
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('dialogs.close')}
          </Button>
          <Button type="button" disabled={busy || scope.trim() === ''} onClick={onSubmit}>
            {busy ? t('common.saving') : t('setup.approve')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ApproveTaskDialog({
  task,
  busy,
  scope,
  onScope,
  onSubmit,
  onClose,
}: {
  task: TaskResponse | null;
  busy: boolean;
  scope: string;
  onScope: (value: string) => void;
  onSubmit: (task: TaskResponse) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog open={task !== null} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('task.approveTitle')}</DialogTitle>
          <DialogDescription>{task ? `${task.kind}` : ''}</DialogDescription>
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="task-scope">{t('task.approveScope')}</Label>
          <Input
            id="task-scope"
            value={scope}
            onChange={(event) => onScope(event.target.value)}
            placeholder={t('task.approveScopePlaceholder')}
            maxLength={500}
          />
          <p className="text-xs text-muted-foreground">{t('task.approveNote')}</p>
        </div>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('dialogs.close')}
          </Button>
          <Button type="button" disabled={busy || scope.trim() === ''} onClick={() => task && onSubmit(task)}>
            {busy ? t('common.saving') : t('task.approve')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function PauseDialog({
  open,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.pauseTitle')}</DialogTitle>
          <DialogDescription>{t('dialogs.pauseBody')}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('dialogs.close')}
          </Button>
          <Button type="button" disabled={busy} onClick={onConfirm}>
            {busy ? t('common.saving') : t('dialogs.pauseConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function ExitDialog({
  open,
  needsDecision,
  busy,
  choice,
  ack,
  onChoice,
  onAck,
  onConfirm,
  onClose,
}: {
  open: boolean;
  needsDecision: boolean;
  busy: boolean;
  choice: 'transfer' | 'cancel';
  ack: string;
  onChoice: (value: 'transfer' | 'cancel') => void;
  onAck: (value: string) => void;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const labels = t('handoff.itemLabels', { returnObjects: true }) as Record<string, string>;
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{needsDecision ? t('card.confirmExit') : t('dialogs.exitTitle')}</DialogTitle>
          <DialogDescription>{t('dialogs.exitBody')}</DialogDescription>
        </DialogHeader>
        <ul className="list-inside list-disc text-sm">
          {Object.values(labels).map((label) => (
            <li key={label}>{label}</li>
          ))}
        </ul>
        {needsDecision && (
          <div className="space-y-3">
            <div className="space-y-1">
              <Label htmlFor="handoff-choice">{t('dialogs.handoffChoice')}</Label>
              <select
                id="handoff-choice"
                className="h-9 w-full rounded-md border bg-transparent px-2 text-sm"
                value={choice}
                onChange={(event) => onChoice(event.target.value as 'transfer' | 'cancel')}
              >
                <option value="transfer">{t('dialogs.handoffTransfer')}</option>
                <option value="cancel">{t('dialogs.handoffCancel')}</option>
              </select>
            </div>
            <div className="space-y-1">
              <Label htmlFor="handoff-ack">{t('dialogs.handoffAck')}</Label>
              <Input id="handoff-ack" value={ack} onChange={(event) => onAck(event.target.value)} />
              <p className="text-xs text-muted-foreground">{t('dialogs.handoffAckHint')}</p>
            </div>
          </div>
        )}
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('dialogs.close')}
          </Button>
          <Button type="button" disabled={busy || (needsDecision && ack.trim() !== HANDOFF_ACK)} onClick={onConfirm}>
            {busy ? t('common.saving') : needsDecision ? t('card.confirmExit') : t('dialogs.exitConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}

function CancelRequestDialog({
  open,
  busy,
  onConfirm,
  onClose,
}: {
  open: boolean;
  busy: boolean;
  onConfirm: () => void;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Dialog open={open} onOpenChange={(next) => !next && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('dialogs.cancelRequestTitle')}</DialogTitle>
          <DialogDescription>{t('dialogs.cancelRequestBody')}</DialogDescription>
        </DialogHeader>
        <DialogFooter>
          <Button type="button" variant="ghost" onClick={onClose}>
            {t('dialogs.close')}
          </Button>
          <Button type="button" disabled={busy} onClick={onConfirm}>
            {busy ? t('common.saving') : t('dialogs.cancelRequestConfirm')}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
