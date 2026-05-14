"use client";

import { Topbar } from "../components/Topbar";
import { StatusCards } from "../components/StatusCards";
import { Floorplan } from "../components/Floorplan/Floorplan";
import { ControlsCard } from "../components/ControlsCard";
import { MediaStationCard } from "../components/MediaStationCard";
import { FridgeCard } from "../components/Fridge/FridgeCard";
import { useStatus } from "../hooks/useStatus";

export default function Page() {
    const { status, error } = useStatus();

    return (
        <div className="min-h-screen">
            <Topbar />

             <main className="max-w-[1100px] mx-auto px-6 py-6 space-y-6">
                {error && (
                    <div className="neu-card p-4 border-danger/40 text-danger text-sm">
                        Backend not reachable: {error}
                    </div>
                )}

                <StatusCards status={status} />
                <Floorplan status={status} />

                <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                    <ControlsCard status={status} />
                    <MediaStationCard status={status} />
                </div>

                <FridgeCard />
            </main>
        </div>
    );
}
