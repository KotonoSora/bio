import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { MFE_ERROR_EVENT, type MfeHostElement } from '@resume-builder/mfe-kit';
import { Alert, AlertAction, AlertDescription, Button, detectLocale } from '@resume-builder/ui';
import { useTranslation } from 'react-i18next';
import { assetUrl, fetchManifest } from './manifest';

// Resilience budget (platform doc 10): manifest + stylesheet + entry must
// complete within 10 s or the shell shows the retryable fallback.
const MOUNT_TIMEOUT_MS = 10_000;

type Phase = 'loading' | 'ready' | 'error';

/**
 * Shell-side composition seam: resolves the vertical from the deploy manifest,
 * injects its stylesheet as a <link> (CSP `style-src 'self'` forbids runtime
 * <style> injection), lazy-imports its ESM entry, and drives the custom
 * element via `mfeContext` ("DOM is the API"). Failures degrade to a retryable
 * fallback instead of taking down the shell.
 */
export default function MfeMount({ name }: { name: string }) {
  const navigate = useNavigate();
  const navigateRef = useRef(navigate);
  navigateRef.current = navigate;
  const hostRef = useRef<HTMLDivElement | null>(null);
  const [phase, setPhase] = useState<Phase>('loading');
  const [attempt, setAttempt] = useState(0);
  const { t } = useTranslation();

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    let cancelled = false;
    let element: MfeHostElement | null = null;
    let timer: number | undefined;
    const timeout = new Promise<never>((_, reject) => {
      timer = window.setTimeout(() => reject(new Error('mfe mount timeout')), MOUNT_TIMEOUT_MS);
    });
    const fail = () => {
      if (!cancelled) setPhase('error');
    };
    const onMfeError = () => fail();

    setPhase('loading');
    void (async () => {
      try {
        const manifest = await Promise.race([fetchManifest(), timeout]);
        const entry = manifest[name];
        if (!entry) throw new Error(`mfe missing from manifest: ${name}`);
        if (entry.css) {
          await Promise.race([injectStylesheet(assetUrl(entry.css)), timeout]);
        }
        await Promise.race([import(/* @vite-ignore */ assetUrl(entry.entry)), timeout]);
        if (cancelled) return;
        if (!customElements.get(entry.tag)) throw new Error(`mfe entry did not register ${entry.tag}`);
        const mounted = document.createElement(entry.tag) as MfeHostElement;
        mounted.addEventListener(MFE_ERROR_EVENT, onMfeError);
        element = mounted;
        host.replaceChildren(mounted);
        mounted.mfeContext = {
          locale: detectLocale(),
          navigate: (to) => navigateRef.current(to),
        };
        setPhase('ready');
      } catch {
        fail();
      } finally {
        window.clearTimeout(timer);
      }
    })();

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
      element?.removeEventListener(MFE_ERROR_EVENT, onMfeError);
      host.replaceChildren(); // disconnect -> MFE dispose; module stays cached
    };
  }, [name, attempt]);

  return (
    <section aria-busy={phase === 'loading'}>
      {phase === 'loading' && <MfeSkeleton />}
      {phase === 'error' && (
        <Alert variant="destructive">
          <AlertDescription className="text-current">{t('mfe.failed')}</AlertDescription>
          <AlertAction>
            <Button type="button" variant="outline" size="sm" onClick={() => setAttempt((n) => n + 1)}>
              {t('common.retry')}
            </Button>
          </AlertAction>
        </Alert>
      )}
      <div ref={hostRef} hidden={phase !== 'ready'} />
    </section>
  );
}

function injectStylesheet(href: string): Promise<void> {
  return new Promise((resolve) => {
    const link = document.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.addEventListener('load', () => resolve());
    link.addEventListener('error', () => resolve()); // a failed stylesheet is not fatal
    document.head.appendChild(link);
  });
}

function MfeSkeleton() {
  return (
    <div className="mx-auto w-full max-w-2xl space-y-6">
      <div className="flex items-center justify-between gap-3">
        <div className="h-8 w-48 animate-pulse rounded-md bg-muted" />
        <div className="h-9 w-24 animate-pulse rounded-md bg-muted" />
      </div>
      {[0, 1, 2].map((i) => (
        <div key={i} className="h-36 animate-pulse rounded-xl bg-muted" />
      ))}
    </div>
  );
}
