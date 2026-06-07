/** @type {import('next').NextConfig} */
const nextConfig = {
  // Gera bundle standalone para o Dockerfile multi-stage (sem node_modules completo)
  output: "standalone",

  // URL do transaction-service resolvida em runtime:
  //   - Dev local (docker-compose): http://localhost:8090
  //   - K8s (container): http://transaction-service-svc.fintech.svc.cluster.local:8080
  //     injetada via env TRANSACTION_SERVICE_URL no Deployment
  async rewrites() {
    const backendUrl =
      process.env.TRANSACTION_SERVICE_URL ?? "http://localhost:8090";
    return [
      {
        source: "/api/backend/:path*",
        destination: `${backendUrl}/:path*`,
      },
    ];
  },
};

export default nextConfig;
