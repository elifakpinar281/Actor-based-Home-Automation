"use client";

import { useState } from "react";
import { CurrentProducts } from "./CurrentProducts";
import { OrderTab } from "./OrderTab";
import { OrderHistoryTab } from "./OrderHistoryTab";

type Tab = "current" | "order" | "history";

export function FridgeCard() {
  const [tab, setTab] = useState<Tab>("current");

  return (
    <div className="neu-card p-6">
      <h2 className="section-title mb-5">Fridge Management</h2>

      <div className="inline-flex gap-2 mb-5">
        <TabBtn active={tab === "current"} onClick={() => setTab("current")}>
          Current Products
        </TabBtn>
        <TabBtn active={tab === "order"} onClick={() => setTab("order")}>
          Order
        </TabBtn>
        <TabBtn active={tab === "history"} onClick={() => setTab("history")}>
          Order History
        </TabBtn>
      </div>

      <div>
        {tab === "current" && <CurrentProducts />}
        {tab === "order" && <OrderTab />}
        {tab === "history" && <OrderHistoryTab />}
      </div>
    </div>
  );
}

function TabBtn({ active, onClick, children }: { active: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      onClick={onClick}
      className={`px-4 h-8 rounded-full text-[10px] tracking-[0.18em] uppercase font-semibold transition-all ${
        active ? "bg-bg shadow-[var(--shadow-neu-out-sm)] border border-ink/15 text-ink" : "text-ink-soft hover:text-ink"
      }`}
    >
      {children}
    </button>
  );
}
