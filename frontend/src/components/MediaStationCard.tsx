"use client";

import { useState } from "react";
import { api } from "../lib/api";
import { Status } from "../lib/types";
import {useToast} from "@/src/hooks/useToast";
import {Hint} from "@/src/components/Hint";

interface Props {
    status: Status | null;
}

export function MediaStationCard({ status }: Props) {
    const { push } = useToast();
    const [movieInput, setMovieInput] = useState<string>("");

    const playing = status?.moviePlaying ?? false;
    const current = status?.currentMovie;

    async function handlePlay() {
        if (!movieInput.trim()) return;
        try {
            await api.playMovie(movieInput.trim());
            setMovieInput("");
        } catch (e) {
            push(`Play failed: ${(e as Error).message}`, "error");
        }
    }

    async function handleStop() {
        try {
            await api.stopMovie();
        } catch (e) {
            push(`Stop failed: ${(e as Error).message}`, "error");
        }
    }

    return (
        <div className="neu-card p-6">
            <h2 className="section-title mb-2">Media Station</h2>
            <Hint>
                Type a film title and press <strong>Play</strong>. Starting a movie automatically closes the blinds. Stopping the movie restores them based on the current weather.
            </Hint>

            <div className="neu-inset px-4 py-3 mt-4 mb-4 flex items-center justify-between gap-3">
                <div className="flex flex-col">
                    <span className="topbar-label">Now Playing</span>
                    <span className="text-base font-semibold text-ink mt-1">
                        {playing && current ? current : "—"}
                    </span>
                </div>
                <button
                    onClick={handleStop}
                    aria-label="Stop"
                    className="w-9 h-9 neu-btn flex items-center justify-center"
                    disabled={!playing}
                >
                    <span className="block w-3 h-3 bg-danger" />
                </button>
            </div>

            <div className="flex gap-2">
                <input
                    value={movieInput}
                    onChange={(e) => setMovieInput(e.target.value)}
                    onKeyDown={(e) => {
                        if (e.key === "Enter") handlePlay();
                    }}
                    placeholder="Film title"
                    className="neu-inset flex-1 px-4 h-10 text-sm text-ink placeholder:text-ink-soft outline-none"
                />
                <button onClick={handlePlay} className="neu-btn px-6 h-10 text-xs tracking-widest uppercase">
                    Play
                </button>
            </div>
        </div>
    );
}
