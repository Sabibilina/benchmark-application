export function LoadingState({ label = "Loading" }: { label?: string }) {
  return <div className="py-8 text-sm text-neutral-600">{label}</div>;
}
