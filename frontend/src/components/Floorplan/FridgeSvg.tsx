interface Props {
    className?: string;
    lowStock?: boolean;
}

export function FridgeSvg({ className, lowStock = false }: Props) {
    return (
        <svg
            width="85"
            height="44"
            viewBox="0 0 85 44"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className={className}
        >
            <rect width="85" height="41" fill="#FFE7E7" />
            <rect x="0.5" y="0.5" width="84" height="40" fill="#FFF8F8" stroke="#2E3D4D" />

            <path d="M50 1H54L39 40H35L50 1Z" fill="black" fillOpacity="0.1" />
            <path d="M62 1H73L58 40H47L62 1Z" fill="black" fillOpacity="0.1" />
            <path d="M80 1H78L63 40H65L80 1Z" fill="black" fillOpacity="0.1" />

            <rect x="5" y="41" width="5" height="3" fill="#2E3D4D" />
            <rect x="75" y="41" width="5" height="3" fill="#2E3D4D" />

            <g style={lowStock ? { animation: "fridgePulse 1.8s ease-in-out infinite" } : undefined}>
                <path
                    fillRule="evenodd"
                    clipRule="evenodd"
                    d="M42.6828 14.5842L44.1747 13.7413L43.1909 12L41.6828 12.8521L40.1747 12L39.1909 13.7413L40.6828 14.5842V16.4233L39.0847 17.346L37.4921 16.4265L37.508 14.713L35.5081 14.6943L35.492 16.4264L34 17.3064L35.0161 19.0291L36.4921 18.1585L38.0847 19.078L38.0847 20.9233L36.492 21.8428L35.0161 20.9723L34 22.6949L35.492 23.5749L35.5081 25.307L37.508 25.2884L37.492 23.5749L39.0847 22.6553L40.6828 23.578V25.4171L39.1909 26.26L40.1747 28.0013L41.6828 27.1492L43.1909 28.0013L44.1747 26.26L42.6828 25.4171V23.578L44.2809 22.6554L45.8736 23.5749L45.8576 25.2884L47.8575 25.3071L47.8737 23.575L49.3656 22.695L48.3495 20.9723L46.8736 21.8429L45.2809 20.9233L45.2809 19.078L46.8736 18.1585L48.3495 19.029L49.3656 17.3064L47.8737 16.4264L47.8575 14.6943L45.8576 14.7129L45.8736 16.4264L44.2809 17.3459L42.6828 16.4233V14.5842ZM41.6828 21.5006C42.5112 21.5006 43.1828 20.829 43.1828 20.0006C43.1828 19.1722 42.5112 18.5006 41.6828 18.5006C40.8544 18.5006 40.1828 19.1722 40.1828 20.0006C40.1828 20.829 40.8544 21.5006 41.6828 21.5006Z"
                    fill="#2E3D4D"
                />
            </g>

            <style jsx>{`
                @keyframes fridgePulse {
                    0%, 100% { opacity: 1; }
                    50% { opacity: 0.3; }
                }
            `}</style>
        </svg>
    );
}
