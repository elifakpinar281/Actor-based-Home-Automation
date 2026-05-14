interface Props {
    on: boolean;
    className?: string;
}

export function TvSvg({ on, className }: Props) {
    return (
        <svg
            width="24"
            height="75"
            viewBox="0 0 24 75"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            className={className}
        >
            <path
                d="M22.9609 72.4644C22.9608 73.4208 22.0774 74.1413 21.1427 73.9479L4.08158 70.4181C3.39179 70.2754 2.89886 69.6701 2.89871 68.9644L2.90559 5.99656C2.90582 5.28009 3.41335 4.65919 4.11642 4.51379L21.1785 0.984324C22.1054 0.792707 22.9677 1.49377 22.9678 2.43829L22.9609 72.4644Z"
                fill={on ? "#93C0EC" : "#7D8B99"}
                stroke="#2E3D4D"
                strokeLinejoin="round"
                style={{ transition: "fill 600ms ease-in-out" }}
            />
            <rect
                width="2.6114"
                height="45.6558"
                transform="matrix(0.999997 0.00245813 -0.00117235 0.999999 0.0537109 14.8148)"
                fill="#2E3D4D"
            />
            <text
                x="12"
                y="42"
                textAnchor="middle"
                transform="rotate(90 12 42)"
                fontSize="6"
                fontWeight="700"
                fill="#1f2c4c"
                letterSpacing="1"
                style={{ opacity: on ? 1 : 0, transition: "opacity 400ms ease-in-out" }}
            >
                PLAYING
            </text>
        </svg>
    );
}
