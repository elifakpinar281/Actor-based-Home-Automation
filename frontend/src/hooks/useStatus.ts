"use client";

import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { Status } from "../lib/types";

export function useStatus(intervalMs: number = 2000) {
  const [status, setStatus] = useState<Status | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let alive = true;

    async function load() {
      try {
        const data = await api.getStatus();
        if (alive) {
          setStatus(data);
          setError(null);
        }
      } catch (e) {
        if (alive) setError((e as Error).message);
      }
    }

    load();
    const id = setInterval(load, intervalMs);
    return () => {
      alive = false;
      clearInterval(id);
    };
  }, [intervalMs]);

  return { status, error };
}
