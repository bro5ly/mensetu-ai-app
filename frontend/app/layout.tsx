import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Mensetu AI - 面接練習アプリ",
  description: "AIコーチと音声で面接練習ができるアプリ",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ja">
      <body className="font-sans antialiased">{children}</body>
    </html>
  );
}
