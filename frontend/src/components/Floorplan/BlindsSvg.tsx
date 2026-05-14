interface Props {
    closed: boolean;
    className?: string;
}

export function BlindsSvg({ closed, className }: Props) {
    return (
        <svg
            width="118"
            height="26"
            viewBox="0 0 118 26"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            style={{ overflow: "hidden" }}
            className={className}
        >
            <g
                style={{
                    transform: closed ? "translateY(0)" : "translateY(-13px)",
                    opacity: closed ? 1 : 0,
                    transition: "transform 900ms cubic-bezier(0.4, 0, 0.2, 1), opacity 700ms ease-in-out",
                }}
            >
                <path
                    d="M114.154 12.902L2.93853 12.2676C1.75485 12.2608 1.07364 10.9149 1.77085 9.96012L8.84371 0.273778L108.323 0.841241L115.335 10.6079C116.027 11.5706 115.337 12.9088 114.154 12.902Z"
                    fill="#7D8B99"
                    stroke="#2E3D4D"
                    strokeWidth="0.1"
                    strokeLinejoin="round"
                />
                <line x1="7.25" y1="2.59" x2="109.43" y2="2.59" stroke="#2E3D4D" strokeWidth="0.4" />
                <line x1="5.31" y1="5.23" x2="111.37" y2="5.23" stroke="#2E3D4D" strokeWidth="0.4" />
                <line x1="3.37" y1="7.86" x2="113.31" y2="7.86" stroke="#2E3D4D" strokeWidth="0.4" />
            </g>
            <rect x="0.779785" width="1.94015" height="16.7088" fill="#1f2c4c" />
            <rect x="113.955" width="1.94015" height="16.7088" fill="#1f2c4c" />

            <rect x="110.073" y="10.3971" width="5" height="5" fill="#1f2c4c" />
            <rect x="61.073" y="10.3971" width="49" height="5" fill="white" stroke="#1f2c4c" strokeWidth="0.6" />
            <rect x="56.073" y="10.3971" width="5" height="5" fill="#1f2c4c" />
            <rect x="7.073" y="10.3971" width="49" height="5" fill="white" stroke="#1f2c4c" strokeWidth="0.6" />
            <rect x="2.073" y="10.3971" width="5" height="5" fill="#1f2c4c" />
        </svg>
    );
}