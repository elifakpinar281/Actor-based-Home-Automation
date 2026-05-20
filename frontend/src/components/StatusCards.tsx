"use client";

import { Status } from "../lib/types";

function weatherLabel(w: string): string {
    if (w === "SUNNY") return "Sunny";
    if (w === "RAINY") return "Rainy";
    if (w === "CLOUDY") return "Cloudy";
    if (w === "SNOWY") return "Snowy";
    if (w === "STORMY") return "Stormy";
    return w;
}

function acLabel(s: Status): string {
    if (!s.acPoweredOn) return "Off";
    return s.acCooling ? "Cooling" : "Idle";
}

interface Props {
    status: Status | null;
}

export function StatusCards({ status }: Props) {
    const temp = status ? status.temperature.toFixed(1) : "--";
    const weather = status ? weatherLabel(status.weather) : "--";
    const ac = status ? acLabel(status) : "--";
    const blinds = status ? (status.blindsClosed ? "Closed" : "Open") : "--";

    return (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-5">
            <Card label="Temperature" icon={<TempIcon />}>
                <span className="font-(family-name:--font-digit) text-3xl text-ink leading-none">{temp}°</span>
            </Card>

            <Card label="Weather" icon={<SunIcon />}>
                <span className="text-xl font-semibold text-ink">{weather}</span>
            </Card>

            <Card label="Air Conditioner" icon={<AcIcon />}>
                <span className="text-xl font-semibold text-ink">{ac}</span>
            </Card>

            <Card label="Blinds">
                <span className="text-xl font-semibold text-ink">{blinds}</span>
            </Card>
        </div>
    );
}

function Card({ label, icon, children }: { label: string; icon?: React.ReactNode; children: React.ReactNode }) {
    return (
        <div className="neu-card px-5 py-4">
            <div className="flex items-center justify-between mb-2">
                <span className="topbar-label">{label}</span>
                {icon}
            </div>
            <div>{children}</div>
        </div>
    );
}

function TempIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#1f2c4c" strokeWidth="2">
            <path d="M14 14.76V3a2 2 0 0 0-4 0v11.76a4 4 0 1 0 4 0z" />
        </svg>
    );
}
function SunIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#1f2c4c" strokeWidth="2">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
    );
}
function AcIcon() {
    return (
        <svg width="16" height="16" viewBox="0 0 24 24" fill="#3a55a3">
            <rect x="3" y="6" width="18" height="8" rx="2" />
            <path d="M6 16v2M12 16v2M18 16v2" stroke="#3a55a3" strokeWidth="2" />
        </svg>
    );
}