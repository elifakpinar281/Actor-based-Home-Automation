"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "../lib/api";
import { Product } from "../lib/types";

export function useProducts(intervalMs: number = 3000) {
    const [products, setProducts] = useState<Product[]>([]);
    const [error, setError] = useState<string | null>(null);

    const reload = useCallback(async () => {
        try {
            const data = await api.getProducts();
            setProducts(data);
            setError(null);
        } catch (e) {
            setError((e as Error).message);
        }
    }, []);

    useEffect(() => {
        queueMicrotask(reload);
        const id = setInterval(reload, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, reload]);

    return { products, error, reload };
}