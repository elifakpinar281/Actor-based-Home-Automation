"use client";

import { ReactNode } from "react";

export function Hint({ children }: { children: ReactNode }) {
    return (
        <p className="flex items-start text-[11px] leading-snug text-ink-soft/80 mt-1.5">
            <span>{children}</span>
        </p>
    );
}

