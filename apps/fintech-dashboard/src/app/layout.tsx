import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Fintech MVP — Dashboard",
  description: "Painel operacional e administrativo do ecossistema Fintech MVP",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="pt-BR" className="dark">
      <body className="min-h-screen bg-gray-950 text-gray-100 antialiased">
        {children}
      </body>
    </html>
  );
}
