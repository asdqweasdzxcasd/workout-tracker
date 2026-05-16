/**
 * 기본 버튼 컴포넌트.
 *
 * <p>variant 별 색상은 Tailwind 기본 팔레트(zinc/blue/red) 사용. 다크모드/테마 토큰은 도입하지 않는다.
 */
import type { ButtonHTMLAttributes } from "react";

type ButtonVariant = "primary" | "secondary" | "danger" | "ghost";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  fullWidth?: boolean;
};

const variantClasses: Record<ButtonVariant, string> = {
  primary: "bg-blue-600 text-white hover:bg-blue-700 disabled:bg-blue-400",
  secondary: "bg-white text-zinc-800 border border-zinc-300 hover:bg-zinc-50 disabled:bg-zinc-100",
  danger: "bg-red-600 text-white hover:bg-red-700 disabled:bg-red-400",
  ghost: "bg-transparent text-zinc-700 hover:bg-zinc-100 disabled:text-zinc-400",
};

export function Button({
  variant = "primary",
  fullWidth = false,
  className = "",
  children,
  ...rest
}: Props) {
  const base =
    "inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed";
  const width = fullWidth ? "w-full" : "";
  return (
    <button {...rest} className={`${base} ${variantClasses[variant]} ${width} ${className}`}>
      {children}
    </button>
  );
}
