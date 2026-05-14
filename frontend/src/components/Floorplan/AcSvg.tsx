interface Props {
    cooling: boolean;
    className?: string;
}

export function AcSvg({ cooling, className }: Props) {
    return (
        <svg
            width="102"
            height="36"
            viewBox="0 0 102 36"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className={className}
        >
            <rect
                x="12.3"
                y="25"
                width="77"
                height="8"
                rx="4"
                fill="#93C0EC"
                style={{
                    filter: "blur(6px)",
                    opacity: cooling ? 0.9 : 0,
                    transition: "opacity 800ms ease-in-out",
                    animation: cooling ? "acGlow 2s ease-in-out infinite" : "none",
                }}
            />

            <rect
                x="10.66"
                y="0.23"
                width="72.64"
                height="26.29"
                fill={cooling ? "#F8FFFF" : "#FFE7E7"}
                style={{ transition: "fill 800ms ease-in-out" }}
            />
            <rect
                x="10.3"
                y="0"
                width="83"
                height="26"
                fill={cooling ? "#EEF9FB" : "#FFF1F1"}
                stroke="#2E3D4D"
                strokeWidth="1"
                style={{ transition: "fill 800ms ease-in-out" }}
            />
            <rect x="10.3" y="26" width="83" height="3" fill="#2E3D4D" />
            <rect x="9.3" y="2" width="1" height="27" fill="#2E3D4D" />

            <path d="M48.1 7.23H56.23L57.04 12.1H58.66V14.54H45.66V12.1H47.29L48.1 7.23Z" fill="#2E3D4D" />
            <path d="M51.35 16.16V16.64C51.35 17.16 51.56 17.66 51.93 18.03L52.16 18.27C52.68 18.79 52.98 19.49 52.98 20.23H51.35C51.35 19.92 51.23 19.63 51.01 19.41L50.78 19.18C50.1 18.5 49.73 17.59 49.73 16.64V16.16H51.35Z" fill="#2E3D4D" />
            <path d="M54.94 16.98C54.72 16.76 54.6 16.47 54.6 16.16H52.98C52.98 16.9 53.27 17.61 53.79 18.13L54.03 18.36C54.39 18.73 54.6 19.23 54.6 19.75V20.23H56.23V19.75C56.23 18.8 55.85 17.89 55.18 17.21L54.94 16.98Z" fill="#2E3D4D" />

            <circle
                cx="22.66"
                cy="11.23"
                r="5"
                fill={cooling ? "#a5d8a5" : "#F8D7D7"}
                stroke={cooling ? "#3f8a3f" : "#C97171"}
                strokeWidth="0.5"
                style={{ transition: "fill 800ms ease-in-out, stroke 800ms ease-in-out" }}
            />
            <text
                x="22.66"
                y="13.2"
                textAnchor="middle"
                fontSize="4.5"
                fontWeight="700"
                fill={cooling ? "#1f4d1f" : "#7a2828"}
                letterSpacing="0.3"
            >
                {cooling ? "ON" : "OFF"}
            </text>

            <style>{`
        @keyframes acGlow {
          0%, 100% { opacity: 0.4; }
          50% { opacity: 0.9; }
        }
      `}</style>
        </svg>
    );
}
