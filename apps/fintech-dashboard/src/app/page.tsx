"use client";

import { useState, useEffect, useCallback } from "react";

// ─── Types ────────────────────────────────────────────────────────────────────

type PixStatus = "idle" | "loading" | "success" | "error";
type RecoverStatus = "idle" | "loading" | "success" | "error";

interface PixForm {
  fromAccountId: string;
  toAccountId: string;
  amountCents: string;
}

interface PixResult {
  id: string;
  status: string;
  amountCents: number;
}

interface OutboxMetrics {
  pending: number;
  sent: number;
  deadLetter: number;
  error?: string;
}

interface RecoverResult {
  recovered: number;
  message: string;
}

// ─── Constants ────────────────────────────────────────────────────────────────

const BACKEND = "http://localhost:8090";
const POLLING_INTERVAL_MS = 5000;

const DEFAULT_FORM: PixForm = {
  fromAccountId: "a0000000-0000-0000-0000-000000000001",
  toAccountId: "b0000000-0000-0000-0000-000000000002",
  amountCents: "99900",
};

// ─── Sub-components ───────────────────────────────────────────────────────────

function SectionCard({
  title,
  subtitle,
  children,
  accent,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  accent?: string;
}) {
  return (
    <div
      className={`rounded-2xl border bg-gray-900 p-6 shadow-xl ${accent ?? "border-gray-800"}`}
    >
      <div className="mb-5">
        <h2 className="text-lg font-semibold text-white">{title}</h2>
        <p className="mt-0.5 text-sm text-gray-400">{subtitle}</p>
      </div>
      {children}
    </div>
  );
}

function StatusBadge({
  label,
  value,
  color,
}: {
  label: string;
  value: number;
  color: string;
}) {
  return (
    <div className={`flex flex-col items-center rounded-xl p-4 ${color}`}>
      <span className="text-3xl font-bold tabular-nums">{value}</span>
      <span className="mt-1 text-xs font-medium uppercase tracking-widest opacity-80">
        {label}
      </span>
    </div>
  );
}

// ─── Main Page ────────────────────────────────────────────────────────────────

