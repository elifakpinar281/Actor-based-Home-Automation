"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api";
import { Capacity } from "../lib/types";

export function useCapacity(intervalMs: number = 3000) {
    const [capacity, setCapacity] = useState<Capacity | null>(null);

    const reload = useCallback(async () => {
        try {
            const data = await api.getCapacity();
            setCapacity(data);
        } catch {
            // ignore - try again next interval
        }
    }, []);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        reload();
        const id = setInterval(reload, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, reload]);

    return { capacity, reload };
}