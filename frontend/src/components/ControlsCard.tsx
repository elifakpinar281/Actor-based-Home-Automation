"use client";

import { useEffect, useState } from "react";
import { api } from "../lib/api";
import { SimulationMode, Status, Weather } from "../lib/types";

interface Props {
    status: Status | null;
}

const TEMP_MIN = -5;
const TEMP_MAX = 35;
const TEMP_STEP = 0.5;

export function ControlsCard({ status }: Props) {
    const [target, setTarget] = useState<number>(20);
    const [editing, setEditing] = useState<boolean>(false);

    useEffect(() => {
        if (!editing && status?.temperature != null) {
            // eslint-disable-next-line react-hooks/set-state-in-effect
            setTarget(status.temperature);
        }
    }, [status?.temperature, editing]);

    async function pushTemp(value: number) {
        const clamped = Math.max(TEMP_MIN, Math.min(TEMP_MAX, value));
        const rounded = Math.round(clamped * 2) / 2;
        setTarget(rounded);
        try {
            await api.setTemperature(rounded);
        } catch (e) {
            console.error("Failed to set temperature", e);
        }
    }

    function onSliderChange(e: React.ChangeEvent<HTMLInputElement>) {
        setEditing(true);
        const v = parseFloat(e.target.value);
        setTarget(v);
    }

    async function onSliderRelease() {
        await pushTemp(target);
        setTimeout(() => setEditing(false), 800);
    }

    async function chooseWeather(w: Weather) {
        setLocalWeather(w);
        try {
            await api.setWeather(w);
        } catch (e) {
            console.error("Failed to set weather", e);
        }
    }

    async function chooseMode(mode: SimulationMode) {
        setLocalMode(mode);
        try {
            await api.setMode(mode);
        } catch (e) {
            console.error("Failed to set mode", e);
        }
    }

    const [localWeather, setLocalWeather] = useState<Weather | null>(null);
    const [localMode, setLocalMode] = useState<SimulationMode | null>(null);

    useEffect(() => {
        if (localWeather && status?.weather === localWeather) setLocalWeather(null);
    }, [status?.weather, localWeather]);
    useEffect(() => {
        if (localMode && status?.simulationMode === localMode) setLocalMode(null);
    }, [status?.simulationMode, localMode]);

    const currentMode = localMode ?? status?.simulationMode;
    const currentWeather = localWeather ?? status?.weather;

    const isFixed = currentMode === "FIXED";

    return (
        <div className="neu-card p-6">
            <h2 className="section-title mb-5">Controls</h2>

            <div className="flex items-baseline justify-between mb-3">
                <p className="topbar-label">Temperature</p>
                {!isFixed && (
                    <span className="text-[10px] tracking-wider text-ink-soft/70 italic normal-case">
                        Switch to <span className="font-semibold">Fixed</span> to set values
                    </span>
                )}
            </div>
            <div className={`flex items-center gap-4 mb-2 ${!isFixed ? "opacity-50 pointer-events-none" : ""}`}>
                <button
                    onClick={() => pushTemp(target - TEMP_STEP)}
                    className="neu-btn w-10 h-10 text-xl shrink-0"
                    aria-label="Decrease temperature"
                    disabled={!isFixed}
                >
                    −
                </button>

                <div className="flex-1 neu-inset px-4 py-3">
                    <div className="flex items-baseline justify-between mb-2">
                        <span className="text-[10px] tracking-widest text-ink-soft uppercase">Target</span>
                        <span className="font-(family-name:--font-digit) text-2xl text-ink leading-none">
              {target.toFixed(1)}°
            </span>
                    </div>
                    <input type="range" min={TEMP_MIN} max={TEMP_MAX} step={TEMP_STEP} value={target} onChange={onSliderChange}
                        onMouseUp={onSliderRelease} onTouchEnd={onSliderRelease}
                        onKeyUp={onSliderRelease} disabled={!isFixed} className="temp-slider w-full"
                    />
                    <div className="flex justify-between text-[10px] text-ink-soft/70 mt-1">
                        <span>{TEMP_MIN}°</span>
                        <span>{TEMP_MAX}°</span>
                    </div>
                </div>

                <button
                    onClick={() => pushTemp(target + TEMP_STEP)}
                    className="neu-btn w-10 h-10 text-xl shrink-0"
                    aria-label="Increase temperature"
                    disabled={!isFixed}
                >
                    +
                </button>
            </div>

            <div className="mb-6 h-2" />

            <p className="topbar-label mb-3">Weather</p>
            <div className={`neu-inset p-1.5 grid grid-cols-4 gap-1 mb-6 ${!isFixed ? "opacity-50 pointer-events-none" : ""}`}>
                <WeatherPill label="Sunny"  active={currentWeather === "SUNNY"}  onClick={() => chooseWeather("SUNNY")}  glyph={<SunGlyph />} />
                <WeatherPill label="Rainy"  active={currentWeather === "RAINY"}  onClick={() => chooseWeather("RAINY")}  glyph={<DropGlyph />} />
                <WeatherPill label="Cloudy" active={currentWeather === "CLOUDY"} onClick={() => chooseWeather("CLOUDY")} glyph={<CloudGlyph />} />
                <WeatherPill label="Snowy"  active={currentWeather === "SNOWY"}  onClick={() => chooseWeather("SNOWY")}  glyph={<SnowGlyph />} />
            </div>

            <p className="topbar-label mb-3">Simulation Mode</p>
            <div className="neu-inset p-1.5 grid grid-cols-4 gap-1">
                <ModePill label="Internal" active={currentMode === "INTERNAL"}      onClick={() => chooseMode("INTERNAL")} />
                <ModePill label="MQTT"     active={currentMode === "EXTERNAL_MQTT"} onClick={() => chooseMode("EXTERNAL_MQTT")} />
                <ModePill label="Disabled" active={currentMode === "DISABLED"}      onClick={() => chooseMode("DISABLED")} />
                <ModePill label="Fixed"    active={currentMode === "FIXED"}         onClick={() => chooseMode("FIXED")} />
            </div>

            <style jsx>{`
                .temp-slider {
                    -webkit-appearance: none;
                    appearance: none;
                    height: 8px;
                    border-radius: 999px;
                    background: linear-gradient(to right, #5044c0 0%, #5044c0 ${pctOf(target)}%, rgba(31, 44, 76, 0.12) ${pctOf(target)}%, rgba(31, 44, 76, 0.12) 100%);
                    outline: none;
                    cursor: pointer;
                }

                .temp-slider::-webkit-slider-thumb {
                    -webkit-appearance: none;
                    appearance: none;
                    width: 22px;
                    height: 22px;
                    border-radius: 50%;
                    background: #e9ecf1;
                    border: 2px solid #1f2c4c;
                    cursor: pointer;
                    box-shadow: 2px 2px 5px rgba(31, 44, 76, 0.2);
                }

                .temp-slider::-moz-range-thumb {
                    width: 22px;
                    height: 22px;
                    border-radius: 50%;
                    background: #e9ecf1;
                    border: 2px solid #1f2c4c;
                    cursor: pointer;
                }
            `}</style>
        </div>
    );

    function pctOf(t: number): number {
        return ((t - TEMP_MIN) / (TEMP_MAX - TEMP_MIN)) * 100;
    }
}

