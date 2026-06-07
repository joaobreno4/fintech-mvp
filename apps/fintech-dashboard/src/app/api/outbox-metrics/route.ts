import { NextResponse } from "next/server";

// Extrai um valor numérico de uma linha do formato Prometheus text exposition:
// outbox_pending_events{...} 3.0
function extractGauge(prometheusText: string, metricName: string): number {
  const regex = new RegExp(`^${metricName}\\{[^}]*\\}\\s+([\\d.]+)`, "m");
  const match = prometheusText.match(regex);
  return match ? parseFloat(match[1]) : 0;
}

export async function GET() {
  // Dev local: http://localhost:8090
  // K8s: http://transaction-service-svc.fintech.svc.cluster.local:8080
  const backendUrl =
    process.env.TRANSACTION_SERVICE_URL ?? "http://localhost:8090";

  try {
    const res = await fetch(`${backendUrl}/actuator/prometheus`, {
      // Sem cache — sempre dados frescos para o polling de 5s
      cache: "no-store",
    });

    if (!res.ok) {
      return NextResponse.json(
        { error: "Actuator indisponível", status: res.status },
        { status: 502 }
      );
    }

    const text = await res.text();

    const metrics = {
      pending: extractGauge(text, "outbox_pending_events"),
      sent: extractGauge(text, "outbox_sent_events"),
      deadLetter: extractGauge(text, "outbox_dead_letter_events"),
    };

    return NextResponse.json(metrics);
  } catch {
    // transaction-service offline ou Actuator não acessível
    return NextResponse.json(
      { error: "Não foi possível conectar ao transaction-service" },
      { status: 503 }
    );
  }
}
