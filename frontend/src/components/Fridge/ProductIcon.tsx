interface Props {
  name: string;
  size?: number;
}

export function ProductIcon({ name, size = 14 }: Props) {
  const n = name.toLowerCase();
  const stroke = "#1f2c4c";

  if (n.includes("milk")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M8 2h8v4l2 4v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10l2-4V2z" />
      </svg>
    );
  }
  if (n.includes("apple")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M12 6c0-2 2-4 4-4M12 22c-4 0-7-4-7-9 0-3 2-5 5-5 1 0 2 1 2 1s1-1 2-1c3 0 5 2 5 5 0 5-3 9-7 9z" />
      </svg>
    );
  }
  if (n.includes("sourdough") || n.includes("bread")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M4 12c0-3 3-5 8-5s8 2 8 5v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6z" />
      </svg>
    );
  }
  if (n.includes("gruy") || n.includes("cheese")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M3 12L12 4l9 8v8H3v-8z" />
        <circle cx="9" cy="14" r="1" fill={stroke} />
        <circle cx="14" cy="16" r="1" fill={stroke} />
      </svg>
    );
  }
  if (n.includes("egg")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <ellipse cx="12" cy="13" rx="6" ry="8" />
      </svg>
    );
  }
  if (n.includes("chicken")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M6 14c0-4 3-7 7-7s6 2 6 5-2 5-5 5h-2l-2 4-2-4H6z" />
      </svg>
    );
  }
  if (n.includes("salmon") || n.includes("fish")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M2 12c4-6 10-6 14-3l4-3v12l-4-3c-4 3-10 3-14-3z" />
      </svg>
    );
  }
  if (n.includes("water")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M8 2h8v4l1 2v12a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V8l1-2V2z" />
      </svg>
    );
  }
  if (n.includes("carrot")) {
    return (
      <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
        <path d="M14 4l6 6-10 10-4-4 8-12z" />
        <path d="M14 4l-2-2M16 6l-2-2" />
      </svg>
    );
  }
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={stroke} strokeWidth="2">
      <circle cx="12" cy="12" r="8" />
    </svg>
  );
}
