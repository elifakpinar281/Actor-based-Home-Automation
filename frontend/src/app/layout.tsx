import type { Metadata } from "next";
import "./globals.css";
import {ToastProvider} from "@/src/hooks/useToast";
import {Toaster} from "@/src/components/Toaster";

export const metadata: Metadata = {
    title: "Smart Home Automation",
    description: "Actor-based home automation system",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
    return (
        <html lang="en">
        <body>
        <ToastProvider>
            {children}
            <Toaster />
        </ToastProvider>
        </body>
        </html>
    );
}
