"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "../lib/api";
import { Product } from "../lib/types";

export function useProducts(intervalMs: number = 3000) {
    const [products, setProducts] = useState<Product[]>([]);
    const [error, setError] = useState<string | null>(null);
    const productsRef = useRef<Product[]>([]);

    const reload = useCallback(async () => {
        try {
            const data = await api.getProducts();
            setProducts(data);
            productsRef.current = data;
            setError(null);
        } catch (e) {
            setError((e as Error).message);
        }
    }, []);

    const reloadUntilChanged = useCallback(async (
        attempts: number = 8,
        delayMs: number = 800
    ) => {
        const snapshot = productsRef.current.map(p => `${p.id}:${p.quantity}`).join(",");

        for (let i = 0; i < attempts; i++) {
            await new Promise(resolve => setTimeout(resolve, delayMs));
            try {
                const data = await api.getProducts();
                const current = data.map(p => `${p.id}:${p.quantity}`).join(",");
                setProducts(data);
                productsRef.current = data;
                setError(null);
                if (current !== snapshot) {
                    return;
                }
            } catch (e) {
                setError((e as Error).message);
            }
        }
    }, []);

    useEffect(() => {
        queueMicrotask(reload);
        const id = setInterval(reload, intervalMs);
        return () => clearInterval(id);
    }, [intervalMs, reload]);

    return { products, error, reload, reloadUntilChanged };
}