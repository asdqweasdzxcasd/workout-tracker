/**
 * 기본 input/textarea/select 스타일.
 *
 * <p>react-hook-form 의 register() 결과를 펼쳐서 받기 좋게 forwardRef 형태로 작성.
 */
import { forwardRef, type InputHTMLAttributes, type TextareaHTMLAttributes, type SelectHTMLAttributes } from "react";

const baseClass =
  "block w-full rounded-md border border-zinc-300 bg-white px-3 py-2 text-sm text-zinc-900 placeholder:text-zinc-400 focus:border-blue-500 focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:bg-zinc-100 disabled:text-zinc-500";

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(
  function Input({ className = "", ...rest }, ref) {
    return <input ref={ref} className={`${baseClass} ${className}`} {...rest} />;
  },
);

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ className = "", ...rest }, ref) {
    return <textarea ref={ref} className={`${baseClass} ${className}`} {...rest} />;
  },
);

export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement>>(
  function Select({ className = "", children, ...rest }, ref) {
    return (
      <select ref={ref} className={`${baseClass} ${className}`} {...rest}>
        {children}
      </select>
    );
  },
);

type LabelProps = { children: React.ReactNode; htmlFor?: string; required?: boolean };
export function Label({ children, htmlFor, required }: LabelProps) {
  return (
    <label htmlFor={htmlFor} className="mb-1 block text-sm font-medium text-zinc-700">
      {children}
      {required ? <span className="ml-0.5 text-red-500">*</span> : null}
    </label>
  );
}

type FieldErrorProps = { message?: string };
export function FieldError({ message }: FieldErrorProps) {
  if (!message) return null;
  return (
    <p role="alert" className="mt-1 text-xs text-red-600">
      {message}
    </p>
  );
}
