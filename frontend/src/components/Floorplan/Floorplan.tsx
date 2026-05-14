"use client";

import Image from "next/image";
import { Status } from "../../lib/types";
import { AcSvg } from "./AcSvg";
import { BlindsSvg } from "./BlindsSvg";
import { TvSvg } from "./TvSvg";

interface Props {
    status: Status | null;
}

export function Floorplan({ status }: Props) {
    const blindsClosed = status?.blindsClosed ?? false;
    const moviePlaying = status?.moviePlaying ?? false;
    const acCooling = status?.acCooling ?? false;

    return (
            <div className="relative w-full" style={{ aspectRatio: "679 / 385" }}>
                <Image
                    src="/floorplan.svg"
                    alt="Floorplan"
                    fill
                    priority
                    className="object-cover select-none pointer-events-none"
                />
                <div
                    className="absolute"
                    style={{ left: "55.4%", top: "2.4%", width: "15.5%" }}
                    aria-label="Air Conditioner Living Room"
                >
                    <AcSvg cooling={acCooling} className="w-full h-auto block" />
                </div>
                <div
                    className="absolute"
                    style={{
                        left: "0.8%",
                        top: "90%",
                        width: "15.5%",
                        transform: "rotate(-90deg)",
                        transformOrigin: "top left",
                    }}
                    aria-label="Air Conditioner Kitchen"
                >
                    <AcSvg cooling={acCooling} className="w-full h-auto block" />
                </div>

                <div
                    className="absolute"
                    style={{ left: "79.5%", top: "2%", width: "18%" }}
                    aria-label="Blinds Living Room"
                >
                    <BlindsSvg closed={blindsClosed} className="w-full h-auto block" />
                </div>

                <div
                    className="absolute"
                    style={{
                        left: "22.5%",
                        bottom: "1.5%",
                        width: "18%",
                        transform: "rotate(180deg)",
                    }}
                    aria-label="Blinds Kitchen"
                >
                    <BlindsSvg closed={blindsClosed} className="w-full h-auto block" />
                </div>

                <div
                    className="absolute"
                    style={{ right: "1.5%", top: "37%", width: "3.7%" }}
                    aria-label="Media Station"
                >
                    <TvSvg on={moviePlaying} className="w-full h-auto block" />
                </div>
            </div>
    );
}
