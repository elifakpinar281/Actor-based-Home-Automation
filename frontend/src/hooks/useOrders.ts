"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api";
import { Order } from "../lib/types";

export function useOrders(intervalMs: number = 3000) {
    const [orders, setOrders] = useState<Order[]>([]);

    const reload = useCallback(async () => {
        try {
            const data = await api.getOrderHistory();
            setOrders(data);
        } catch {
            // ignore
        }
    }, []);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        reload();
        const id = setInterval(reload, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, reload]);

    return { orders, reload };
}