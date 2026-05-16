"use client";

import { useState } from "react";
import { api } from "../../lib/api";
import { useProducts } from "../../hooks/useProducts";
import { useToast } from "../../hooks/useToast";
import { Toaster } from "../Toaster";
import { Product } from "../../lib/types";
import { ProductIcon } from "./ProductIcon";

export function OrderTab() {
    const { products, reload } = useProducts();
    const { toasts, push, dismiss } = useToast();
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
        try {
            await api.orderProducts(cart);
            push("Order placed successfully!", "success");
            setCart({});
            reload();
        } catch (e) {
            const msg = (e as Error).message;
            let display = msg;
            const jsonStart = msg.indexOf("{");
            if (jsonStart !== -1) {
                try {
                    const parsed = JSON.parse(msg.slice(jsonStart));
                    display = parsed.message ?? display;
                } catch {
                    // leave as-is
                }
            }
            push(display, "error");
        } finally {
            setSubmitting(false);
        }
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
        <>
            <Toaster toasts={toasts} dismiss={dismiss} />
            <div className="grid grid-cols-1 lg:grid-cols-[1fr_auto_1fr] gap-6 items-start">
                <div className="neu-inset p-4">
                    <p className="topbar-label mb-3">Catalog</p>
                    <ul className="space-y-1 max-h-80 overflow-auto scroll-thin pr-1">
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
                        disabled={!selectedId}
                        className="neu-btn w-12 h-12 text-2xl"
                        aria-label="Add"
                    >
                        +
                    </button>
                    <button
                        onClick={() => selectedId && changeQty(selectedId, -1)}
                        disabled={!selectedId}
                        className="neu-btn w-12 h-12 text-2xl"
                        aria-label="Remove"
                    >
                        −
                    </button>
                </div>

                <div className="neu-inset p-5 flex flex-col">
                    <p className="section-title text-sm mb-4">Shopping Cart</p>

                    {cartItems.length === 0 ? (
                        <p className="text-sm text-ink-soft mb-4">Cart is empty.</p>
                    ) : (
                        <ul className="space-y-2 mb-6">
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
                            className="neu-btn w-full h-11 text-xs tracking-widest uppercase"
                        >
                            {submitting ? "Submitting…" : "Submit Order →"}
                        </button>
                    </div>
                </div>
            </div>
        </>
    );
}