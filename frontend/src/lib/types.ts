export type Weather = "SUNNY" | "RAINY" | "CLOUDY" | "SNOWY";
export type SimulationMode = "INTERNAL" | "EXTERNAL_MQTT" | "FIXED" | "DISABLED";

export interface Status {
  temperature: number;
  weather: Weather;
  simulationMode: SimulationMode;
  acPoweredOn: boolean;
  acCooling: boolean;
  blindsClosed: boolean;
  moviePlaying: boolean;
  currentMovie: string | null;
}

export interface Product {
  id: string;
  name: string;
  weight: number;
  price: number;
  quantity: number;
}

export interface Capacity {
  currentItems: number;
  maxItems: number;
  currentWeight: number;
  maxWeight: number;
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface Order {
  orderId: string;
  timestamp: string;
  status: "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";
  totalPrice: number;
  items: OrderItem[];
}
