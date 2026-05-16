"use client";

import { useState } from "react";
import { api } from "../../lib/api";
import { useProducts } from "../../hooks/useProducts";
import { useCapacity } from "../../hooks/useCapacity";
import { useToast } from "../../hooks/useToast";
import { Hint } from "../Hint";
import { Product } from "../../lib/types";
import { ProductIcon } from "./ProductIcon";

export function CurrentProducts() {
    const { products, reload: reloadProducts, reloadUntilChanged } = useProducts();
    const { capacity, reload: reloadCapacity } = useCapacity();
    const { push } = useToast();

    const [optimistic, setOptimistic] = useState<Record<string, number>>({});

    function effectiveQty(p: Product): number {
        const override = optimistic[p.id];
        if (override === undefined || override >= p.quantity) {
            return p.quantity;
        }
        return override;
    }

    async function handleConsume(p: Product) {
        const current = effectiveQty(p);
        if (current <= 0) return;

        setOptimistic((prev) => ({ ...prev, [p.id]: current - 1 }));

        try {
            await api.consumeProduct(p.id, 1);
            reloadCapacity();

            if (current - 1 <= 0) {
                push(`${p.name} consumed — re-ordering…`, "success");
                await reloadUntilChanged();
            } else {
                push(`${p.name} consumed`, "success");
                reloadProducts();
            }

            setOptimistic((prev) => {
                const next = { ...prev };
                delete next[p.id];
                return next;
            });
        } catch (e) {
            setOptimistic((prev) => {
                const next = { ...prev };
                delete next[p.id];
                return next;
            });
            push(`Consume failed: ${(e as Error).message}`, "error");
        }
    }

    const visible = products
        .map((p) => ({ ...p, quantity: effectiveQty(p) }))
        .filter((p) => p.quantity > 0);

    const usedPct = capacity ? Math.min(100, (capacity.currentWeight / capacity.maxWeight) * 100) : 0;
    const freeKg = capacity ? Math.max(0, capacity.maxWeight - capacity.currentWeight) : 0;

    return (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_280px] gap-6">
            <div>
                <Hint>
                    Click <strong>Consume</strong> to use one unit of a product. When a product runs out, the fridge automatically re-orders the initial quantity.
                </Hint>
                <div className="space-y-3 mt-3">
                    {visible.length === 0 && (
                        <p className="text-sm text-ink-soft">Fridge is empty.</p>
                    )}
                    {visible.map((p) => (
                        <ProductRow key={p.id} product={p} onConsume={() => handleConsume(p)} />
                    ))}
                </div>
            </div>

            <div className="space-y-4">
                <div className="grid grid-cols-2 gap-3">
                    <div className="neu-card p-4">
                        <p className="topbar-label">Items</p>
                        <p className="font-(family-name:--font-digit) text-2xl text-ink mt-1">
                            {capacity ? `${capacity.currentItems} / ${capacity.maxItems}` : "—"}
                        </p>
                    </div>
                    <div className="neu-card p-4">
                        <p className="topbar-label">Weight</p>
                        <p className="font-(family-name:--font-digit) text-2xl text-ink mt-1">
                            {capacity ? `${capacity.currentWeight.toFixed(1)} kg` : "—"}
                        </p>
                    </div>
                </div>

                <div className="neu-inset p-4">
                    <p className="topbar-label mb-3">Capacity</p>
                    <div className="h-2 rounded-full bg-ink/10 overflow-hidden">
                        <div className="h-full bg-accent" style={{ width: `${usedPct}%` }} />
                    </div>
                    <div className="flex justify-between text-xs text-ink-soft mt-2">
                        <span>{capacity ? `${capacity.currentWeight.toFixed(1)} kg used` : "—"}</span>
                        <span>{capacity ? `${freeKg.toFixed(1)} kg free` : "—"}</span>
                    </div>
                </div>
            </div>
        </div>
    );
}

function ProductRow({ product, onConsume }: { product: Product; onConsume: () => void }) {
    return (
        <div className="neu-card px-4 py-3 flex items-center gap-4">
            <div className="w-9 h-9 neu-inset flex items-center justify-center shrink-0">
                <ProductIcon name={product.name} />
            </div>
            <div className="flex flex-col min-w-[110px]">
                <span className="text-sm font-semibold text-ink leading-tight">{product.name}</span>
                <span className="text-[10px] tracking-widest text-ink-soft uppercase mt-0.5">
                    {unitFor(product.name)}
                </span>
            </div>
            <span className="text-sm text-ink ml-2">×{product.quantity}</span>
            <span className="text-sm text-ink-soft ml-auto">{(product.weight * product.quantity).toFixed(2)} kg</span>
            <span className="text-sm font-semibold text-ink w-16 text-right">€{(product.price * product.quantity).toFixed(2)}</span>
            <button
                onClick={onConsume}
                className="pill border border-danger text-danger ml-2 hover:bg-danger hover:text-white transition-colors"
            >
                Consume
            </button>
        </div>
    );
}

function unitFor(name: string): string {
    const n = name.toLowerCase();
    if (n.includes("milk") || n.includes("water")) return "L";
    if (n.includes("egg")) return "pck";
    return "pcs";
}