function WeatherPill({
                         label,
                         active,
                         glyph,
                         onClick,
                     }: {
    label: string;
    active: boolean;
    glyph: React.ReactNode;
    onClick: () => void;
}) {
    return (
        <button
            onClick={onClick}
            className={`flex items-center justify-center gap-2 h-9 rounded-full text-xs font-semibold uppercase tracking-wider ${
                active
                    ? "bg-bg shadow-[var(--shadow-neu-out-sm)] border border-ink/15 text-ink"
                    : "text-ink-soft hover:text-ink hover:bg-bg/40"
            }`}
        >
            {glyph}
            <span>{label}</span>
        </button>
    );
}

function ModePill({
                      label,
                      active,
                      onClick,
                  }: {
    label: string;
    active: boolean;
    onClick: () => void;
}) {
    return (
        <button
            onClick={onClick}
            className={`flex items-center justify-center h-9 rounded-full text-xs font-semibold uppercase tracking-wider ${
                active
                    ? "bg-bg shadow-[var(--shadow-neu-out-sm)] border border-ink/15 text-ink"
                    : "text-ink-soft hover:text-ink hover:bg-bg/40"
            }`}
        >
            {label}
        </button>
    );
}

function SunGlyph() {
    return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="12" cy="12" r="4" />
            <path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M4.93 19.07l1.41-1.41M17.66 6.34l1.41-1.41" />
        </svg>
    );
}
function DropGlyph() {
    return (
        <svg width="12" height="14" viewBox="0 0 24 24" fill="currentColor">
            <path d="M12 2C8 8 6 11 6 14a6 6 0 0 0 12 0c0-3-2-6-6-12z" />
        </svg>
    );
}
function CloudGlyph() {
    return (
        <svg width="16" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M17.5 17a4.5 4.5 0 0 0 .5-9 6 6 0 0 0-11.7 1.5A4 4 0 0 0 7 17h10.5z" />
        </svg>
    );
}
function SnowGlyph() {
    return (
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M12 2v20M2 12h20M5 5l14 14M19 5L5 19" />
        </svg>
    );
}