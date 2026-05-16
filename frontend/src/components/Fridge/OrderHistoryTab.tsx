"use client";

import { useState } from "react";
import { useOrders } from "../../hooks/useOrders";
import { Order } from "../../lib/types";
import {Hint} from "@/src/components/Hint";

export function OrderHistoryTab() {
    const { orders } = useOrders();

    const reversed = orders.slice().reverse();
    const [selectedId, setSelectedId] = useState<string | null>(null);

    const selectedOrder =
        reversed.find((o) => o.orderId === selectedId) || reversed[0] || null;

    function shortId(orderId: string): string {
        const tail = orderId.slice(-5).toUpperCase();
        return `ORD-${tail}`;
    }

    function dayMonth(iso: string): string {
        try {
            const d = new Date(iso);
            const dd = String(d.getDate()).padStart(2, "0");
            const mm = String(d.getMonth() + 1).padStart(2, "0");
            return `${dd}.${mm}.`;
        } catch {
            return "--.--.";
        }
    }

    return (
        <div>
            <Hint>
                Only successful orders appear here. Failed orders are rejected before they reach the history.
            </Hint>
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-3">
                <ReceiptCard order={selectedOrder} />

                <div className="neu-inset p-3">
                    {reversed.length === 0 ? (
                        <p className="text-sm text-ink-soft p-3">No orders yet.</p>
                    ) : (
                        <ul className="space-y-1">
                            {reversed.slice(0, 12).map((o) => {
                                const active = (selectedId ?? reversed[0].orderId) === o.orderId;
                                return (
                                    <li key={o.orderId}>
                                        <button
                                            onClick={() => setSelectedId(o.orderId)}
                                            className={`w-full grid grid-cols-3 items-center px-4 py-2 rounded-xl text-sm transition-all ${
                                                active ? "bg-bg shadow-[var(--shadow-neu-out-sm)] border border-ink/15" : "hover:bg-bg/60"
                                            }`}
                                        >
                                            <span className="text-ink font-semibold text-left">{shortId(o.orderId)}</span>
                                            <span className="text-ink-soft text-center">{dayMonth(o.timestamp)}</span>
                                            <span className="text-ink text-right">€{o.totalPrice.toFixed(2)}</span>
                                        </button>
                                    </li>
                                );
                            })}
                        </ul>
                    )}
                </div>
            </div>
        </div>
    );
}

function ReceiptCard({ order }: { order: Order | null }) {
    if (!order) {
        return (
            <div className="receipt-paper p-6 text-center text-ink-soft">
                <p className="text-sm">Select an order to view the receipt.</p>
            </div>
        );
    }

    return (
        <div className="receipt-paper p-6 font-(family-name:--font-digit) text-ink">
            <h3 className="text-center text-xl font-bold tracking-widest mb-4">RECEIPT</h3>
            <ul className="space-y-1 text-sm">
                {order.items.map((item) => {
                    const itemTotal = item.unitPrice * item.quantity;
                    return (
                        <li key={item.productId} className="flex justify-between">
              <span>
                {item.quantity}X {item.productName.toUpperCase()}
              </span>
                            <span>€ {itemTotal.toFixed(2)}</span>
                        </li>
                    );
                })}
            </ul>
            <div className="border-t border-dashed border-ink/40 my-4" />
            <div className="flex justify-between text-base font-bold">
                <span>TOTAL</span>
                <span>€ {order.totalPrice.toFixed(2)}</span>
            </div>

            <style jsx>{`
                .receipt-paper {
                    background: #ecedee;
                    border-radius: 14px;
                    box-shadow: inset 4px 4px 10px rgba(31, 44, 76, 0.1),
                    inset -4px -4px 10px rgba(255, 255, 255, 0.9);
                    position: relative;
                }
            `}</style>
        </div>
    );
}
