"use client";

import { useState } from "react";
import { api } from "../../lib/api";
import { useProducts } from "../../hooks/useProducts";
import { useToast } from "../../hooks/useToast";
import { Product } from "../../lib/types";
import { ProductIcon } from "./ProductIcon";
import {Hint} from "@/src/components/Hint";
import {useCapacity} from "@/src/hooks/useCapacity";
import {useOrders} from "@/src/hooks/useOrders";

export function OrderTab() {
    const { products, reload: reloadProducts } = useProducts();
    const { reload: reloadCapacity } = useCapacity();
    const { reload: reloadOrders } = useOrders();
    const { push } = useToast();

    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [cart, setCart] = useState<Record<string, number>>({});
    const [submitting, setSubmitting] = useState<boolean>(false);

    function changeQty(productId: string, delta: number) {
        setCart((prev) => {
            const next = { ...prev };
            const nextQty = (next[productId] || 0) + delta;
            if (nextQty <= 0) {
                delete next[productId];
            } else {
                next[productId] = nextQty;
            }
            return next;
        });
    }

    async function submitOrder() {
        if (Object.keys(cart).length === 0) return;
        setSubmitting(true);
        const knownIds = new Set(
            (await api.getOrderHistory().catch(() => [])).map((o: { orderId: string }) => o.orderId)
        );
        try {
            await api.orderProducts(cart);
            push("Order placed successfully!", "success");
            setCart({});
            reloadProducts();
            reloadCapacity();
            reloadOrders();
        } catch (e) {
            const raw = (e as Error).message;
            if (raw.toLowerCase().includes("failed to fetch")) {
                await waitForNewOrder(knownIds, 10_000);
                push("Order placed successfully!", "success");
                setCart({});
                reloadProducts();
                reloadCapacity();
                reloadOrders();
            } else {
                push(extractBackendMessage(raw), "error");
            }
        } finally {
            setSubmitting(false);
        }
    }

    async function waitForNewOrder(knownIds: Set<string>, timeoutMs: number): Promise<boolean> {
        const deadline = Date.now() + timeoutMs;
        while (Date.now() < deadline) {
            const current = await api.getOrderHistory().catch(() => []);
            if (current.some((o: { orderId: string }) => !knownIds.has(o.orderId))) return true;
            await new Promise(r => setTimeout(r, 800));
        }
        return false;
    }

    let subtotal = 0;
    const cartItems: { product: Product; qty: number }[] = [];
    for (const product of products) {
        const qty = cart[product.id];
        if (qty && qty > 0) {
            subtotal += product.price * qty;
            cartItems.push({ product, qty });
        }
    }

    return (
        <div className="grid grid-cols-1 lg:grid-cols-[1fr_auto_1fr] gap-6 items-start">
            <div className="neu-inset p-4">
                <p className="topbar-label mb-2">Catalog</p>
                <Hint>
                    Tap a product to select it, then use + / − to choose how many to order.
                </Hint>
                <ul className="space-y-1 max-h-80 overflow-auto scroll-thin pr-1 mt-3">
                    {products.map((p) => {
                        const active = selectedId === p.id;
                        return (
                            <li key={p.id}>
                                <button
                                    onClick={() => setSelectedId(p.id)}
                                    className={`w-full flex items-center gap-3 px-3 py-2 rounded-xl transition-all ${
                                        active
                                            ? "bg-bg shadow-[var(--shadow-neu-out-sm)] border border-ink/15"
                                            : "hover:bg-bg/60"
                                    }`}
                                >
                                    <ProductIcon name={p.name} />
                                    <div className="flex flex-col text-left flex-1">
                                        <span className="text-sm font-semibold text-ink">{p.name}</span>
                                        <span className="text-[10px] tracking-widest text-ink-soft uppercase">
                                            {p.weight.toFixed(2)} kg
                                        </span>
                                    </div>
                                    <span className="text-sm text-ink">€{p.price.toFixed(2)}</span>
                                </button>
                            </li>
                        );
                    })}
                </ul>
            </div>

            <div className="flex lg:flex-col items-center justify-center gap-3 py-4">
                <button
                    onClick={() => selectedId && changeQty(selectedId, 1)}
                    disabled={!selectedId || submitting}
                    className="neu-btn w-12 h-12 text-2xl"
                    aria-label="Add"
                >
                    +
                </button>
                <button
                    onClick={() => selectedId && changeQty(selectedId, -1)}
                    disabled={!selectedId || submitting}
                    className="neu-btn w-12 h-12 text-2xl"
                    aria-label="Remove"
                >
                    −
                </button>
            </div>

            <div className="neu-inset p-5 flex flex-col">
                <p className="section-title text-sm mb-2">Shopping Cart</p>
                <Hint>
                    Orders are rejected if the fridge does not have enough space or weight capacity.
                </Hint>

                {cartItems.length === 0 ? (
                    <p className="text-sm text-ink-soft mt-4 mb-4">Cart is empty.</p>
                ) : (
                    <ul className="space-y-2 mt-4 mb-6">
                        {cartItems.map(({ product, qty }) => (
                            <li key={product.id} className="flex justify-between text-sm text-ink">
                                <span>
                                    {qty} × {product.name}
                                </span>
                                <span>€ {(product.price * qty).toFixed(2)}</span>
                            </li>
                        ))}
                    </ul>
                )}

                <div className="mt-auto">
                    <div className="flex justify-between items-baseline mb-4">
                        <span className="topbar-label">Subtotal</span>
                        <span className="font-(family-name:--font-digit) text-3xl text-ink">
                            €{subtotal.toFixed(2)}
                        </span>
                    </div>

                    <button
                        onClick={submitOrder}
                        disabled={cartItems.length === 0 || submitting}
                        className="neu-btn w-full h-11 text-xs tracking-widest uppercase relative overflow-hidden"
                    >
                        {submitting ? (
                            <span className="flex items-center justify-center gap-2">
                                <span className="flex gap-1">
                                    <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:0ms]" />
                                    <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:150ms]" />
                                    <span className="w-1.5 h-1.5 rounded-full bg-current animate-bounce [animation-delay:300ms]" />
                                </span>
                                Processing order
                            </span>
                        ) : (
                            "Submit Order →"
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}


function extractBackendMessage(raw: string): string {
    const jsonStart = raw.indexOf("{");
    if (jsonStart === -1) return raw;
    try {
        const parsed = JSON.parse(raw.slice(jsonStart));
        return parsed.message ?? raw;
    } catch {
        return raw;
    }
}