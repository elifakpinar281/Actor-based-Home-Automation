"use client";

import { useToast } from "../hooks/useToast";

export function Toaster() {
    const { toasts, dismiss } = useToast();
    if (toasts.length === 0) return null;

    return (
        <div className="fixed top-6 right-6 z-50 flex flex-col gap-3 pointer-events-none">
            {toasts.map((t) => {
                const accent =
                    t.type === "error"
                        ? "border-danger/40"
                        : t.type === "success"
                            ? "border-accent/40"
                            : "border-ink/15";
                const dot =
                    t.type === "error"
                        ? "bg-danger"
                        : t.type === "success"
                            ? "bg-accent"
                            : "bg-ink-soft";

                return (
                    <div
                        key={t.id}
                        className={`neu-card pointer-events-auto min-w-[260px] max-w-sm pl-4 pr-3 py-3 flex items-start gap-3 ${accent}`}
                        role="status"
                    >
                        <span className={`mt-1.5 inline-block w-2 h-2 rounded-full shrink-0 ${dot}`} />
                        <p className="text-sm text-ink leading-snug flex-1">{t.message}</p>
                        <button
                            onClick={() => dismiss(t.id)}
                            aria-label="Dismiss"
                            className="text-ink-soft hover:text-ink text-lg leading-none px-1"
                        >
                            ×
                        </button>
                    </div>
                );
            })}
        </div>
    );
}