export default function Home() {
  // Pix form state
  const [form, setForm] = useState<PixForm>(DEFAULT_FORM);
  const [pixStatus, setPixStatus] = useState<PixStatus>("idle");
  const [pixResult, setPixResult] = useState<PixResult | null>(null);
  const [pixError, setPixError] = useState<string>("");

  // Outbox metrics state
  const [metrics, setMetrics] = useState<OutboxMetrics | null>(null);
  const [metricsLoading, setMetricsLoading] = useState(true);

  // Recover state
  const [recoverStatus, setRecoverStatus] = useState<RecoverStatus>("idle");
  const [recoverResult, setRecoverResult] = useState<RecoverResult | null>(null);
  const [recoverError, setRecoverError] = useState<string>("");

  // ── Outbox metrics polling ──────────────────────────────────────────────────

  const fetchMetrics = useCallback(async () => {
    try {
      const res = await fetch("/api/outbox-metrics");
      const data: OutboxMetrics = await res.json();
      setMetrics(data);
    } catch {
      setMetrics({ pending: 0, sent: 0, deadLetter: 0, error: "offline" });
    } finally {
      setMetricsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, POLLING_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [fetchMetrics]);

  // ── Pix transfer submit ─────────────────────────────────────────────────────

  async function handlePixSubmit(e: React.FormEvent) {
    e.preventDefault();
    setPixStatus("loading");
    setPixResult(null);
    setPixError("");

    try {
      const res = await fetch(`${BACKEND}/transfers`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Idempotency-Key": crypto.randomUUID(),
        },
        body: JSON.stringify({
          fromAccountId: form.fromAccountId,
          toAccountId: form.toAccountId,
          amountCents: parseInt(form.amountCents, 10),
          currency: "BRL",
        }),
      });

      if (!res.ok) {
        const err = await res.text();
        throw new Error(`HTTP ${res.status}: ${err}`);
      }

      const data = await res.json();
      setPixResult(data);
      setPixStatus("success");
    } catch (err) {
      setPixError(err instanceof Error ? err.message : "Erro desconhecido");
      setPixStatus("error");
    }
  }

  // ── Recover dead letters ────────────────────────────────────────────────────

  async function handleRecover() {
    setRecoverStatus("loading");
    setRecoverResult(null);
    setRecoverError("");

    try {
      const res = await fetch(`${BACKEND}/admin/outbox/recover`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
      });

      if (!res.ok) throw new Error(`HTTP ${res.status}`);

      const data: RecoverResult = await res.json();
      setRecoverResult(data);
      setRecoverStatus("success");
      // Força refresh imediato das métricas após recovery
      setTimeout(fetchMetrics, 1500);
    } catch (err) {
      setRecoverError(err instanceof Error ? err.message : "Erro desconhecido");
      setRecoverStatus("error");
    }
  }

  // ── Derived UI state ────────────────────────────────────────────────────────

  const hasCriticalDeadLetters =
    metrics !== null && !metrics.error && metrics.deadLetter > 0;

  const outboxCardBorder = hasCriticalDeadLetters
    ? "border-red-500"
    : "border-gray-800";

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      {/* Header */}
      <header className="mb-10">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-xl font-bold">
            F
          </div>
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-white">
              Fintech MVP
            </h1>
            <p className="text-sm text-gray-400">
              Painel Operacional &amp; Administrativo
            </p>
          </div>
        </div>
      </header>

      <div className="grid gap-6 lg:grid-cols-2">
        {/* ── Seção 1: Simulador Pix ─────────────────────────────────────── */}
        <SectionCard
          title="Simulador Pix"
          subtitle="Dispara uma transferência via POST /transfers com Idempotency-Key gerada automaticamente."
          accent="border-indigo-800"
        >
          <form onSubmit={handlePixSubmit} className="space-y-4">
            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">
                From Account ID
              </label>
              <input
                type="text"
                value={form.fromAccountId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, fromAccountId: e.target.value }))
                }
                className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                placeholder="UUID da conta de origem"
                required
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">
                To Account ID
              </label>
              <input
                type="text"
                value={form.toAccountId}
                onChange={(e) =>
                  setForm((f) => ({ ...f, toAccountId: e.target.value }))
                }
                className="w-full rounded-lg border border-gray-700 bg-gray-800 px-3 py-2 text-sm text-white placeholder-gray-500 focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                placeholder="UUID da conta de destino"
                required
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-gray-400">
                Valor (centavos)
              </label>
              <div className="relative">
                <span className="absolute left-3 top-1/2 -translate-y-1/2 text-sm text-gray-400">
                  R$
                </span>
                <input
                  type="number"
                  value={form.amountCents}
                  onChange={(e) =>
                    setForm((f) => ({ ...f, amountCents: e.target.value }))
                  }
                  className="w-full rounded-lg border border-gray-700 bg-gray-800 py-2 pl-9 pr-3 text-sm text-white focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500"
                  placeholder="ex: 99900 = R$ 999,00"
                  min="1"
                  required
                />
              </div>
              {form.amountCents && (
                <p className="mt-1 text-xs text-gray-500">
                  ={" "}
                  {(parseInt(form.amountCents, 10) / 100).toLocaleString(
                    "pt-BR",
                    { style: "currency", currency: "BRL" }
                  )}
                </p>
              )}
            </div>

            <button
              type="submit"
              disabled={pixStatus === "loading"}
              className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {pixStatus === "loading" ? "Enviando..." : "Enviar Transferência"}
            </button>
          </form>

          {/* Feedback Pix */}
          {pixStatus === "success" && pixResult && (
            <div className="mt-4 rounded-lg border border-green-700 bg-green-950 p-4">
              <p className="text-sm font-semibold text-green-400">
                Transferência criada com sucesso
              </p>
              <p className="mt-1 font-mono text-xs text-green-300 break-all">
                ID: {pixResult.id}
              </p>
              <p className="mt-0.5 text-xs text-green-400">
                Status: {pixResult.status} &mdash;{" "}
                {(pixResult.amountCents / 100).toLocaleString("pt-BR", {
                  style: "currency",
                  currency: "BRL",
                })}
              </p>
            </div>
          )}

          {pixStatus === "error" && (
            <div className="mt-4 rounded-lg border border-red-700 bg-red-950 p-4">
              <p className="text-sm font-semibold text-red-400">
                Falha na requisição
              </p>
              <p className="mt-1 font-mono text-xs text-red-300">{pixError}</p>
            </div>
          )}
        </SectionCard>

        {/* ── Seção 2 + 3: Outbox Health + Recover ──────────────────────── */}
        <div className="flex flex-col gap-6">
          {/* Card de Saúde do Outbox */}
          <SectionCard
            title="Estado do Outbox"
            subtitle={`Polling a cada ${POLLING_INTERVAL_MS / 1000}s via /actuator/prometheus`}
            accent={outboxCardBorder}
          >
            {metricsLoading ? (
              <div className="flex items-center justify-center py-6">
                <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
                <span className="ml-3 text-sm text-gray-400">
                  Conectando ao Actuator...
                </span>
              </div>
            ) : metrics?.error ? (
              <div className="rounded-lg border border-yellow-700 bg-yellow-950 p-4 text-sm text-yellow-400">
                transaction-service offline ou Actuator inacessível
              </div>
            ) : (
              <>
                {hasCriticalDeadLetters && (
                  <div className="mb-4 animate-pulse rounded-lg border border-red-600 bg-red-950 px-4 py-2 text-sm font-semibold text-red-400">
                    ALERTA CRÍTICO — {metrics!.deadLetter} evento(s) em
                    DEAD_LETTER
                  </div>
                )}
                <div className="grid grid-cols-3 gap-3">
                  <StatusBadge
                    label="Pending"
                    value={metrics!.pending}
                    color="bg-yellow-950 text-yellow-300"
                  />
                  <StatusBadge
                    label="Sent"
                    value={metrics!.sent}
                    color="bg-green-950 text-green-300"
                  />
                  <StatusBadge
                    label="Dead Letter"
                    value={metrics!.deadLetter}
                    color={
                      metrics!.deadLetter > 0
                        ? "bg-red-950 text-red-300"
                        : "bg-gray-800 text-gray-400"
                    }
                  />
                </div>
              </>
            )}
          </SectionCard>

          {/* Card de Recovery Administrativo */}
          <SectionCard
            title="Recuperação de Fila"
            subtitle="Recoloca todos os eventos DEAD_LETTER em PENDING para reprocessamento pelo relay."
            accent={hasCriticalDeadLetters ? "border-red-700" : "border-gray-800"}
          >
            <button
              onClick={handleRecover}
              disabled={recoverStatus === "loading"}
              className={`w-full rounded-lg px-4 py-3 text-sm font-bold tracking-wide transition disabled:cursor-not-allowed disabled:opacity-50 ${
                hasCriticalDeadLetters
                  ? "bg-red-600 text-white hover:bg-red-500"
                  : "bg-gray-700 text-gray-200 hover:bg-gray-600"
              }`}
            >
              {recoverStatus === "loading"
                ? "Executando recovery..."
                : "Recover Queue"}
            </button>

            {/* Toast de sucesso */}
            {recoverStatus === "success" && recoverResult && (
              <div className="mt-4 rounded-lg border border-green-700 bg-green-950 p-4">
                <p className="text-sm font-semibold text-green-400">
                  Recovery executado
                </p>
                <p className="mt-1 text-xs text-green-300">
                  {recoverResult.message}
                </p>
              </div>
            )}

            {/* Toast de erro */}
            {recoverStatus === "error" && (
              <div className="mt-4 rounded-lg border border-red-700 bg-red-950 p-4">
                <p className="text-sm font-semibold text-red-400">
                  Falha no recovery
                </p>
                <p className="mt-1 font-mono text-xs text-red-300">
                  {recoverError}
                </p>
              </div>
            )}

            <p className="mt-3 text-xs text-gray-500">
              Operação idempotente — segura para executar múltiplas vezes. O
              relay processará os eventos resgatados em até 5 segundos.
            </p>
          </SectionCard>
        </div>
      </div>

      {/* Footer */}
      <footer className="mt-10 border-t border-gray-800 pt-6 text-center text-xs text-gray-600">
        Fintech MVP &mdash; transaction-service{" "}
        <span className="font-mono">:8090</span> &mdash; Grafana{" "}
        <a
          href="http://localhost:3000"
          target="_blank"
          rel="noopener noreferrer"
          className="text-indigo-500 hover:underline"
        >
          :3000
        </a>{" "}
        &mdash; Kafka UI{" "}
        <a
          href="http://localhost:8085"
          target="_blank"
          rel="noopener noreferrer"
          className="text-indigo-500 hover:underline"
        >
          :8085
        </a>
      </footer>
    </div>
  );
}
