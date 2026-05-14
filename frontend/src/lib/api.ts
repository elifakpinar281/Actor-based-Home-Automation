import { Capacity, Order, Product, SimulationMode, Status, Weather } from "./types";

const BASE = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8084";

async function jsonOrError<T>(res: Response): Promise<T> {
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`HTTP ${res.status}: ${text}`);
  }
  return res.json();
}

export const api = {
  async getStatus(): Promise<Status> {
    const res = await fetch(`${BASE}/status`);
    return jsonOrError<Status>(res);
  },

  async setTemperature(value: number): Promise<void> {
    const res = await fetch(`${BASE}/environment/temperature?value=${value}`, { method: "POST" });
    await jsonOrError(res);
  },

  async setWeather(condition: Weather): Promise<void> {
    const res = await fetch(`${BASE}/environment/weather?condition=${condition}`, { method: "POST" });
    await jsonOrError(res);
  },

  async setMode(mode: SimulationMode): Promise<void> {
    const res = await fetch(`${BASE}/environment/source?mode=${mode}`, { method: "POST" });
    await jsonOrError(res);
  },

  async setAcPower(on: boolean): Promise<void> {
    const res = await fetch(`${BASE}/ac/power?on=${on}`, { method: "POST" });
    await jsonOrError(res);
  },

  async getProducts(): Promise<Product[]> {
    const res = await fetch(`${BASE}/fridge/products`);
    const data = await jsonOrError<{ products: Product[] }>(res);
    return data.products;
  },

  async getCapacity(): Promise<Capacity> {
    const res = await fetch(`${BASE}/fridge/capacity`);
    return jsonOrError<Capacity>(res);
  },

  async consumeProduct(productId: string, quantity: number): Promise<void> {
    const res = await fetch(`${BASE}/fridge/consume?productId=${productId}&quantity=${quantity}`, { method: "POST" });
    await jsonOrError(res);
  },

  async orderProducts(items: Record<string, number>): Promise<void> {
    const res = await fetch(`${BASE}/fridge/order`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items }),
    });
    await jsonOrError(res);
  },

  async getOrderHistory(): Promise<Order[]> {
    const res = await fetch(`${BASE}/fridge/history`);
    const data = await jsonOrError<{ orders: Order[] }>(res);
    return data.orders;
  },

  async playMovie(name: string): Promise<void> {
    const res = await fetch(`${BASE}/media-station/play?movieName=${encodeURIComponent(name)}`, { method: "POST" });
    await jsonOrError(res);
  },

  async stopMovie(): Promise<void> {
    const res = await fetch(`${BASE}/media-station/stop`, { method: "POST" });
    await jsonOrError(res);
  },
};
